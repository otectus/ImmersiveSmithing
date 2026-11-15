package com.otectus.immersivesmithing.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.block.SmithsTroughBlock;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.client.work.HeatColors;
import com.otectus.immersivesmithing.client.work.WorkEffects;
import com.otectus.immersivesmithing.client.work.WorkpieceRenderer;
import com.otectus.immersivesmithing.client.work.WorkpieceShape;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The trough's water surface (its height follows the quenches left) and a piece being quenched: held in tongs, lowered from
 * above the rim through the waterline and kept under it while it hisses. The real piece is in the trough's block entity
 * only; these tongs are a drawn tool, not an item.
 */
public class SmithsTroughRenderer implements BlockEntityRenderer<SmithsTroughBlockEntity> {
    private static final ResourceLocation WATER = new ResourceLocation("block/water_still");

    public SmithsTroughRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() { return 48; }

    @Override
    public void render(SmithsTroughBlockEntity trough, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        WorkEffects.seen(trough);
        float fill = trough.clientFillFraction();
        BlockState state = trough.getBlockState();
        Direction facing = state.getBlock() instanceof SmithsTroughBlock ? state.getValue(SmithsTroughBlock.FACING) : Direction.NORTH;
        float surface = surfaceHeight(fill);
        WorkpieceData piece = trough.quenchPiece();
        if (piece != null) renderPiece(trough, piece, facing, surface, partialTick, pose, buffers, light);
        if (fill <= 0F) return;
        boolean alongX = !(state.getBlock() instanceof SmithsTroughBlock) || facing.getAxis() == Direction.Axis.Z;
        int color = trough.getLevel() != null ? BiomeColors.getAverageWaterColor(trough.getLevel(), trough.getBlockPos()) : 0x3F76E4;
        float x0 = alongX ? 2 / 16F : 4 / 16F;
        float x1 = alongX ? 14 / 16F : 12 / 16F;
        float z0 = alongX ? 4 / 16F : 2 / 16F;
        float z1 = alongX ? 12 / 16F : 14 / 16F;
        RenderUtil.horizontalQuad(pose, buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)),
                RenderUtil.sprite(WATER), x0, z0, x1, z1, surface, 0xC0000000 | (color & 0xFFFFFF), light);
    }

    private void renderPiece(SmithsTroughBlockEntity trough, WorkpieceData piece, Direction facing, float surface, float partialTick,
                             PoseStack pose, MultiBufferSource buffers, int light) {
        ProcessState p = piece.process();
        WorkpieceShape shape = WorkpieceShape.of(piece);
        float above = surface + 0.42F;
        float under = Math.max(3.4F / 16F, surface - 0.06F - 0.08F * p.profile.immersionMin() / 1000F);
        float lowering = trough.immersed() ? 1F : Math.min(1F, (trough.lowerTicks() + partialTick) / QuenchRules.BOUNDARY_TICKS);
        float eased = lowering * lowering * (3F - 2F * lowering);
        float y = above + (under - above) * eased;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationLayout.yaw(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.pushPose();
        pose.translate(0.5, y, 0.5);
        float scale = Math.min(1F, 0.72F / Math.max(0.05F, shape.length));
        pose.scale(scale, scale, scale);
        WorkpieceRenderer.render(pose, buffers, shape, p.heat, HeatColors.coldColor(piece.family().getPath()), light, null, -1, false);
        pose.popPose();
        // The tongs grip the tang end and rise out of the water toward the smith's side.
        com.otectus.immersivesmithing.client.work.SceneTools.renderTongs(pose, buffers,
                0.5 - shape.length * scale / 2F + Math.min(0.1, shape.length * scale / 4F), y + shape.thickness[0] * scale / 2F, 0.5, light);
        pose.popPose();
    }

    public static float surfaceHeight(float fill) {
        return (float) StationLayout.troughSurface(fill);
    }
}
