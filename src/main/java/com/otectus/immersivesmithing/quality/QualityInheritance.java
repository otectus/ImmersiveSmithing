package com.otectus.immersivesmithing.quality;

import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.RecyclingData;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Quality inheritance at the crafting table: a recipe that turns exactly one graded piece into other equipment
 * (dyeing a helmet, making a sword into a spear, an elemental upgrade) keeps that piece's quality and maker's mark.
 * Applied to the crafting preview ({@code CraftingMenuMixin}) so the shown and the taken result agree, and again when
 * the result is taken, for crafting screens that replace the vanilla grid logic. Two graded inputs have no honest
 * answer and no quality is inherited. Recycling accounting is preserved independently of quality. Smithing-table
 * upgrades need no help: they copy the base piece's data themselves.
 */
public final class QualityInheritance {

    /** Writes the one graded input's quality and mark onto {@code result}; returns whether it did. */
    public static boolean inherit(ItemStack result, Container inputs) {
        RecyclingData.inherit(result, inputs);
        if (!ServerConfig.get(ServerConfig.INHERIT_QUALITY_ON_CRAFT)) return false;
        if (result.isEmpty() || QualityData.has(result) || !EquipmentClassifier.isCandidate(result)) return false;
        ItemStack source = ItemStack.EMPTY;
        for (int i = 0; i < inputs.getContainerSize(); i++) {
            ItemStack input = inputs.getItem(i);
            if (input.isEmpty() || !QualityData.has(input)) continue;
            if (!source.isEmpty()) return false;
            source = input;
        }
        if (source.isEmpty()) return false;
        QualityData.get(source).ifPresent(q -> q.apply(result));
        MakersMark.copy(source, result);
        return true;
    }

    private QualityInheritance() {}
}
