package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.magistuarmory.thirtyyearswar.item.GunItem", remap = false)
public abstract class ThirtyYearsWarGunMixin {
    @Shadow private static void hurtWithArmorPiercing(Entity target, DamageSource source, float damage, int piercing) {
        throw new AssertionError();
    }

    @Redirect(method = "performShooting", at = @At(value = "INVOKE", target =
            "Lcom/magistuarmory/thirtyyearswar/item/GunItem;hurtWithArmorPiercing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FI)V"))
    private void immersive_smithing$shotDamage(Entity target, DamageSource damageSource, float damage, int piercing,
                                               ItemStack firingStack, Level level, LivingEntity shooter) {
        double multiplier = ServerConfig.get(ServerConfig.EPIC_KNIGHTS_RANGED_EFFICACY)
                ? ProjectileEfficacy.efficacy(firingStack) : 1;
        hurtWithArmorPiercing(target, damageSource, (float) (damage * multiplier), piercing);
    }
}
