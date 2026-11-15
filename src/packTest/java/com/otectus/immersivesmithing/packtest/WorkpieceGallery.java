package com.otectus.immersivesmithing.packtest;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.client.work.WorkpieceRenderer;
import com.otectus.immersivesmithing.client.work.WorkpieceShape;
import com.otectus.immersivesmithing.client.work.WorkpieceVisuals;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Test-only contact sheet of every shipped workpiece silhouette, using the production renderer and resource profiles. */
final class WorkpieceGallery extends Screen {
    private static final List<String> PROFILES = List.of("straight_blade", "short_blade", "curved_blade", "chopping_head",
            "pick_head", "digging_plate", "hammer_head", "polearm_head", "armor_plate", "shield_hardware",
            "curved_thrown", "ranged_hardware", "generic");

    WorkpieceGallery() {
        super(Component.literal("Workpiece visual review"));
    }

    private static ProcessState state(String name, float progress) {
        var id = ImmersiveSmithing.id(name);
        var op = new ProcessProfile.Operation("review", "review", ProcessProfile.Kind.SHAPE, 100, 1,
                ProcessProfile.Face.ANY, 0, 1, -0.5F, 0.5F, List.of());
        var profile = new ProcessProfile(id, id, null, List.of(op), 100, 500, List.of(), List.of());
        var state = ProcessState.fresh(profile, ThermalProfile.DEFAULT, 1000, List.of());
        state.work[0] = Math.round(state.required(0) * progress);
        return state;
    }

    /** Surface regression: deep dishes retain thickness above the anvil, and picking follows each drawn facet. */
    static boolean surfacesAgree() {
        for (String name : PROFILES) {
            var id = ImmersiveSmithing.id(name);
            if (!WorkpieceVisuals.has(id)) return false;
            for (float progress : new float[]{0, 0.5F, 1}) {
                WorkpieceShape shape = WorkpieceShape.of(WorkpieceVisuals.get(id), state(name, progress));
                for (int s = 0; s < 12; s++) {
                    float u = (s + 0.5F) / 12F;
                    for (int strip = 0; strip < 4; strip++) {
                        float a = -0.5F + strip / 4F, b = a + 0.25F, middle = (a + b) / 2F;
                        double top = shape.point(u, middle).y;
                        if (top - shape.thickness[s] < -0.00001) return false;
                        double interpolated = (shape.point(u, a).y + shape.point(u, b).y) / 2;
                        if (Math.abs(top - interpolated) > 0.00001) return false;
                        var point = shape.point(u, middle);
                        float[] picked = shape.toCanonical(point.x, point.z);
                        if (picked == null || Math.abs(picked[0] - u) > 0.0001 || Math.abs(picked[1] - middle) > 0.0001) return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF25282D);
        g.drawString(font, "Workpiece profiles: cold finished / hot half-shaped", 12, 10, 0xFFE0E0E0, false);
        float cellWidth = (width - 24) / 4F, cellHeight = (height - 36) / 4F;
        for (int i = 0; i < PROFILES.size(); i++) {
            String name = PROFILES.get(i);
            float x = 12 + i % 4 * cellWidth, y = 28 + i / 4 * cellHeight;
            g.fill((int) x, (int) y, (int) (x + cellWidth - 4), (int) (y + cellHeight - 4), 0xFF34383F);
            g.drawString(font, name, (int) x + 4, (int) y + 3, 0xFFC5CBD1, false);
            g.flush();
            for (int row = 0; row < 2; row++) {
                var state = state(name, row == 0 ? 1 : 0.5F);
                var shape = WorkpieceShape.of(WorkpieceVisuals.get(state.profile.visual()), state);
                var pose = g.pose();
                pose.pushPose();
                pose.translate(x + cellWidth / 2, y + 25 + row * (cellHeight - 20) / 2, 160);
                float scale = Math.min(cellWidth * 0.75F, cellHeight * 0.8F);
                pose.scale(scale, -scale, scale);
                pose.mulPose(Axis.XP.rotationDegrees(55));
                Lighting.setupForFlatItems();
                int[] heat = row == 0 ? new int[]{0, 0, 0} : new int[]{5800, 6400, 7000};
                WorkpieceRenderer.render(pose, minecraft.renderBuffers().bufferSource(), shape, heat, 0xB4B8BC,
                        LightTexture.FULL_BRIGHT, null, -1, false);
                minecraft.renderBuffers().bufferSource().endBatch();
                pose.popPose();
                Lighting.setupFor3DItems();
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
