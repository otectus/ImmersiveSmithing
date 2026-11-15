package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A committed forge job: metal reserved in the reservoir, the exact auxiliary stacks in escrow, and the rough billet on
 * the tray with its heat. Everything needed to finish is frozen here at commitment (target stack, recovery cap, process
 * profile, heat bands), so a datapack reload never changes a job in progress. Until the billet is drawn, cancelling
 * releases the reservation and refunds the escrow once; drawing moves the units out of the reservoir and the escrow into
 * the workpiece in one step.
 */
public final class ForgeJob {
    public UUID id;
    public UUID owner;
    /** Game time of the owner's last action on this job; others may take it over once it is old enough. */
    public long lastTouch;
    public ResourceLocation recipeId;
    public ResourceLocation family;
    public ResourceLocation anvilPattern;
    public int units;
    public int recoverable;
    public ItemStack target;
    public List<ItemStack> escrow;
    public List<ProcessState.Socket> sockets;
    public ProcessProfile profile;
    public ThermalProfile thermal;
    public int workScale;
    public int[] heat = {ThermalRules.FRESH_BILLET, ThermalRules.FRESH_BILLET, ThermalRules.FRESH_BILLET};
    public int position = 1;
    public int scale;
    public int burnTicks;
    public boolean burnt;
    public boolean warned;

    public int mean() {
        return ThermalRules.mean(heat);
    }

    /** Ready to draw: the coolest zone is workable. */
    public boolean drawable() {
        return ThermalRules.coolest(heat) >= thermal.workableMin();
    }

    /** The workpiece this billet becomes when drawn; its heat preparation is recorded now and never again. */
    public WorkpieceData draw() {
        ProcessState p = ProcessState.fresh(profile, thermal, workScale, sockets);
        p.heat = heat.clone();
        p.heatTick = ProcessState.ITEM_HELD;
        p.scale = scale;
        p.burnTicks = burnTicks;
        p.burnt = burnt;
        p.heatPrep = ThermalRules.heatPreparation(heat, thermal);
        return new WorkpieceData(target.copy(), recipeId, family, units, -1, -1, false, anvilPattern, recoverable, p);
    }

    public CompoundTag save(boolean full) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Owner", owner);
        tag.putLong("LastTouch", lastTouch);
        tag.putString("Recipe", recipeId.toString());
        tag.putString("Family", family.toString());
        tag.putString("Pattern", anvilPattern.toString());
        tag.putInt("Units", units);
        tag.putInt("Recoverable", recoverable);
        tag.put("Target", target.save(new CompoundTag()));
        if (full) {
            ListTag esc = new ListTag();
            for (ItemStack s : escrow) esc.add(s.save(new CompoundTag()));
            tag.put("Escrow", esc);
        }
        ListTag socketList = new ListTag();
        for (ProcessState.Socket s : sockets) {
            CompoundTag c = new CompoundTag();
            c.putString("Name", s.name());
            ListTag stacks = new ListTag();
            for (ItemStack st : s.stacks()) stacks.add(st.save(new CompoundTag()));
            c.put("Stacks", stacks);
            socketList.add(c);
        }
        tag.put("Sockets", socketList);
        tag.put("Profile", profile.save());
        tag.put("Thermal", thermal.save());
        tag.putInt("WorkScale", workScale);
        tag.putIntArray("Heat", heat);
        tag.putInt("Position", position);
        tag.putInt("Scale", scale);
        tag.putInt("BurnTicks", burnTicks);
        tag.putBoolean("Burnt", burnt);
        return tag;
    }

    /** Null when the record does not decode completely (a missing mod's item): the forge then keeps the raw tag. */
    @Nullable
    public static ForgeJob load(CompoundTag tag, boolean full) {
        try {
            ForgeJob j = new ForgeJob();
            if (!tag.hasUUID("Id") || !tag.hasUUID("Owner")) return null;
            j.id = tag.getUUID("Id");
            j.owner = tag.getUUID("Owner");
            j.lastTouch = tag.getLong("LastTouch");
            j.recipeId = ResourceLocation.tryParse(tag.getString("Recipe"));
            j.family = ResourceLocation.tryParse(tag.getString("Family"));
            j.anvilPattern = ResourceLocation.tryParse(tag.getString("Pattern"));
            if (j.recipeId == null || j.family == null || j.anvilPattern == null) return null;
            j.units = Math.max(0, tag.getInt("Units"));
            j.recoverable = Math.max(0, tag.getInt("Recoverable"));
            j.target = ItemStack.of(tag.getCompound("Target"));
            if (j.target.isEmpty()) return null;
            j.escrow = new ArrayList<>();
            if (full) {
                ListTag esc = tag.getList("Escrow", Tag.TAG_COMPOUND);
                for (int i = 0; i < Math.min(esc.size(), 64); i++) {
                    ItemStack s = ItemStack.of(esc.getCompound(i));
                    if (s.isEmpty()) return null;
                    j.escrow.add(s);
                }
            }
            j.sockets = new ArrayList<>();
            ListTag socketList = tag.getList("Sockets", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(socketList.size(), ProcessProfile.MAX_SOCKETS); i++) {
                CompoundTag c = socketList.getCompound(i);
                ListTag stacks = c.getList("Stacks", Tag.TAG_COMPOUND);
                List<ItemStack> items = new ArrayList<>();
                for (int k = 0; k < Math.min(stacks.size(), ProcessState.MAX_STACKS_PER_SOCKET); k++) {
                    ItemStack s = ItemStack.of(stacks.getCompound(k));
                    if (s.isEmpty()) return null;
                    items.add(s);
                }
                j.sockets.add(new ProcessState.Socket(c.getString("Name"), List.copyOf(items), false));
            }
            j.profile = ProcessProfile.load(tag.getCompound("Profile"));
            if (j.profile == null) return null;
            j.thermal = ThermalProfile.load(tag.getCompound("Thermal"));
            j.workScale = Math.max(500, Math.min(2000, tag.getInt("WorkScale")));
            int[] heat = tag.getIntArray("Heat");
            for (int i = 0; i < j.heat.length && i < heat.length; i++) j.heat[i] = Math.max(0, Math.min(ThermalProfile.SCALE, heat[i]));
            j.position = Math.floorMod(tag.getInt("Position"), ThermalRules.TRAY_POSITIONS);
            j.scale = Math.max(0, Math.min(1000, tag.getInt("Scale")));
            j.burnTicks = Math.max(0, tag.getInt("BurnTicks"));
            j.burnt = tag.getBoolean("Burnt");
            return j;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
