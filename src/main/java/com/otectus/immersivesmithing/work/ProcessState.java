package com.otectus.immersivesmithing.work;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The durable process record of one workpiece: identity, stage, heat, the work done on every operation, the quench and the
 * components still to be fitted. It is a plain mutable value: holders hand out the stored instance read-only, and every
 * change goes through {@link #copy()} and a new {@link com.otectus.immersivesmithing.workpiece.WorkpieceData}, so a
 * rejected action never leaves a half-changed piece behind. Tool slots, leases and camera state are never stored here.
 */
public final class ProcessState {
    /** The forge score is the one stored by an old (version 1/2) forge minigame; never recomputed. */
    public static final int LEGACY_FORGE = 1;
    /** The anvil score is the one stored by the old anvil minigame; never recomputed. */
    public static final int LEGACY_ANVIL = 2;
    /** The piece's components were consumed when it was forged (old pieces): nothing to fit. */
    public static final int COMPONENTS_PAID = 4;
    /** Migrated from an older payload; kept so reports can say so. */
    public static final int MIGRATED = 8;

    /** Heat stored on an item (tongs, a dropped piece) is current as written; holders with a clock bake against it. */
    public static final long ITEM_HELD = -1L;
    public static final int MAX_STACKS_PER_SOCKET = 8;

    /** A fitting socket and the exact reserved stacks that go into it. */
    public record Socket(String name, List<ItemStack> stacks, boolean attached) {
        Socket copy() {
            List<ItemStack> out = new ArrayList<>(stacks.size());
            for (ItemStack s : stacks) out.add(s.copy());
            return new Socket(name, List.copyOf(out), attached);
        }

        public ItemStack display() {
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
        }
    }

    public UUID id;
    public int revision;
    public WorkStage stage;
    public ProcessProfile profile;
    public ThermalProfile thermal;
    public int[] heat = new int[ThermalRules.ZONES];
    public long heatTick = ITEM_HELD;
    public int scale;
    public int burnTicks;
    public boolean burnt;
    /** Heat preparation recorded when the billet was drawn, per mille; -1 for legacy pieces. */
    public int heatPrep = -1;
    /** Size of the piece relative to an iron sword, per mille: larger pieces need proportionally more work. */
    public int workScale = 1000;
    public int[] work;
    public int[] qualitySum;
    public int[] marks;
    public int[] blows;
    public int[] corrections;
    public boolean flipped;
    public int quenchTicks;
    public int dips;
    public int quenchControl = -1;
    @Nullable
    public BlockPos quenchPos;
    public List<Socket> sockets = new ArrayList<>();
    public int flags;

    private ProcessState() {}

    public static ProcessState fresh(ProcessProfile profile, ThermalProfile thermal, int workScale, List<Socket> sockets) {
        ProcessState s = new ProcessState();
        s.id = UUID.randomUUID();
        s.stage = WorkStage.BILLET;
        s.profile = profile;
        s.thermal = thermal;
        java.util.Arrays.fill(s.heat, ThermalRules.FRESH_BILLET);
        s.workScale = Math.max(500, Math.min(2000, workScale));
        s.sizeArrays();
        s.sockets = new ArrayList<>(sockets);
        return s;
    }

    /**
     * An old FORGED or SHAPED piece, migrated once: its stored scores stay authoritative and its components were already
     * paid, so it needs no fitting. A FORGED piece is shaped again (new work decides its anvil score), a SHAPED one only
     * needs its quench.
     */
    public static ProcessState legacy(boolean shaped, ProcessProfile profile, ThermalProfile thermal) {
        ProcessState s = new ProcessState();
        s.id = UUID.randomUUID();
        s.stage = shaped ? WorkStage.SHAPED : WorkStage.BILLET;
        s.profile = profile;
        s.thermal = thermal;
        java.util.Arrays.fill(s.heat, ThermalRules.LEGACY_HEAT);
        s.sizeArrays();
        s.flags = LEGACY_FORGE | COMPONENTS_PAID | MIGRATED | (shaped ? LEGACY_ANVIL : 0);
        if (shaped) {
            for (int i = 0; i < s.work.length; i++) s.work[i] = s.required(i);
        }
        return s;
    }

    private void sizeArrays() {
        int n = profile.operations().size();
        work = new int[n];
        qualitySum = new int[n];
        marks = new int[n];
        blows = new int[n];
        corrections = new int[n];
    }

    public ProcessState copy() {
        ProcessState s = new ProcessState();
        s.id = id;
        s.revision = revision;
        s.stage = stage;
        s.profile = profile;
        s.thermal = thermal;
        s.heat = heat.clone();
        s.heatTick = heatTick;
        s.scale = scale;
        s.burnTicks = burnTicks;
        s.burnt = burnt;
        s.heatPrep = heatPrep;
        s.workScale = workScale;
        s.work = work.clone();
        s.qualitySum = qualitySum.clone();
        s.marks = marks.clone();
        s.blows = blows.clone();
        s.corrections = corrections.clone();
        s.flipped = flipped;
        s.quenchTicks = quenchTicks;
        s.dips = dips;
        s.quenchControl = quenchControl;
        s.quenchPos = quenchPos;
        List<Socket> out = new ArrayList<>(sockets.size());
        for (Socket socket : sockets) out.add(socket.copy());
        s.sockets = out;
        s.flags = flags;
        return s;
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    // ------------------------------------------------------------------ heat

    /** The zones' heat at {@code clock} on a holder that keeps a work clock, without changing anything. */
    public int[] heatAt(long clock, double assist) {
        if (heatTick == ITEM_HELD || clock <= heatTick) return heat.clone();
        return ThermalRules.cool(heat, clock - heatTick, thermal, assist);
    }

    /** Writes the current heat as of {@code clock} and re-bases it to {@code newBase} (another holder's clock, or ITEM_HELD). */
    public void bake(long clock, long newBase) {
        heat = heatAt(clock, 1.0);
        heatTick = newBase;
    }

    public int meanHeat() {
        return ThermalRules.mean(heat);
    }

    // ------------------------------------------------------------------ shaping

    public int required(int op) {
        return Math.max(1, (int) ((long) profile.operations().get(op).work() * workScale / 1000));
    }

    public boolean complete(int op) {
        return work[op] >= required(op);
    }

    public boolean allComplete() {
        for (int i = 0; i < work.length; i++) if (!complete(i)) return false;
        return true;
    }

    public boolean prerequisitesMet(int op) {
        for (String dep : profile.operations().get(op).after()) {
            int index = profile.operationIndex(dep);
            if (index >= 0 && !complete(index)) return false;
        }
        return true;
    }

    /** Overall shaping progress, per mille, for display. */
    public int progress() {
        long done = 0, total = 0;
        for (int i = 0; i < work.length; i++) {
            done += Math.min(work[i], required(i));
            total += required(i);
        }
        return total == 0 ? 0 : (int) (done * 1000 / total);
    }

    public int progress(int op) {
        return (int) Math.min(1000, (long) work[op] * 1000 / required(op));
    }

    // ------------------------------------------------------------------ fitting

    public int socketsLeft() {
        int left = 0;
        for (Socket s : sockets) if (!s.attached()) left++;
        return left;
    }

    public boolean needsFitting() {
        return !has(COMPONENTS_PAID) && socketsLeft() > 0;
    }

    /** Unattached reserved stacks, which a deliberate recycling returns. */
    public List<ItemStack> unattachedStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (Socket s : sockets) if (!s.attached()) s.stacks().forEach(st -> out.add(st.copy()));
        return out;
    }

    // ------------------------------------------------------------------ persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putInt("Revision", revision);
        tag.putString("Stage", stage.name());
        tag.put("Profile", profile.save());
        tag.put("Thermal", thermal.save());
        tag.putIntArray("Heat", heat);
        tag.putLong("HeatTick", heatTick);
        tag.putInt("Scale", scale);
        tag.putInt("BurnTicks", burnTicks);
        tag.putBoolean("Burnt", burnt);
        tag.putInt("HeatPrep", heatPrep);
        tag.putInt("WorkScale", workScale);
        tag.putIntArray("Work", work);
        tag.putIntArray("QualitySum", qualitySum);
        tag.putIntArray("Marks", marks);
        tag.putIntArray("Blows", blows);
        tag.putIntArray("Corrections", corrections);
        tag.putBoolean("Flipped", flipped);
        tag.putInt("QuenchTicks", quenchTicks);
        tag.putInt("Dips", dips);
        tag.putInt("QuenchControl", quenchControl);
        if (quenchPos != null) tag.put("QuenchPos", NbtUtils.writeBlockPos(quenchPos));
        ListTag socketList = new ListTag();
        for (Socket s : sockets) {
            CompoundTag c = new CompoundTag();
            c.putString("Name", s.name());
            c.putBoolean("Attached", s.attached());
            ListTag stacks = new ListTag();
            for (ItemStack st : s.stacks()) stacks.add(st.save(new CompoundTag()));
            c.put("Stacks", stacks);
            socketList.add(c);
        }
        tag.put("Sockets", socketList);
        tag.putInt("Flags", flags);
        return tag;
    }

    /**
     * Reads a saved record. Every number is bounded and every array sized to the profile; a component stack that no longer
     * decodes (its mod is gone) makes the whole record unreadable instead of silently dropping it.
     */
    @Nullable
    public static ProcessState load(CompoundTag tag) {
        ProcessProfile profile = ProcessProfile.load(tag.getCompound("Profile"));
        WorkStage stage = WorkStage.byName(tag.getString("Stage"));
        if (profile == null || stage == null || !tag.hasUUID("Id")) return null;
        ProcessState s = new ProcessState();
        s.id = tag.getUUID("Id");
        s.revision = Math.max(0, tag.getInt("Revision"));
        s.stage = stage;
        s.profile = profile;
        s.thermal = ThermalProfile.load(tag.getCompound("Thermal"));
        s.heat = bounded(tag.getIntArray("Heat"), ThermalRules.ZONES, 0, ThermalProfile.SCALE);
        s.heatTick = tag.getLong("HeatTick");
        if (s.heatTick < ITEM_HELD) s.heatTick = ITEM_HELD;
        s.scale = clamp(tag.getInt("Scale"), 0, 1000);
        s.burnTicks = clamp(tag.getInt("BurnTicks"), 0, 100_000);
        s.burnt = tag.getBoolean("Burnt");
        s.heatPrep = clamp(tag.getInt("HeatPrep"), -1, 1000);
        s.workScale = clamp(tag.getInt("WorkScale"), 500, 2000);
        int n = profile.operations().size();
        s.work = bounded(tag.getIntArray("Work"), n, 0, 100_000);
        s.qualitySum = bounded(tag.getIntArray("QualitySum"), n, 0, 100_000_000);
        s.marks = bounded(tag.getIntArray("Marks"), n, 0, 1000);
        s.blows = bounded(tag.getIntArray("Blows"), n, 0, 100_000);
        s.corrections = bounded(tag.getIntArray("Corrections"), n, 0, 100);
        s.flipped = tag.getBoolean("Flipped");
        s.quenchTicks = clamp(tag.getInt("QuenchTicks"), 0, 100_000);
        s.dips = clamp(tag.getInt("Dips"), 0, 1000);
        s.quenchControl = clamp(tag.getInt("QuenchControl"), -1, 1000);
        s.quenchPos = tag.contains("QuenchPos", Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound("QuenchPos")) : null;
        ListTag socketList = tag.getList("Sockets", Tag.TAG_COMPOUND);
        List<Socket> sockets = new ArrayList<>();
        for (int i = 0; i < Math.min(socketList.size(), ProcessProfile.MAX_SOCKETS); i++) {
            CompoundTag c = socketList.getCompound(i);
            ListTag stacks = c.getList("Stacks", Tag.TAG_COMPOUND);
            List<ItemStack> items = new ArrayList<>();
            for (int j = 0; j < Math.min(stacks.size(), MAX_STACKS_PER_SOCKET); j++) {
                ItemStack st = ItemStack.of(stacks.getCompound(j));
                if (st.isEmpty()) return null; // a component from a missing mod: keep the raw payload instead
                items.add(st);
            }
            sockets.add(new Socket(c.getString("Name"), List.copyOf(items), c.getBoolean("Attached")));
        }
        s.sockets = sockets;
        s.flags = tag.getInt("Flags") & 0xF;
        return s;
    }

    private static int[] bounded(int[] values, int length, int min, int max) {
        int[] out = new int[length];
        for (int i = 0; i < length && i < values.length; i++) out[i] = clamp(values[i], min, max);
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
