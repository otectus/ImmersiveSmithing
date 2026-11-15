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

/** Pinned procedural launch boundary: capture the provided weapon before spawning each projectile. */
@Pseudo
@Mixin(targets = {"net.mcreator.epicfletchery.procedures.BoltShootProcedure"}, remap = false)
public abstract class FletcheryShotMixin {
    @Redirect(method = "execute", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean immersive_smithing$snapshot(Level receiver, Entity projectile, LevelAccessor world,
            double x, double y, double z, Entity shooter, ItemStack weapon) {
        ProjectileEfficacy.epicSnapshot(projectile, weapon);
        return receiver.addFreshEntity(projectile);
    }
}
