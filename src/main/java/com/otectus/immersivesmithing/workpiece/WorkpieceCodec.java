package com.otectus.immersivesmithing.workpiece;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.work.ProcessState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned NBT form of {@link WorkpieceData}. This workpiece schema version is independent of the finished-item quality
 * compound's own {@code Version}.
 *
 * <ul>
 *   <li>Version 1 and 2 (no version field, or 1, or 2 with {@code RecoverableUnits}): the old FORGED/SHAPED pieces. They are
 *       migrated by explicit dispatch into a process record ({@link WorkpieceData}'s legacy constructor) with a process id
 *       derived from the payload, so reading the same old payload twice gives the same piece; holders write it back as
 *       version 3 at their next save.</li>
 *   <li>Version 3: the full process record ({@code Process}).</li>
 *   <li>Anything newer, or a payload whose target or components no longer decode (a removed mod), is <b>unreadable</b>:
 *       {@link #read} says why, and every holder keeps the raw tag untouched and refuses to use the piece, so nothing is
 *       silently replaced by an empty stack.</li>
 * </ul>
 */
public final class WorkpieceCodec {
    public static final String ROOT = "immersive_smithing";
    public static final String HELD = "HeldWorkpiece";
    public static final int VERSION = 3;

    /** A decoded piece, or the reason it could not be decoded (the raw tag is the caller's to keep). */
    public record Read(@Nullable WorkpieceData data, @Nullable String problem, boolean migrated) {
        public boolean ok() {
            return data != null;
        }
    }

    public static CompoundTag save(WorkpieceData data) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", VERSION);
        tag.put("TargetStack", data.target().save(new CompoundTag()));
        tag.putString("RecipeId", data.recipeId().toString());
        tag.putString("MaterialFamily", data.family().toString());
        tag.putInt("MetalUnits", data.metalUnits());
        tag.putInt("RecoverableUnits", data.recoverableUnits());
        tag.putInt("ForgeScore", data.forgeScore());
        tag.putInt("AnvilScore", data.anvilScore());
        tag.putBoolean("Faulty", data.faulty());
        // The old two-state name, kept for diagnostics and older readers; version 3 readers use Process.
        tag.putString("State", data.state().name());
        tag.putString("AnvilPattern", data.anvilPattern().toString());
        tag.put("Process", data.process().save());
        return tag;
    }

    public static Optional<WorkpieceData> load(CompoundTag tag) {
        return Optional.ofNullable(read(tag).data());
    }

    public static Read read(CompoundTag tag) {
        try {
            int version = tag.contains("Version", Tag.TAG_INT) ? tag.getInt("Version") : 1;
            if (version > VERSION) return new Read(null, "saved by a newer version (workpiece schema " + version + ")", false);
            if (!tag.contains("TargetStack", Tag.TAG_COMPOUND)) return new Read(null, "no target item", false);
            ItemStack target = ItemStack.of(tag.getCompound("TargetStack"));
            if (target.isEmpty()) {
                return new Read(null, "its item " + tag.getCompound("TargetStack").getString("id") + " no longer exists", false);
            }
            ResourceLocation recipe = ResourceLocation.tryParse(tag.getString("RecipeId"));
            ResourceLocation family = ResourceLocation.tryParse(tag.getString("MaterialFamily"));
            ResourceLocation pattern = ResourceLocation.tryParse(tag.getString("AnvilPattern"));
            if (recipe == null) recipe = ImmersiveSmithing.id("unknown");
            if (family == null) family = ImmersiveSmithing.id("unknown");
            if (pattern == null || tag.getString("AnvilPattern").isEmpty()) pattern = EquipmentClassifier.classify(target);
            int units = Math.max(0, tag.getInt("MetalUnits"));
            int recoverable = tag.contains("RecoverableUnits", Tag.TAG_INT) ? Math.max(0, tag.getInt("RecoverableUnits")) : units;
            boolean faulty = tag.getBoolean("Faulty");

            if (version <= 2) {
                WorkpieceState state = WorkpieceState.byName(tag.getString("State"));
                if (state == null) return new Read(null, "unknown legacy state '" + tag.getString("State") + "'", false);
                int forge = Mth.clamp(tag.getInt("ForgeScore"), 0, 100);
                int anvil = state == WorkpieceState.SHAPED ? Mth.clamp(tag.getInt("AnvilScore"), 0, 100) : -1;
                WorkpieceData migrated = new WorkpieceData(target, recipe, family, units, forge, anvil, faulty, state, pattern, recoverable);
                migrated.process().id = legacyId(tag);
                return new Read(migrated, null, true);
            }

            ProcessState process = ProcessState.load(tag.getCompound("Process"));
            if (process == null) return new Read(null, "its process record does not decode (a component's mod may be missing)", false);
            int forge = Mth.clamp(tag.getInt("ForgeScore"), -1, 100);
            int anvil = Mth.clamp(tag.getInt("AnvilScore"), -1, 100);
            return new Read(new WorkpieceData(target, recipe, family, units, forge, anvil, faulty, pattern, recoverable, process), null, false);
        } catch (RuntimeException e) {
            return new Read(null, "unreadable: " + e, false);
        }
    }

    /** A stable identity for an old payload, so that repeated migration yields the same piece. */
    private static UUID legacyId(CompoundTag tag) {
        String key = tag.getString("RecipeId") + '|' + tag.getString("MaterialFamily") + '|' + tag.getInt("MetalUnits") + '|'
                + tag.getInt("ForgeScore") + '|' + tag.getInt("AnvilScore") + '|' + tag.getBoolean("Faulty") + '|'
                + tag.getString("State") + '|' + tag.getString("AnvilPattern") + '|' + tag.getInt("RecoverableUnits") + '|'
                + tag.getCompound("TargetStack");
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    /** The raw payload carried by tongs or a Hot Workpiece item, readable or not. */
    @Nullable
    public static CompoundTag rawHeld(ItemStack holder) {
        CompoundTag tag = holder.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) return null;
        CompoundTag root = tag.getCompound(ROOT);
        return root.contains(HELD, Tag.TAG_COMPOUND) ? root.getCompound(HELD) : null;
    }

    /** The workpiece carried by tongs or stored in a Hot Workpiece item, if it decodes. */
    public static Optional<WorkpieceData> getHeld(ItemStack holder) {
        CompoundTag raw = rawHeld(holder);
        return raw == null ? Optional.empty() : load(raw);
    }

    /** Whether the holder carries any payload at all, readable or not; such tongs are never treated as empty. */
    public static boolean isHolding(ItemStack holder) {
        return rawHeld(holder) != null;
    }

    /** Whether the holder's payload is an older schema that should be rewritten as the current one. */
    public static boolean needsUpgrade(ItemStack holder) {
        CompoundTag raw = rawHeld(holder);
        return raw != null && (!raw.contains("Version", Tag.TAG_INT) || raw.getInt("Version") < VERSION) && load(raw).isPresent();
    }

    public static void setHeld(ItemStack holder, WorkpieceData data) {
        holder.getOrCreateTagElement(ROOT).put(HELD, save(data));
    }

    /** Puts an undecodable payload back exactly as it was. */
    public static void setRawHeld(ItemStack holder, CompoundTag raw) {
        holder.getOrCreateTagElement(ROOT).put(HELD, raw.copy());
    }

    public static void clearHeld(ItemStack holder) {
        CompoundTag tag = holder.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) return;
        CompoundTag root = tag.getCompound(ROOT);
        root.remove(HELD);
        if (root.isEmpty()) holder.removeTagKey(ROOT);
    }

    private WorkpieceCodec() {}
}
