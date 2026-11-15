package com.otectus.immersivesmithing.compat.epicknights;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Loaded ammunition belongs to the weapon. Destructive operations require it to be fired/unloaded natively first. */
public final class LoadedEquipment {
    public static boolean hasPayload(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        String mod = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return switch (mod) {
            case "thirtyyearswar" -> tag.getBoolean("Charged");
            case "medieval_boomsticks" -> tag.getBoolean("Charged") || tag.getBoolean("recharge")
                    || !tag.getList("ChargedProjectiles", Tag.TAG_COMPOUND).isEmpty();
            case "fletchery" -> tag.getDouble("loaded") > 0;
            case "artillery_addon" -> artilleryPayload(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(), tag);
            default -> false;
        };
    }

    private static boolean artilleryPayload(String item, CompoundTag tag) {
        if (item.equals("dual_wheellock_pistol") || item.equals("dual_wheellock_carbine")) {
            // 'loaded' records the last barrel selected, even after both barrels are empty.
            return tag.getDouble("barrel_one") > 0 || tag.getDouble("barrel_two") > 0;
        }
        if (item.equals("chu_ko_nu")) return tag.getDouble("ammo") > 0;
        if (item.equals("fire_lance")) return tag.getDouble("ammo") > 0 || tag.getDouble("powder") > 0 || tag.getDouble("stage") > 0;
        if (tag.getBoolean("loaded") || tag.getDouble("powder") > 0) return true;
        int clearedStage = switch (item) {
            case "arquebus", "bronze_handgonne", "hackbut", "hand_cannon", "harquebus", "markmengonne", "matchlock_pistol",
                    "matchlock_musket", "mini_pistola", "noble_handgonne", "handgonne", "tiller_gun", "taccola_handgonne",
                    "toradar_rifle", "wheellock_hand_mortar" -> 3;
            case "matchlock_carbine", "wheellock_breechloading_rifle", "wheellock_hunting_rifle", "wheellock_musket",
                    "wheellock_pistol", "wheellock_wallgun" -> 4;
            default -> -1;
        };
        double stage = tag.getDouble("stage");
        // Ammo on single-shot guns is an ammunition TYPE, retained after firing; do not treat it as a count.
        return stage > 0 && stage != clearedStage;
    }
    private LoadedEquipment() {}
}
