package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.material.MaterialResolver;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.Optional;

/** Assembles only from complete, validated escrow. Neither inventory nor the recipe's shared result is mutated. */
public final class ResultAssembler {
    public static Optional<ItemStack> assemble(SmithingRecipe recipe, List<ItemStack> escrow) {
        EscrowInputs inputs = EscrowInputs.bind(recipe, escrow).orElse(null);
        if (inputs == null) return Optional.empty();
        ItemStack result = recipe.result().copy();
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) return Optional.empty();
        if (recipe.keepsBaseData() && inputs.bases().size() == 1) UpgradeStackData.transfer(inputs.bases().get(0), result);
        return Optional.of(result);
    }

    /** Recovery is bounded by actual base provenance and paid added metal, independently of new quality. */
    public static int recovery(SmithingRecipe recipe, List<ItemStack> escrow, int paid) {
        EscrowInputs inputs = EscrowInputs.bind(recipe, escrow).orElse(null);
        if (inputs == null) return 0;
        long total = Math.min(Math.max(0, paid), recipe.addedContentUnits());
        for (ItemStack base : inputs.bases()) {
            var value = MaterialResolver.recyclingValue(base).orElse(null);
            if (value != null && value.family().equals(recipe.family())) total += value.units();
        }
        return (int) Math.min(recipe.recoverableUnits(), total);
    }

    private ResultAssembler() {}
}
