package com.otectus.immersivesmithing.quality;

import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.registry.ModRecipeTypes;
import com.otectus.immersivesmithing.registry.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Which equipment may carry smithing quality, kept apart from whether the forge can make it and whether it can be
 * melted down. Everything the forge makes qualifies; so does equipment in {@code #immersive_smithing:quality_eligible}
 * (loot, runic and unique weapons that are never forged) while its mod's integration is switched on. Eligibility only
 * decides loot grading and the grindstone's neutral starting grade; it never adds a recipe.
 */
public final class QualityEligibility {
    /** The grade an eligible piece without one starts from at the grindstone: both scores at the neutral midpoint. */
    public static final int NEUTRAL_SCORE = 50;

    /** Server side: uses the rebuilt smithing data. */
    public static boolean canCarryQuality(ItemStack stack) {
        return canCarryQuality(stack, !SmithingData.current().recipesProducing(stack.getItem()).isEmpty());
    }

    /** Either side: the client asks its synced recipe manager whether the forge makes the item. */
    public static boolean canCarryQuality(ItemStack stack, Level level) {
        if (!level.isClientSide) return canCarryQuality(stack);
        boolean forged = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.SMITHING.get()).stream()
                .anyMatch(r -> r.resultItem() == stack.getItem());
        return canCarryQuality(stack, forged);
    }

    private static boolean canCarryQuality(ItemStack stack, boolean forged) {
        if (stack.isEmpty() || stack.getMaxStackSize() != 1 || ImmersiveSmithingAPI.isExcluded(stack)
                || com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.owns(stack)) return false;
        if (stack.is(ModTags.NON_SMITHABLE_EQUIPMENT)) return false;
        if (forged) return EquipmentClassifier.isCandidate(stack);
        if (!stack.is(ModTags.QUALITY_ELIGIBLE) || !stack.isDamageableItem()) return false;
        return Integrations.enabled(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    /** The neutral grade an eligible ungraded piece starts from; it is written only by a paid refinement. */
    public static QualityData neutral() {
        return new QualityData(NEUTRAL_SCORE, NEUTRAL_SCORE, false);
    }

    private QualityEligibility() {}
}
