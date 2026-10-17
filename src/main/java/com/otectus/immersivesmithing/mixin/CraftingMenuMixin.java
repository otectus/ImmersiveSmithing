package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.quality.QualityInheritance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows inherited quality in the crafting preview (crafting table and inventory grid share this method), so the result
 * a player sees is the result they take. The grid is remembered for the duration of the call because the result slot
 * write does not carry it. {@code require = 0}: a crafting mod that replaces this logic loses only the preview; the
 * quality is still applied when the result is taken (ItemCraftedEvent).
 */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Unique
    private static final ThreadLocal<CraftingContainer> immersive_smithing$grid = new ThreadLocal<>();

    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), require = 0)
    private static void immersive_smithing$rememberGrid(AbstractContainerMenu menu, Level level, Player player, CraftingContainer grid,
                                                        ResultContainer result, CallbackInfo ci) {
        immersive_smithing$grid.set(grid);
    }

    @ModifyArg(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1, require = 0)
    private static ItemStack immersive_smithing$inheritQuality(ItemStack stack) {
        CraftingContainer grid = immersive_smithing$grid.get();
        // In place: the same stack is sent to the client right after.
        if (grid != null) QualityInheritance.inherit(stack, grid);
        return stack;
    }

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"), require = 0)
    private static void immersive_smithing$forgetGrid(AbstractContainerMenu menu, Level level, Player player, CraftingContainer grid,
                                                      ResultContainer result, CallbackInfo ci) {
        immersive_smithing$grid.remove();
    }
}
