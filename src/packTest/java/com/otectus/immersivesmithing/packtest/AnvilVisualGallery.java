package com.otectus.immersivesmithing.packtest;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.block.SmithsAnvilBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.client.work.BladeWorkpieceModel;
import com.otectus.immersivesmithing.client.work.WorkpieceVisuals;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.work.ProcessResolver;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Real anvil block and block-entity rendering before/after completion, in every blockstate facing. */
final class AnvilVisualGallery extends Screen {
    private static final Item[] SWORDS = {Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private final SmithsAnvilBlockEntity[][] anvils = new SmithsAnvilBlockEntity[4][2];

    AnvilVisualGallery() {
        super(Component.literal("Anvil alignment review"));
        for (int row = 0; row < 4; row++) for (int col = 0; col < 2; col++) {
            ItemStack target = new ItemStack(SWORDS[row]);
            var profile = ProcessResolver.forLegacy(ImmersiveSmithing.id("sword"), target);
            var process = ProcessState.fresh(profile, ThermalProfile.DEFAULT, 1000,
                    List.of(new ProcessState.Socket("grip", List.of(new ItemStack(Items.STICK)), false)));
            for (int op = 0; op < process.work.length; op++) process.work[op] = process.required(op);
            process.stage = WorkStage.COOLED;
            process.flipped = row % 2 == 1;
            var state = ModBlocks.SMITHS_ANVIL.get().defaultBlockState().setValue(SmithsAnvilBlock.FACING, FACINGS[row]);
            var anvil = new SmithsAnvilBlockEntity(new BlockPos(300 + row, 64, 300 + col), state);
            if (col == 0) anvil.setWorkpiece(new WorkpieceData(target, ImmersiveSmithing.id("smithing/iron_sword"),
                    new net.minecraft.resources.ResourceLocation("minecraft:iron"), 18, 80, 80, false,
                    ImmersiveSmithing.id("sword"), 18, process));
            else anvil.setOutput(target, profile.visual(), process.flipped);
            anvils[row][col] = anvil;
        }
    }

    static boolean silhouettesAvailable() {
        var visual = WorkpieceVisuals.get(ImmersiveSmithing.id("straight_blade"));
        for (Item item : SWORDS) {
            var model = BladeWorkpieceModel.get(new ItemStack(item), visual);
            if (model == null) return false;
            var profile = model.profile(visual);
            if (profile.length() != visual.length()) return false;
            for (float width : profile.width()) if (!Float.isFinite(width) || width <= 0) return false;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF25282D);
        g.drawString(font, "Shaped blank + aligned grip     /     Finished item in the same work frame", 12, 9, 0xFFE0E0E0, false);
        float cellWidth = (width - 24) / 2F, cellHeight = (height - 30) / 4F;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 2; col++) {
            float x = 12 + col * cellWidth, y = 25 + row * cellHeight;
            g.fill((int) x, (int) y, (int) (x + cellWidth - 4), (int) (y + cellHeight - 4), 0xFF34383F);
            g.drawString(font, FACINGS[row].getName() + (row % 2 == 1 ? " / flipped" : ""), (int) x + 4, (int) y + 3, 0xFFC5CBD1, false);
            g.flush();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(x + cellWidth / 2, y + cellHeight - 15, 180);
            float scale = cellHeight * 0.67F;
            pose.scale(scale, -scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(35));
            pose.mulPose(Axis.YP.rotationDegrees(30));
            pose.translate(-0.5, 0, -0.5);
            Lighting.setupForFlatItems();
            var buffers = minecraft.renderBuffers().bufferSource();
            var anvil = anvils[row][col];
            minecraft.getBlockRenderer().renderSingleBlock(anvil.getBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            minecraft.getBlockEntityRenderDispatcher().renderItem(anvil, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            buffers.endBatch();
            pose.popPose();
            Lighting.setupFor3DItems();
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
