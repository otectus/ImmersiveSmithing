package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.Integrations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Only the recovered equipment keeps its name; removed decorations keep native state. */
@Pseudo
@Mixin(targets = "com.magistuarmory.item.crafting.DecorationRemoveRecipe", remap = false)
public abstract class EpicDecorationNameMixin {
    @Inject(method = "takeApart", at = @At("RETURN"))
    private static void immersiveSmithing$personalName(ItemStack input, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!input.hasCustomHoverName() || !Integrations.enabled(BuiltInRegistries.ITEM.getKey(input.getItem()).getNamespace())) return;
        for (ItemStack result : cir.getReturnValue()) if (result.is(input.getItem())) result.setHoverName(input.getHoverName().copy());
    }
}
