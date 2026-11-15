package com.otectus.immersivesmithing.compat.epicknights;

import com.google.common.collect.BiMap;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.recipe.UpgradeStackData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Equivalent helmet swaps keep the native full tag; they never reroll quality or change the maker. */
public final class VisorSwap {
    public static void apply(ServerPlayer sender, int requestedPlayer, String requestedItem) {
        if (sender == null || sender.getId() != requestedPlayer || !sender.isAlive()) return;
        ResourceLocation targetId = ResourceLocation.tryParse(requestedItem);
        if (targetId == null || !BuiltInRegistries.ITEM.containsKey(targetId)) return;
        ItemStack source = sender.getItemBySlot(EquipmentSlot.HEAD);
        if (!(source.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != EquipmentSlot.HEAD) return;
        String sourceId = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        var pairs = com.otectus.immersivesmithing.config.ServerConfig.list(com.otectus.immersivesmithing.config.ServerConfig.EPIC_KNIGHTS_HELMET_PAIRS);
        if (!pairs.contains(sourceId + "-" + requestedItem) && !pairs.contains(requestedItem + "-" + sourceId)) return;
        ItemStack target = new ItemStack(BuiltInRegistries.ITEM.get(targetId));
        if (!(target.getItem() instanceof ArmorItem targetArmor) || targetArmor.getEquipmentSlot() != EquipmentSlot.HEAD) return;
        // This is the addon's equivalent-state operation, not a structural upgrade. Its native contract copies NBT.
        if (source.hasTag()) target.setTag(source.getTag().copy());
        UpgradeStackData.transferWear(source, target);
        sender.setItemSlot(EquipmentSlot.HEAD, target);
    }
    private VisorSwap() {}
}
