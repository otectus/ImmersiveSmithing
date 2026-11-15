package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.ArtilleryThrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"net.mcreator.artilleryaddon.item.JavelinItem", "net.mcreator.artilleryaddon.item.ThrowingCrossItem"}, remap = false)
public abstract class ArtilleryThrownUseMixin {
    @Inject(method = {"finishUsingItem", "m_5922_"}, at = @At("HEAD"))
    private void immersive_smithing$beginFinish(ItemStack stack, Level level, LivingEntity owner, CallbackInfoReturnable<ItemStack> ci) {
        ArtilleryThrow.begin(owner, stack);
    }
    @Inject(method = {"finishUsingItem", "m_5922_"}, at = @At("RETURN"))
    private void immersive_smithing$endFinish(ItemStack stack, Level level, LivingEntity owner, CallbackInfoReturnable<ItemStack> ci) {
        ArtilleryThrow.end();
    }
    @Inject(method = {"releaseUsing", "m_5551_"}, at = @At("HEAD"))
    private void immersive_smithing$beginRelease(ItemStack stack, Level level, LivingEntity owner, int remaining, CallbackInfo ci) {
        ArtilleryThrow.begin(owner, stack);
    }
    @Inject(method = {"releaseUsing", "m_5551_"}, at = @At("RETURN"))
    private void immersive_smithing$endRelease(ItemStack stack, Level level, LivingEntity owner, int remaining, CallbackInfo ci) {
        ArtilleryThrow.end();
    }
}
