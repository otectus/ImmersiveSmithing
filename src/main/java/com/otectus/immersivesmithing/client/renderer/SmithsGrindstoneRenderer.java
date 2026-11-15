package com.otectus.immersivesmithing.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.client.work.WorkEffects;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.work.StationLayout;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The grinding wheel, turning only while an edge is actually held to it, and the piece: resting on the base, or held with
 * its edge on the top of the wheel during a pass.
 */
public class SmithsGrindstoneRenderer implements BlockEntityRenderer<SmithsGrindstoneBlockEntity> {
    private final ItemRenderer itemRenderer;

    public SmithsGrindstoneRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public int getViewDistance() { return 48; }

    @Override
    public void render(SmithsGrindstoneBlockEntity stone, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        WorkEffects.seen(stone);
        BlockState state = stone.getBlockState();
        Direction facing = state.getBlock() instanceof SmithsGrindstoneBlock ? state.getValue(SmithsGrindstoneBlock.FACING) : Direction.NORTH;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationLayout.yaw(facing)));
        pose.translate(-0.5, 0, -0.5);
        float angle = Mth.lerp(partialTick, stone.wheelAngleO, stone.wheelAngle);
        if (ClientConfig.get(ClientConfig.REDUCED_MOTION)) angle *= 0.5F;
        pose.pushPose();
        // The wheel turns about the axle (x axis through y 10, z 8).
        pose.translate(0, 10 / 16F, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        pose.translate(0, -10 / 16F, -0.5);
        StationModels.render(StationModels.GRINDSTONE_WHEEL, pose, buffers, light, 0xFFFFFF);
        pose.popPose();
        ItemStack piece = stone.piece();
        if (!piece.isEmpty()) {
            pose.pushPose();
            if (stone.contact() != null) {
                // Edge to the top of the wheel, blade angled away from the smith.
                pose.translate(StationLayout.WHEEL_CONTACT.x, StationLayout.WHEEL_CONTACT.y + 0.012, StationLayout.WHEEL_CONTACT.z);
                pose.mulPose(Axis.XP.rotationDegrees(74F));
                pose.mulPose(Axis.ZP.rotationDegrees(-45F + (ClientConfig.get(ClientConfig.REDUCED_MOTION) ? 0F : 3F * Mth.sin(stone.clock() * 0.6F))));
            } else {
                pose.translate(StationLayout.GRINDSTONE_REST.x, StationLayout.GRINDSTONE_REST.y + 0.01, StationLayout.GRINDSTONE_REST.z);
                pose.mulPose(Axis.XP.rotationDegrees(90F));
                pose.mulPose(Axis.ZP.rotationDegrees(45F));
            }
            pose.scale(0.5F, 0.5F, 0.5F);
            itemRenderer.renderStatic(piece, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, stone.getLevel(),
                    (int) stone.getBlockPos().asLong());
            pose.popPose();
        }
        pose.popPose();
    }
}
