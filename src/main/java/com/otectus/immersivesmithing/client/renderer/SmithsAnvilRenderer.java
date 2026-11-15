package com.otectus.immersivesmithing.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.block.SmithsAnvilBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.client.work.FocusController;
import com.otectus.immersivesmithing.client.work.BladeWorkpieceModel;
import com.otectus.immersivesmithing.client.work.HeatColors;
import com.otectus.immersivesmithing.client.work.SceneTools;
import com.otectus.immersivesmithing.client.work.WorkEffects;
import com.otectus.immersivesmithing.client.work.WorkFocusScreen;
import com.otectus.immersivesmithing.client.work.WorkTargets;
import com.otectus.immersivesmithing.client.work.WorkpieceRenderer;
import com.otectus.immersivesmithing.client.work.WorkpieceShape;
import com.otectus.immersivesmithing.client.work.WorkpieceVisuals;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
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
import net.minecraft.world.phys.Vec3;

/**
 * The anvil's face: the piece as it is being worked (its shape from the shared work geometry, glowing by zone, chalk guides
 * for the smith working it), the reserved components on the fitting tray and fitted ones in their sockets, the finished
 * piece waiting to be collected (its real item model), and, for the focused smith only, their hammer at the work.
 */
public class SmithsAnvilRenderer implements BlockEntityRenderer<SmithsAnvilBlockEntity> {
    private final ItemRenderer itemRenderer;

    public SmithsAnvilRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public int getViewDistance() { return 48; }

    @Override
    public void render(SmithsAnvilBlockEntity anvil, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = anvil.getBlockState();
        Direction facing = state.getBlock() instanceof SmithsAnvilBlock ? state.getValue(SmithsAnvilBlock.FACING) : Direction.NORTH;
        WorkEffects.seen(anvil);
        if (anvil.hasOutput()) renderOutput(anvil, anvil.output(), facing, pose, buffers, light);
        WorkpieceData piece = anvil.workpiece();
        if (piece != null) {
            ProcessState p = piece.process();
            WorkpieceShape shape = WorkpieceShape.of(piece);
            BladeWorkpieceModel blade = BladeWorkpieceModel.get(piece.target(), shape.visual);
            boolean focused = FocusController.focusedOn(anvil.getBlockPos());
            WorkFocusScreen screen = focused ? WorkFocusScreen.showing() : null;
            WorkTargets.Target target = screen != null ? screen.hoveredTarget() : null;
            int hover = target != null && target.kind() == WorkTargets.Kind.OPERATION ? target.index() : -1;
            boolean guides = ClientConfig.get(ClientConfig.SHOW_WORK_GUIDES) && (focused || p.stage.shapeable());
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(StationLayout.yaw(facing)));
            pose.translate(-0.5, 0, -0.5);
            pose.pushPose();
            pose.translate(StationLayout.ANVIL_WORK_CENTER.x, StationLayout.ANVIL_FACE + 0.002, StationLayout.ANVIL_WORK_CENTER.z);
            pose.mulPose(Axis.YP.rotationDegrees(90F));
            if (blade != null && (p.allComplete() || p.stage.quenched())) {
                blade.render(pose, buffers, shape.visual, p, anvil.currentHeat(), HeatColors.coldColor(piece.family().getPath()), light);
            } else {
                WorkpieceRenderer.render(pose, buffers, shape, anvil.currentHeat(), HeatColors.coldColor(piece.family().getPath()), light,
                        p.stage.shapeable() ? p : null, hover, guides && p.stage.shapeable());
            }
            pose.popPose();
            if (!p.sockets.isEmpty()) renderComponents(anvil, p, shape, blade != null, pose, buffers, light);
            pose.popPose();
        }
        if (FocusController.focusedOn(anvil.getBlockPos())) {
            pose.pushPose();
            SceneTools.renderHammer(pose, buffers, anvil.getBlockPos(), light);
            pose.popPose();
        }
    }

    /** Unfitted components wait on the tray beside the work; fitted ones sit in their sockets on the piece. */
    private void renderComponents(SmithsAnvilBlockEntity anvil, ProcessState p, WorkpieceShape shape, boolean blade, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean quenched = p.stage.quenched();
        int waiting = 0;
        for (int i = 0; i < p.sockets.size(); i++) {
            ProcessState.Socket s = p.sockets.get(i);
            ItemStack shown = s.display();
            if (shown.isEmpty()) continue;
            // These components are already represented by the blade's own fitted grip/guard pixels.
            boolean grip = s.name().equals("grip") || s.name().equals("haft") || s.name().equals("strap");
            if (blade && s.attached() && (grip || s.name().equals("guard"))) continue;
            Vec3 at;
            float yaw;
            if (s.attached()) {
                float[] uv = shape.visual.socket(s.name(), i);
                at = WorkTargets.pieceToAnvil(shape.point(uv[0], WorkpieceShape.visibleV(uv[1], p.flipped)));
                yaw = grip ? 90F : 0F;
            } else {
                // A blade's tang reaches over the supply strip. Keep its waiting grip alongside it, in view.
                at = StationLayout.ANVIL_SUPPLY.add((blade ? 0.18 : 0) + (waiting - (p.socketsLeft() - 1) / 2.0) * 0.14, 0.004, 0);
                waiting++;
                yaw = 90F;
            }
            pose.pushPose();
            pose.translate(at.x, at.y + 0.01, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.mulPose(Axis.ZP.rotationDegrees(-45F));
            float scale = s.attached() ? 0.3F : 0.22F;
            if (!s.attached() && quenched && ClientConfig.get(ClientConfig.SHOW_WORK_GUIDES)) {
                // The component that fits next lifts slightly: the cue for what the next action uses.
                scale *= 1.0F + 0.08F * Mth.sin((anvil.clock() % 40) / 40F * Mth.TWO_PI);
            }
            pose.scale(scale, scale, scale);
            itemRenderer.renderStatic(shown, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, anvil.getLevel(), i);
            pose.popPose();
        }
    }

    /** The finished piece: its own item model, lying on the face. */
    private void renderOutput(SmithsAnvilBlockEntity anvil, ItemStack output, Direction facing, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationLayout.yaw(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.translate(StationLayout.ANVIL_WORK_CENTER.x, StationLayout.ANVIL_FACE + 0.002, StationLayout.ANVIL_WORK_CENTER.z);
        pose.mulPose(Axis.YP.rotationDegrees(90F));
        var visual = WorkpieceVisuals.get(anvil.outputVisual());
        BladeWorkpieceModel blade = BladeWorkpieceModel.get(output, visual);
        if (blade != null) {
            blade.pose(pose, visual.length(), anvil.outputFlipped());
        } else {
            pose.translate(0, 0.03, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-90F));
            pose.mulPose(Axis.ZP.rotationDegrees(-45F));
            pose.scale(0.55F, 0.55F, 0.55F);
        }
        itemRenderer.renderStatic(output, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, anvil.getLevel(),
                (int) anvil.getBlockPos().asLong());
        pose.popPose();
    }
}
