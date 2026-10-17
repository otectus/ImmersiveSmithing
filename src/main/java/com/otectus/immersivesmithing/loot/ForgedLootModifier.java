package com.otectus.immersivesmithing.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import com.otectus.immersivesmithing.quality.RandomQuality;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Grades equipment found in the world. Any loot item that can carry smithing quality (the forge could have made it,
 * or it is a loot, runic or unique weapon in {@code #immersive_smithing:quality_eligible}) and carries none yet receives
 * one, so found gear reads like forged gear. A stack that already has a grade is never touched and no maker's mark is
 * ever written. The band comes from the server config: Standard only by default, so anything Fine or better is someone's work.
 */
public class ForgedLootModifier extends LootModifier {
    public static final Codec<ForgedLootModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, ForgedLootModifier::new));

    public ForgedLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ServerConfig.LootQualityMode mode = ServerConfig.get(ServerConfig.LOOT_QUALITY_MODE);
        if (mode == ServerConfig.LootQualityMode.OFF || loot.isEmpty()) return loot;
        if (isExcluded(context.getQueriedLootTableId())) return loot;
        for (ItemStack stack : loot) {
            if (stack.isEmpty() || QualityData.has(stack) || !QualityEligibility.canCarryQuality(stack)) continue;
            RandomQuality.forLoot(mode, context.getRandom()).apply(stack);
        }
        return loot;
    }

    /** Entries are full table ids or bare namespaces. */
    public static boolean isExcluded(ResourceLocation table) {
        List<String> excluded = ServerConfig.list(ServerConfig.LOOT_QUALITY_EXCLUDED_TABLES);
        if (excluded.isEmpty()) return false;
        String id = table.toString();
        for (String entry : excluded) {
            if (entry.equals(id) || (entry.indexOf(':') < 0 && entry.equals(table.getNamespace()))) return true;
        }
        return false;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
