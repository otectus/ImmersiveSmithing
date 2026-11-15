package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.Integrations;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve a personal name; native surcoat names still apply to unnamed equipment. */
@Pseudo
@Mixin(targets = {"com.magistuarmory.item.crafting.HeraldryRecipe",
        "com.magistuarmory.item.crafting.ArmorDecorationRecipe"}, remap = false)
public abstract class EpicHeraldryNameMixin {
    @Inject(method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void immersiveSmithing$personalName(CraftingContainer input, RegistryAccess access, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result.isEmpty() || !Integrations.enabled(BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace())) return;
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            ItemStack base = input.getItem(slot);
            if (base.is(result.getItem()) && base.hasCustomHoverName()) {
                result.setHoverName(base.getHoverName().copy());
                return;
            }
        }
    }
}
