package com.otectus.immersivesmithing.compat.epicknights;

import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;

/** Semantic roles without linking any optional mod class. A blocking weapon remains a weapon. */
public final class EpicEquipment {
    public enum Role { WEAPON, RANGED, SHIELD, HORSE_ARMOR, DECORATION, OTHER }

    private static final ClassValue<Role> ROLES = new ClassValue<>() {
        @Override protected Role computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                switch (c.getName()) {
                    case "com.magistuarmory.item.MedievalWeaponItem": return Role.WEAPON;
                    case "com.magistuarmory.item.MedievalShieldItem": return Role.SHIELD;
                    case "com.magistuarmory.thirtyyearswar.item.GunItem",
                         "net.mcreator.epicfletchery.item.CranequinCrossbowItem",
                         "net.mcreator.epicfletchery.item.GoatfootCrossbowItem",
                         "net.mcreator.epicfletchery.item.LatchetCrossbowItem": return Role.RANGED;
                    case "com.magistuarmory.item.ArmorDecorationItem", "com.magistuarmory.item.armor.WearableArmorDecorationItem": return Role.DECORATION;
                    case "net.minecraft.world.item.HorseArmorItem": return Role.HORSE_ARMOR;
                }
            }
            return Role.OTHER;
        }
    };

    public static Role role(ItemStack stack) {
        if (stack.getItem() instanceof HorseArmorItem) return Role.HORSE_ARMOR;
        if (stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) return Role.RANGED;
        return ROLES.get(stack.getItem().getClass());
    }

    private EpicEquipment() {}
}
