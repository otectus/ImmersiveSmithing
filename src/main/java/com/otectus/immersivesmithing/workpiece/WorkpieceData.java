package com.otectus.immersivesmithing.workpiece;

import com.otectus.immersivesmithing.work.ProcessResolver;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.WorkStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * An unfinished piece of equipment. It lives in Smithing Tongs, on a Smith's Anvil, on the forge's reheat rest, in a
 * Smith's Trough during a quench, or in a dropped Hot Workpiece item, and becomes the usable target stack only through
 * {@link com.otectus.immersivesmithing.work.SmithingCompletionService}. Exactly one holder has it at any time.
 *
 * <p>{@code forgeScore} and {@code anvilScore} are the finalized phase scores (-1 until finalized): the anvil score when the
 * quench begins, the forge score when it ends. Legacy pieces keep the scores they were saved with ({@link ProcessState#LEGACY_FORGE},
 * {@link ProcessState#LEGACY_ANVIL}).</p>
 */
public record WorkpieceData(
        ItemStack target,
        ResourceLocation recipeId,
        ResourceLocation family,
        int metalUnits,
        int forgeScore,
        int anvilScore,
        boolean faulty,
        ResourceLocation anvilPattern,
        int recoverableUnits,
        ProcessState process) {

    /** Legacy/API constructor: old workpieces carry only their paid metal as their conservative recovery cap. */
    public WorkpieceData(ItemStack target, ResourceLocation recipeId, ResourceLocation family, int metalUnits,
                         int forgeScore, int anvilScore, boolean faulty, WorkpieceState state, ResourceLocation anvilPattern) {
        this(target, recipeId, family, metalUnits, forgeScore, anvilScore, faulty, state, anvilPattern, metalUnits);
    }

    /**
     * A piece in the old two-state form (what version 1 and 2 payloads contain), migrated: a FORGED piece keeps its forge
     * score and is shaped again; a SHAPED one keeps both scores and only needs its quench. Neither is charged for components.
     */
    public WorkpieceData(ItemStack target, ResourceLocation recipeId, ResourceLocation family, int metalUnits,
                         int forgeScore, int anvilScore, boolean faulty, WorkpieceState state, ResourceLocation anvilPattern,
                         int recoverableUnits) {
        this(target, recipeId, family, metalUnits, forgeScore, state == WorkpieceState.SHAPED ? anvilScore : -1, faulty, anvilPattern,
                recoverableUnits, legacyProcess(state, target, family, anvilPattern));
    }

    private static ProcessState legacyProcess(WorkpieceState state, ItemStack target, ResourceLocation family, ResourceLocation pattern) {
        var profile = ProcessResolver.forLegacy(pattern, target);
        return ProcessState.legacy(state == WorkpieceState.SHAPED, profile, ProcessResolver.thermal(family, profile));
    }

    /** The old two-state view, for tooltips, Jade and API callers. */
    public WorkpieceState state() {
        return process.stage.legacyShaped() ? WorkpieceState.SHAPED : WorkpieceState.FORGED;
    }

    public boolean isShaped() {
        return process.stage.legacyShaped();
    }

    public WorkStage stage() {
        return process.stage;
    }

    public java.util.UUID id() {
        return process.id;
    }

    public WorkpieceData withProcess(ProcessState next) {
        return new WorkpieceData(target, recipeId, family, metalUnits, forgeScore, anvilScore, faulty, anvilPattern, recoverableUnits, next);
    }

    public WorkpieceData withScores(int forge, int anvil, boolean faulty, ProcessState next) {
        return new WorkpieceData(target, recipeId, family, metalUnits, forge, anvil, faulty, anvilPattern, recoverableUnits, next);
    }

    /** A saved server cap never authorizes more metal after a datapack changes or removes the item's composition. */
    public int recoverableNow() {
        var value = com.otectus.immersivesmithing.material.MaterialResolver.recyclingValue(target).orElse(null);
        if (value == null) return Math.max(0, Math.min(recoverableUnits, metalUnits));
        if (!value.family().equals(family)) return 0;
        return (int) Math.max(0, Math.min((long) recoverableUnits, (long) value.units() * target.getCount()));
    }
}
