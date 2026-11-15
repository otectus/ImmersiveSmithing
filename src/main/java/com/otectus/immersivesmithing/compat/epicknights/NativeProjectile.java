package com.otectus.immersivesmithing.compat.epicknights;

import net.minecraft.world.item.ItemStack;

/** Implemented by version-gated mixins; common code never links an optional entity class. */
public interface NativeProjectile {
    /** Damage is computed by a native hit method, which reads the immutable launch multiplier itself. */
    interface DirectDamage {}
    interface ThrownStack { ItemStack immersive_smithing$thrownStack(); }
}
