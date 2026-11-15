package com.otectus.immersivesmithing.packtest;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Test-only contact sheet that exercises the production tongs BEWLR, baked item transforms and loaded NBT path. */
final class TongsGallery extends Screen {
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron");
    private static final List<ProfileCase> PROFILES = List.of(
            new ProfileCase("straight_blade", Items.IRON_SWORD),
            new ProfileCase("short_blade", Items.SHEARS),
            new ProfileCase("curved_blade", Items.GOLDEN_SWORD),
            new ProfileCase("chopping_head", Items.IRON_AXE),
            new ProfileCase("pick_head", Items.IRON_PICKAXE),
            new ProfileCase("digging_plate", Items.IRON_SHOVEL),
            new ProfileCase("hammer_head", Items.IRON_HOE),
            new ProfileCase("polearm_head", Items.TRIDENT),
            new ProfileCase("armor_plate", Items.IRON_CHESTPLATE),
            new ProfileCase("shield_hardware", Items.SHIELD),
            new ProfileCase("curved_thrown", Items.SHEARS),
            new ProfileCase("ranged_hardware", Items.CROSSBOW),
            new ProfileCase("generic", Items.IRON_INGOT));

    private static final List<ItemDisplayContext> POSES = List.of(
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

    private final List<ProfilePair> profileStacks = new ArrayList<>();
    private final List<ItemStack> tierStacks = new ArrayList<>();
    private final ItemStack poseStack;

    TongsGallery() {
        super(Component.literal("Tongs jaw alignment review"));
        for (ProfileCase profile : PROFILES) {
            profileStacks.add(new ProfilePair(profile.name, loaded(SmithingTier.IRON, profile, false),
                    loaded(SmithingTier.IRON, profile, true)));
        }
        ProfileCase sword = PROFILES.get(0);
        for (SmithingTier tier : SmithingTier.values()) tierStacks.add(loaded(tier, sword, true));
        poseStack = loaded(SmithingTier.IRON, PROFILES.get(3), true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF22262B);
        g.drawString(font, "Loaded tongs: hot raw / shaped silhouettes at the metal jaw anchor", 12, 8,
                0xFFE6E8EA, false);

        int columns = 5;
        float top = 24F, profileBottom = height - 190F;
        float cellWidth = (width - 24F) / columns;
        float cellHeight = (profileBottom - top) / 3F;
        for (int i = 0; i < profileStacks.size(); i++) {
            ProfilePair pair = profileStacks.get(i);
            float x = 12F + i % columns * cellWidth;
            float y = top + i / columns * cellHeight;
            g.fill((int) x, (int) y, (int) (x + cellWidth - 4), (int) (y + cellHeight - 4), 0xFF343A42);
            g.drawString(font, pair.name, (int) x + 4, (int) y + 4, 0xFFC9D0D6, false);
            g.drawString(font, "raw", (int) (x + cellWidth * 0.18F), (int) y + 17, 0xFFF09A56, false);
            g.drawString(font, "shaped", (int) (x + cellWidth * 0.59F), (int) y + 17, 0xFFB9C3CC, false);
            float size = Math.min(cellWidth * 0.39F, (cellHeight - 34F) * 0.72F);
            float cy = y + 32F + (cellHeight - 36F) / 2F;
            renderTongs(g, pair.raw, ItemDisplayContext.GUI, x + cellWidth * 0.27F, cy, size, i * 2);
            renderTongs(g, pair.shaped, ItemDisplayContext.GUI, x + cellWidth * 0.72F, cy, size, i * 2 + 1);
        }

        float tierTop = height - 178F;
        g.drawString(font, "Tier silhouettes (shaped straight blade)", 12, (int) tierTop, 0xFFE6E8EA, false);
        for (int i = 0; i < tierStacks.size(); i++) {
            float x = 80F + i * (width - 160F) / 3F;
            g.drawCenteredString(font, SmithingTier.values()[i].name().toLowerCase(), (int) x, (int) tierTop + 13,
                    0xFFC9D0D6);
            renderTongs(g, tierStacks.get(i), ItemDisplayContext.GUI, x, tierTop + 52F, 53F, 100 + i);
        }

        float poseTop = height - 98F;
        g.drawString(font, "Mirrored held transforms (shaped chopping head; contact remains in the jaws)", 12,
                (int) poseTop, 0xFFE6E8EA, false);
        String[] labels = {"1P right", "1P left", "3P right", "3P left"};
        for (int i = 0; i < POSES.size(); i++) {
            float x = 105F + i * (width - 210F) / 3F;
            g.drawCenteredString(font, labels[i], (int) x, (int) poseTop + 13, 0xFFC9D0D6);
            renderTongs(g, poseStack, POSES.get(i), x, poseTop + 58F, 58F, 200 + i);
        }
        Lighting.setupFor3DItems();
    }

    private void renderTongs(GuiGraphics g, ItemStack stack, ItemDisplayContext context, float x, float y, float size,
                             int seed) {
        g.flush();
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 180F);
        pose.scale(size, -size, size);
        if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) pose.mulPose(Axis.YP.rotationDegrees(90F));
        if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) pose.mulPose(Axis.YP.rotationDegrees(-90F));
        boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        Lighting.setupFor3DItems();
        minecraft.getItemRenderer().renderStatic(null, stack, context, left, pose, g.bufferSource(), minecraft.level,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, seed);
        g.flush();
        pose.popPose();
    }

    private static ItemStack loaded(SmithingTier tier, ProfileCase profileCase, boolean shaped) {
        var id = ImmersiveSmithing.id(profileCase.name);
        var operation = new ProcessProfile.Operation("review", "review", ProcessProfile.Kind.SHAPE, 100, 1,
                ProcessProfile.Face.ANY, 0F, 1F, -0.5F, 0.5F, List.of());
        var profile = new ProcessProfile(id, id, null, List.of(operation), 100, 500, List.of(), List.of());
        ProcessState process = ProcessState.fresh(profile, ThermalProfile.DEFAULT, 1000, List.of());
        process.heat = shaped ? new int[]{4600, 5800, 6900} : new int[]{5400, 6500, 7600};
        if (shaped) {
            process.work[0] = process.required(0);
            process.stage = WorkStage.SHAPED;
        }
        WorkpieceData piece = new WorkpieceData(new ItemStack(profileCase.target), ImmersiveSmithing.id("packtest/tongs_gallery"),
                IRON, 18, -1, -1, false, id, 18, process);
        ItemStack tongs = new ItemStack(ModItems.tongs(tier));
        WorkpieceCodec.setHeld(tongs, piece);
        return tongs;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ProfileCase(String name, Item target) {}
    private record ProfilePair(String name, ItemStack raw, ItemStack shaped) {}
}
