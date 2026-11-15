package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.NativeProjectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = {"com.TBK.medieval_boomsticks.server.entity.ThrownJavelin",
        "com.TBK.medieval_boomsticks.server.entity.ThrowableWeapon"}, remap = false)
public abstract class BoomsticksThrownStackMixin implements NativeProjectile.ThrownStack {
    @Accessor("javelinItem")
    public abstract ItemStack immersive_smithing$thrownStack();
}
