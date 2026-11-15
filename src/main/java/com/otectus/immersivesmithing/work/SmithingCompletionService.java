package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.RecyclingData;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.SmithingQuality;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * The one place a workpiece becomes its finished item, in this order: the result from its frozen target snapshot, quality,
 * the item's own crafting initialisation, recycling provenance, the maker's mark, then {@link ItemSmithedEvent} with the live
 * stack, then (optionally) the vanilla crafted event. The caller removes the workpiece from its holder in the same server
 * tick before calling and then commits the returned stack to an output or inventory, so a piece can only complete once and
 * the rewards are given once. The smith is the server-validated player performing the completing action.
 */
public final class SmithingCompletionService {

    public static ItemStack complete(ServerPlayer smith, WorkpieceData piece, BlockPos completionPos, ItemSmithedEvent.Station station) {
        ProcessState p = piece.process();
        int forge = piece.forgeScore() >= 0 ? piece.forgeScore()
                : WorkQuality.forgeScore(p, 0, ServerConfig.get(ServerConfig.WORK_HEAT_WEIGHT));
        int anvil = piece.anvilScore() >= 0 ? piece.anvilScore() : WorkQuality.anvilScore(p, 0);
        boolean faulty = piece.faulty() || p.burnt;
        QualityData quality = new QualityData(forge, anvil, faulty);

        ItemStack result = piece.target().copy();
        quality.apply(result);
        // As if crafted: the crafted statistic, plus items that initialise their NBT on craft (Spartan throwing weapons).
        result.onCraftedBy(smith.level(), smith, result.getCount());
        RecyclingData.record(result, piece.family(), piece.recoverableUnits() / Math.max(1, result.getCount()));
        MakersMark.stamp(result, smith);
        BlockPos trough = p.quenchPos != null ? p.quenchPos : completionPos;
        // Progression mods hear about the piece before it reaches its output and may still change it.
        MinecraftForge.EVENT_BUS.post(new ItemSmithedEvent(smith, result, quality, piece.recipeId(), piece.family(),
                piece.metalUnits(), trough, completionPos, station));
        if (ServerConfig.get(ServerConfig.FIRE_VANILLA_CRAFT_EVENT)) {
            ForgeEventFactory.firePlayerCraftingEvent(smith, result, new SimpleContainer(1));
        }
        SmithingQuality label = quality.quality();
        if (label == SmithingQuality.FAULTY) ModAdvancements.award(smith, ModAdvancements.THATLL_BUFF_OUT);
        if (label == SmithingQuality.FINE || label == SmithingQuality.MASTERWORK) ModAdvancements.award(smith, ModAdvancements.FINE_WORK);
        if (label == SmithingQuality.MASTERWORK) ModAdvancements.award(smith, ModAdvancements.MASTER_SMITH);
        return result;
    }

    private SmithingCompletionService() {}
}
