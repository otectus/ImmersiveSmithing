package com.otectus.immersivesmithing.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.client.work.FocusController;
import com.otectus.immersivesmithing.client.work.HeatColors;
import com.otectus.immersivesmithing.client.work.WorkEffects;
import com.otectus.immersivesmithing.client.work.WorkFocusScreen;
import com.otectus.immersivesmithing.client.work.WorkTargets;
import com.otectus.immersivesmithing.client.work.WorkpieceRenderer;
import com.otectus.immersivesmithing.client.work.WorkpieceShape;
import com.otectus.immersivesmithing.client.work.WorkpieceVisuals;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.ForgeJob;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

import java.util.List;

/**
 * The forge's working parts: fuel in the firebox, the molten basin (back of the hearth), unmelted stock, the billet on its
 * tray glowing zone by zone, the piece on the reheat rest, the bellows' board as it is pumped, the pattern book on the left
 * rim with the pinned drawing beside it, and the reserved components on the right rim. Staged, bounded, no entities.
 */
public class SmithsForgeRenderer implements BlockEntityRenderer<SmithsForgeBlockEntity> {
    private static final ResourceLocation LAVA = new ResourceLocation("block/lava_still");
    private static final ResourceLocation MOLTEN = ImmersiveSmithing.id("block/molten_metal");
    private static final ResourceLocation PAPER = new ResourceLocation("block/white_wool");
    private static final float[][] PILE = {
            {0.42F, 0.62F, 0F}, {0.62F, 0.62F, 0F}, {0.42F, 0.72F, 90F},
            {0.62F, 0.72F, 90F}, {0.42F, 0.62F, 0F}, {0.62F, 0.72F, 0F}
    };
    private static final float[][] FUEL = {
            {0.38F, 0.32F, 12F}, {0.6F, 0.34F, -10F}, {0.48F, 0.61F, 20F}, {0.5F, 0.44F, 5F}
    };
    private final ItemRenderer itemRenderer;

    public SmithsForgeRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public int getViewDistance() { return 48; }

    @Override
    public void render(SmithsForgeBlockEntity forge, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = forge.getBlockState();
        if (!(state.getBlock() instanceof SmithsForgeBlock)) return;
        WorkEffects.seen(forge);
        Direction facing = state.getValue(SmithsForgeBlock.FACING);
        boolean lit = state.getValue(SmithsForgeBlock.LIT);
        boolean distant = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(
                forge.getBlockPos().getX() + 0.5, forge.getBlockPos().getY() + 0.5, forge.getBlockPos().getZ() + 0.5) > 24 * 24;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(StationLayout.yaw(facing)));
        pose.translate(-0.5, 0, -0.5);

        renderFirebox(forge, lit, distant, pose, buffers, light);
        float poolY = 10.05F / 16F;
        if (forge.moltenUnits() > 0) {
            float fill = (float) Math.sqrt(Math.min(1.0, forge.moltenUnits() / (double) Math.max(1, ServerConfig.get(ServerConfig.FORGE_CAPACITY_UNITS))));
            poolY = (10.25F + 3.25F * fill) / 16F;
            int tint = RenderUtil.mixColor(0xFFFFFF, forge.familyTint(), 0.18F);
            // The basin behind the divider (x 3-13, z 8-13), above the hearth and below the rim top.
            RenderUtil.horizontalQuad(pose, buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS)), RenderUtil.sprite(MOLTEN),
                    3 / 16F, 8 / 16F, 13 / 16F, 13 / 16F, poolY, 0xFF000000 | tint, LightTexture.FULL_BRIGHT);
        }
        renderDeposits(forge, lit, distant, poolY, pose, buffers, light);
        renderBellows(forge, partialTick, pose, buffers, light);
        if (!distant) {
            renderBookAndPlan(forge, pose, buffers, light);
            renderSupplies(forge, pose, buffers, light);
        }
        ForgeJob job = forge.job();
        if (job != null) renderBillet(forge, job, pose, buffers, light);
        WorkpieceData rest = forge.rest();
        if (rest != null) renderRest(forge, rest, pose, buffers, light);
        pose.popPose();
    }

    private void renderFirebox(SmithsForgeBlockEntity forge, boolean lit, boolean distant, PoseStack pose, MultiBufferSource buffers, int light) {
        if (forge.hasLava()) {
            RenderUtil.horizontalQuad(pose, buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS)),
                    RenderUtil.sprite(LAVA), 4 / 16F, 1 / 16F, 12 / 16F, 12 / 16F, 3 / 16F, 0xFFFFFFFF, LightTexture.FULL_BRIGHT);
            return;
        }
        ItemStack fuel = !forge.fuel().isEmpty() ? forge.fuel() : forge.burningFuel();
        int count = Math.min(distant ? 1 : FUEL.length, fuelStage(forge.fuel().getCount()) + (forge.burningFuel().isEmpty() ? 0 : 1));
        // The fire brightens with the air the bellows drive into it.
        int fuelLight = lit ? RenderUtil.atLeastBlockLight(light, 10 + Math.round(forge.air() / 1000F * 5)) : light;
        for (int i = 0; i < count && !fuel.isEmpty(); i++) {
            if (fuel.is(Items.COAL) || fuel.is(Items.CHARCOAL)) {
                mesh(StationModels.COAL, FUEL[i][0], (2 + (i == 3 ? 2 : 0)) / 16F, FUEL[i][1], FUEL[i][2], pose, buffers, fuelLight, 0xFFFFFF);
            } else {
                renderFlat(fuel, FUEL[i][0], 3 / 16F + i * 0.02F, FUEL[i][1], FUEL[i][2], 0.34F, pose, buffers, fuelLight, forge, i);
            }
        }
    }

    private void renderDeposits(SmithsForgeBlockEntity forge, boolean lit, boolean distant, float poolY, PoseStack pose, MultiBufferSource buffers, int light) {
        List<SmithsForgeBlockEntity.Deposit> deposits = forge.deposits();
        if (deposits.isEmpty()) return;
        int count = Math.min(distant ? 2 : PILE.length, pileStage(forge.depositUnits()));
        float base = Math.max(10.05F / 16F, poolY);
        float heat = lit ? forge.meltProgress() : 0;
        int pileLight = lit ? RenderUtil.atLeastBlockLight(light, 8 + Math.round(heat * 6)) : light;
        String path = forge.family() != null ? forge.family().getPath() : "";
        for (int i = 0; i < count; i++) {
            ItemStack stack = deposits.get(Math.floorMod(deposits.size() - 1 - i, deposits.size())).stack();
            if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.NUGGETS)) {
                int tint = RenderUtil.mixColor(HeatColors.coldColor(path), forge.familyTint(), heat * 0.8F);
                mesh(StationModels.BILLET, PILE[i][0], base + (i / 2) * 2 / 16F, PILE[i][1], PILE[i][2], pose, buffers, pileLight, tint);
            } else {
                renderFlat(stack, PILE[i][0], base + 1 / 16F + i * 0.035F, PILE[i][1], PILE[i][2], 0.3F, pose, buffers, pileLight, forge, i);
            }
        }
    }

    /** The billet on its tray: the shape of a fresh billet, each zone at its own heat. */
    private void renderBillet(SmithsForgeBlockEntity forge, ForgeJob job, PoseStack pose, MultiBufferSource buffers, int light) {
        WorkpieceShape shape = WorkpieceShape.billet(WorkpieceVisuals.get(job.profile.visual()));
        pose.pushPose();
        double offset = (job.position - 1) * 1.5 / 16.0;
        pose.translate(StationLayout.TRAY_CENTER.x + offset, StationLayout.TRAY_CENTER.y, StationLayout.TRAY_CENTER.z);
        float scale = Math.min(1F, 0.46F / Math.max(0.05F, shape.length));
        pose.scale(scale, scale, scale);
        WorkpieceRenderer.render(pose, buffers, shape, job.heat, HeatColors.coldColor(job.family.getPath()), light, null, -1, false);
        pose.popPose();
        boolean focused = FocusController.focusedOn(forge.getBlockPos());
        WorkFocusScreen screen = focused ? WorkFocusScreen.showing() : null;
        WorkTargets.Target target = screen != null ? screen.hoveredTarget() : null;
        if (target != null && (target.kind() == WorkTargets.Kind.BILLET || target.kind() == WorkTargets.Kind.DRAW)) {
            // The smith's tongs at the billet's end, ready to turn or draw it.
            com.otectus.immersivesmithing.client.work.SceneTools.renderTongs(pose, buffers,
                    StationLayout.TRAY_CENTER.x + offset - shape.length * scale / 2F + 0.04,
                    StationLayout.TRAY_CENTER.y + shape.thickness[0] * scale / 2F, StationLayout.TRAY_CENTER.z, light);
        }
    }

    /** A piece reheating on the bar across the mouth of the fire. */
    private void renderRest(SmithsForgeBlockEntity forge, WorkpieceData rest, PoseStack pose, MultiBufferSource buffers, int light) {
        ProcessState p = rest.process();
        WorkpieceShape shape = WorkpieceShape.of(rest);
        pose.pushPose();
        pose.translate(StationLayout.REST_CENTER.x, StationLayout.REST_CENTER.y, StationLayout.REST_CENTER.z + 0.05);
        // Tip into the fire: the piece runs from the mouth toward the back of the firebox.
        pose.mulPose(Axis.YP.rotationDegrees(-90F));
        float scale = Math.min(1F, 0.55F / Math.max(0.05F, shape.length));
        pose.scale(scale, scale, scale);
        pose.translate(shape.length / 2F - 0.08F, 0, 0);
        WorkpieceRenderer.render(pose, buffers, shape, p.heat, HeatColors.coldColor(rest.family().getPath()), light, null, -1, false);
        pose.popPose();
    }

    private void renderBellows(SmithsForgeBlockEntity forge, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float since = forge.clock() - forge.lastPump() + partialTick;
        float press = 0F;
        if (since >= 0 && since < ThermalRules.PUMP_COOLDOWN_TICKS) {
            float half = ThermalRules.PUMP_COOLDOWN_TICKS / 2F;
            press = since < half ? since / half : 1F - (since - half) / half;
            if (ClientConfig.get(ClientConfig.REDUCED_MOTION)) press *= 0.5F;
        }
        pose.pushPose();
        // Hinged at the back of the bellows (z 9, y 5): the front of the board dips as it is pressed.
        pose.translate(0, 5 / 16F, 9 / 16F);
        pose.mulPose(Axis.XP.rotationDegrees(-11F * press));
        pose.translate(0, -5 / 16F, -9 / 16F);
        StationModels.render(StationModels.BELLOWS_BOARD, pose, buffers, light, 0xFFFFFF);
        pose.popPose();
    }

    private void renderBookAndPlan(SmithsForgeBlockEntity forge, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(StationLayout.BOOK_CENTER.x, StationLayout.BOOK_CENTER.y + 0.005, StationLayout.BOOK_CENTER.z);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.mulPose(Axis.ZP.rotationDegrees(90F));
        pose.scale(0.24F, 0.24F, 0.24F);
        itemRenderer.renderStatic(new ItemStack(ModItems.SMITHING_GUIDE.get()), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buffers, forge.getLevel(), 1);
        pose.popPose();
        if (forge.plan() == null || forge.getLevel() == null) return;
        ItemStack planned = forge.getLevel().getRecipeManager().byKey(forge.plan())
                .map(r -> r.getResultItem(forge.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
        if (planned.isEmpty()) return;
        // The pinned drawing: a sheet on the left rim with the piece drawn on it.
        RenderUtil.horizontalQuad(pose, buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS)), RenderUtil.sprite(PAPER),
                0.25F / 16F, 9 / 16F, 2.75F / 16F, 12.5F / 16F, 14.02F / 16F, 0xFFF2E8D0, light);
        pose.pushPose();
        pose.translate(1.5 / 16.0, 14.05 / 16.0, 10.75 / 16.0);
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(0.15F, 0.15F, 0.15F);
        itemRenderer.renderStatic(planned, ItemDisplayContext.GUI, light, OverlayTexture.NO_OVERLAY, pose, buffers, forge.getLevel(), 2);
        pose.popPose();
    }

    /** The reserved components of a committed job, laid out on the right rim. */
    private void renderSupplies(SmithsForgeBlockEntity forge, PoseStack pose, MultiBufferSource buffers, int light) {
        ForgeJob job = forge.job();
        if (job == null || job.sockets.isEmpty()) return;
        for (int i = 0; i < Math.min(3, job.sockets.size()); i++) {
            ItemStack shown = job.sockets.get(i).display();
            if (shown.isEmpty()) continue;
            renderFlat(shown, 14.5F / 16F, 14.05F / 16F, (5 + i * 3) / 16F, 90F, 0.22F, pose, buffers, light, forge, 10 + i);
        }
    }

    private static void mesh(ResourceLocation model, float x, float y, float z, float yaw, PoseStack pose,
                             MultiBufferSource buffers, int light, int color) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-0.5, 0, -0.5);
        StationModels.render(model, pose, buffers, light, color);
        pose.popPose();
    }

    private void renderFlat(ItemStack stack, float x, float y, float z, float yaw, float scale, PoseStack pose,
                            MultiBufferSource buffers, int light, SmithsForgeBlockEntity forge, int seed) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(scale, scale, scale);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, forge.getLevel(),
                (int) forge.getBlockPos().asLong() + seed);
        pose.popPose();
    }

    private static int pileStage(int units) {
        if (units < 9) return 1;
        if (units < 36) return 2;
        if (units < 144) return 3;
        if (units < 324) return 4;
        return 6;
    }

    private static int fuelStage(int count) {
        if (count <= 0) return 0;
        if (count < 8) return 1;
        if (count < 24) return 2;
        return 3;
    }
}
