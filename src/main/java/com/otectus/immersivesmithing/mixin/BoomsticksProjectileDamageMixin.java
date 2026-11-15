package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.NativeProjectile;
import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** These projectiles ignore AbstractArrow.baseDamage. Scale each native damage portion without changing penetration. */
@Pseudo
@Mixin(targets = {"com.TBK.medieval_boomsticks.server.entity.HeavyBoltProjectile",
        "com.TBK.medieval_boomsticks.server.entity.RoundBallProjectile"}, remap = false)
public abstract class BoomsticksProjectileDamageMixin implements NativeProjectile.DirectDamage {
    @ModifyArg(method = {"onHitEntity", "m_5790_"}, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), index = 1)
    private float immersive_smithing$damage(float damage) {
        return (float) (damage * ProjectileEfficacy.launchMultiplier((Entity) (Object) this));
    }
}
