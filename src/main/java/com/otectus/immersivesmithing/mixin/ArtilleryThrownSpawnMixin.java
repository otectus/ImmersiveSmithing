package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.ArtilleryThrow;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {"net.mcreator.artilleryaddon.entity.JavelinProectileEntity", "net.mcreator.artilleryaddon.entity.ThrowingCrossProEntity",
        "net.mcreator.artilleryaddon.procedures.JavelinPlayerFinishesUsingItemProcedure",
        "net.mcreator.artilleryaddon.procedures.ThrowingCrossPlayerFinshesUseingProcedure"}, remap = false)
public abstract class ArtilleryThrownSpawnMixin {
    @Redirect(method = {"shoot*", "execute"}, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean immersive_smithing$saveWeapon(Level world, Entity projectile) {
        ArtilleryThrow.snapshot(projectile);
        return world.addFreshEntity(projectile);
    }
}
