package com.otectus.immersivesmithing.api;

import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Decides whether a smith may forge a recipe at all. Asked when the Forge screen lists recipes (a denied recipe is
 * not offered; the first denial is the reason shown when nothing is left) and again, authoritatively, when the
 * player selects a recipe, before any metal or auxiliary ingredient is taken. Progression mods gate the making of
 * gear here rather than after the piece exists. Server thread only; a gate that throws is skipped.
 */
@FunctionalInterface
public interface ISmithingRecipeGate {
    /** A player-facing reason to refuse {@code recipe} for {@code smith}, or empty to allow it. */
    Optional<Component> deny(ServerPlayer smith, SmithingRecipe recipe);
}
