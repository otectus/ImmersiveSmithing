package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.ArtilleryThrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = {"net.mcreator.artilleryaddon.procedures.JavelinPlayerFinishesUsingItemProcedure",
        "net.mcreator.artilleryaddon.procedures.ThrowingCrossPlayerFinshesUseingProcedure",
        "net.mcreator.artilleryaddon.procedures.GrenadeProcedure", "net.mcreator.artilleryaddon.procedures.DeleteThrownProcedure"}, remap = false)
public abstract class ArtilleryThrownHandMixin {
    @Redirect(method = "execute", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack immersive_smithing$actualHand(LivingEntity owner) { return ArtilleryThrow.actualHand(owner); }
}
