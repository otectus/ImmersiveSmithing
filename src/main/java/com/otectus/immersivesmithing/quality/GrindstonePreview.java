package com.otectus.immersivesmithing.quality;

import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** A read-only, per-player quote. Only the server can evaluate progression mods' cost hooks. */
public record GrindstonePreview(ResourceLocation dimension, BlockPos pos, ItemStack piece, int durabilityCost, int efficacyCost) {
    public static Optional<GrindstonePreview> forPlayer(ServerPlayer player, BlockPos pos) {
        double reach = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        if (!ServerConfig.get(ServerConfig.ENABLE_SMITH_GRINDSTONE) || !player.isAlive()
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > reach * reach
                || !player.serverLevel().hasChunkAt(pos)
                || !(player.serverLevel().getBlockState(pos).getBlock() instanceof SmithsGrindstoneBlock)) return Optional.empty();
        // The piece resting at the wheel is quoted; with none there, the piece in hand (what placing it would cost).
        ItemStack piece = player.serverLevel().getBlockEntity(pos) instanceof com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity stone
                && !stone.piece().isEmpty() ? stone.piece() : player.getMainHandItem();
        QualityData quality = QualityData.get(piece).orElse(null);
        if (quality == null && QualityEligibility.canCarryQuality(piece)) quality = QualityEligibility.neutral();
        if (quality == null || quality.faulty()) return Optional.empty();
        return Optional.of(new GrindstonePreview(player.level().dimension().location(), pos.immutable(), piece.copy(),
                SmithsGrindstoneBlock.levelCost(player, piece, quality.forgeScore()),
                SmithsGrindstoneBlock.levelCost(player, piece, quality.anvilScore())));
    }

    public boolean matches(ResourceLocation dimension, BlockPos pos, ItemStack held) {
        return this.dimension.equals(dimension) && this.pos.equals(pos) && ItemStack.matches(piece, held);
    }
}
