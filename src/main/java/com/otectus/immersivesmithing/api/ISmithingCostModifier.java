package com.otectus.immersivesmithing.api;

import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Adjusts what a smith pays. Every modifier is applied in registration order to the previous answer; the forge
 * never charges fewer than one unit nor more than the recipe's own, and a refinement never fewer than zero levels.
 * Server thread only; a modifier that throws is skipped for that call.
 */
public interface ISmithingCostModifier {
    /** Metal units the forge takes for {@code recipe}; {@code units} is the current answer (the recipe's own to start). */
    default int metalUnits(ServerPlayer smith, SmithingRecipe recipe, int units) {
        return units;
    }

    /** Experience levels one Smith's Grindstone refinement of {@code piece} costs; {@code levels} is the current answer. */
    default int refinementLevelCost(ServerPlayer smith, ItemStack piece, int levels) {
        return levels;
    }
}
