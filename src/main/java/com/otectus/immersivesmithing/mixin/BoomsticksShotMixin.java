package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Release and alpha share this inspected launch signature; the native projectile handles all other behavior. */
@Pseudo
@Mixin(targets = "com.TBK.medieval_boomsticks.common.items.RechargeItem", remap = false)
public abstract class BoomsticksShotMixin {
    @Redirect(method = {"shootProjectile", "m_40894_"}, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean immersive_smithing$snapshot(Level receiver, Entity projectile, Level level,
            LivingEntity shooter, InteractionHand hand, ItemStack weapon, ItemStack ammunition,
            float pitch, boolean creative, float speed, float inaccuracy, float angle) {
        ProjectileEfficacy.epicSnapshot(projectile, weapon);
        return receiver.addFreshEntity(projectile);
    }
}
