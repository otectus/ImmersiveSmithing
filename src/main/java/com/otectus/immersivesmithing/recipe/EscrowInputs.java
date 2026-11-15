package com.otectus.immersivesmithing.recipe;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Rebinds persisted, complete escrow stacks to their recipe roles without consulting or mutating player inventory. */
public record EscrowInputs(List<ItemStack> bases) {
    public static Optional<EscrowInputs> bind(SmithingRecipe recipe, List<ItemStack> escrow) {
        int[] left = escrow.stream().mapToInt(ItemStack::getCount).toArray();
        List<ItemStack> bases = new ArrayList<>();
        for (AuxiliaryIngredient role : recipe.auxiliary()) {
            int need = role.count();
            for (int i = 0; i < escrow.size() && need > 0; i++) {
                ItemStack stack = escrow.get(i);
                if (left[i] <= 0 || !role.ingredient().test(stack)) continue;
                int amount = Math.min(left[i], need);
                if (role.consumesEquipment()) {
                    if (com.otectus.immersivesmithing.compat.epicknights.LoadedEquipment.hasPayload(stack)) return Optional.empty();
                    if (amount != 1 || stack.getCount() != 1) return Optional.empty();
                    bases.add(stack.copy());
                }
                left[i] -= amount;
                need -= amount;
            }
            if (need != 0) return Optional.empty();
        }
        for (int remainder : left) if (remainder != 0) return Optional.empty();
        if (recipe.keepsBaseData() && bases.size() > 1) return Optional.empty();
        return Optional.of(new EscrowInputs(List.copyOf(bases)));
    }
}
