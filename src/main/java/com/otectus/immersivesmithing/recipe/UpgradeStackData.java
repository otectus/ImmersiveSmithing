package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.quality.QualityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.List;

/** Structural upgrades transfer reviewed personal/equipment fields; target defaults and capabilities stay authoritative. */
public final class UpgradeStackData {
    public static void transfer(ItemStack base, ItemStack target) {
        CompoundTag source = base.getTag();
        if (source == null) return;
        CompoundTag own = target.getOrCreateTag();
        for (String key : List.of("RepairCost", "Trim", "HideFlags", "CustomModelData", "Unbreakable", "ArmorDecoration")) {
            if (source.contains(key) && !own.contains(key)) own.put(key, source.get(key).copy());
        }
        mergeFields(source, own, "display", List.of("Name", "Lore", "color"));
        // Heraldry uses vanilla banner state. Never transfer block inventories or arbitrary block-entity payloads.
        mergeFields(source, own, "BlockEntityTag", List.of("Patterns", "Base"));
        var enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(base));
        EnchantmentHelper.getEnchantments(target).forEach((enchantment, level) -> enchantments.merge(enchantment, level, Math::max));
        if (!enchantments.isEmpty()) EnchantmentHelper.setEnchantments(enchantments, target);
        transferWear(base, target);
        // A structural upgrade receives new scores and maker at quenching, but cannot erase fractional wear.
        CompoundTag quality = base.getTagElement(QualityData.ROOT);
        if (quality != null && quality.contains(QualityData.CARRY, Tag.TAG_ANY_NUMERIC)) {
            double carry = quality.getDouble(QualityData.CARRY);
            if (Double.isFinite(carry) && carry >= 0 && carry < 1) target.getOrCreateTagElement(QualityData.ROOT).putDouble(QualityData.CARRY, carry);
        }
    }

    private static void mergeFields(CompoundTag source, CompoundTag target, String compound, List<String> keys) {
        if (!source.contains(compound, Tag.TAG_COMPOUND)) return;
        CompoundTag from = source.getCompound(compound);
        CompoundTag into = target.getCompound(compound).copy();
        for (String key : keys) if (from.contains(key) && !into.contains(key)) into.put(key, from.get(key).copy());
        if (!into.isEmpty()) target.put(compound, into);
    }

    /** Equal durability keeps exact damage. Otherwise round wear upward; a reverse conversion cannot repair the piece. */
    public static void transferWear(ItemStack source, ItemStack target) {
        if (!source.isDamageableItem() || !target.isDamageableItem()) return;
        long damage = Math.max(0, source.getDamageValue());
        if (source.getMaxDamage() != target.getMaxDamage()) {
            damage = (damage * target.getMaxDamage() + source.getMaxDamage() - 1L) / source.getMaxDamage();
        }
        target.setDamageValue(Math.max(target.getDamageValue(), (int) Math.min(target.getMaxDamage(), damage)));
    }

    private UpgradeStackData() {}
}
