package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.NativeProjectile;
import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The first float store is configured physical damage; enchantment and special effects are added afterwards. */
@Pseudo
@Mixin(targets = {"com.TBK.medieval_boomsticks.server.entity.ThrownJavelin",
        "com.TBK.medieval_boomsticks.server.entity.ThrowableWardart",
        "com.TBK.medieval_boomsticks.server.entity.ThrowableAxe",
        "com.TBK.medieval_boomsticks.server.entity.ThrowableKnife"}, remap = false)
public abstract class BoomsticksThrownDamageMixin implements NativeProjectile.DirectDamage {
    @ModifyVariable(method = {"onHitEntity", "m_5790_"}, at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private float immersive_smithing$damage(float damage) {
        return (float) (damage * ProjectileEfficacy.launchMultiplier((Entity) (Object) this));
    }
}
