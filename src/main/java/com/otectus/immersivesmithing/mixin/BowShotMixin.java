package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BowItem.class)
public abstract class BowShotMixin {
    @Redirect(method = "releaseUsing", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean immersive_smithing$snapshot(Level receiver, Entity projectile, ItemStack bow,
                                               Level level, LivingEntity shooter, int remaining) {
        ProjectileEfficacy.snapshot(projectile, bow);
        return receiver.addFreshEntity(projectile);
    }
}
