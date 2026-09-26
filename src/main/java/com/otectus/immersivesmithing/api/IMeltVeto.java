package com.otectus.immersivesmithing.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Keeps an item out of the Smith's Forge. Melting recovers only the metal, so a mod whose data on an item would be
 * lost (set gems, for example) can refuse it. Consulted for every deposit, by hand and by automation.
 */
@FunctionalInterface
public interface IMeltVeto {
    /** A player-facing reason to refuse melting {@code stack}, or empty to allow it. */
    Optional<Component> vetoMelting(ItemStack stack);
}
