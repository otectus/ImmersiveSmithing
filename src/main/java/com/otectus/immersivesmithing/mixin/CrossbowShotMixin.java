package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CrossbowItem.class)
public abstract class CrossbowShotMixin {
    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean immersive_smithing$snapshot(Level receiver, Entity projectile, Level level,
            LivingEntity shooter, InteractionHand hand, ItemStack crossbow, ItemStack ammunition,
            float soundPitch, boolean creative, float speed, float inaccuracy, float angle) {
        ProjectileEfficacy.snapshot(projectile, crossbow);
        return receiver.addFreshEntity(projectile);
    }
}
