package com.otectus.immersivesmithing.client.work;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.client.renderer.RenderUtil;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.ThermalRules;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Draws a workpiece from its {@link WorkpieceShape}: one closed segment with four joined facets across, so plates show
 * their dish), coloured by the heat of the thermal zone under it and self-lit only while it glows. Optional chalk guides
 * outline the operations still to do on the visible face (the part under the cursor brighter), marks and scale show as
 * dark specks. Everything is drawn in the piece's own frame; the caller places and turns it. Bounded: at most a few hundred
 * quads, no allocation of meshes per frame beyond small arrays.
 */
public final class WorkpieceRenderer {
    private static final ResourceLocation METAL = ImmersiveSmithing.id("block/billet");
    private static final ResourceLocation CHALK = new ResourceLocation("block/white_concrete");

    /**
     * @param heat     per-zone heat (0..10000)
     * @param state    the process, for guides, marks and scale; null for a bare billet
     * @param hoverOp  the operation under the cursor (-1 for none)
     * @param guides   whether to draw chalk guides
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, WorkpieceShape shape, int[] heat, int coldColor, int worldLight,
                              @Nullable ProcessState state, int hoverOp, boolean guides) {
        TextureAtlasSprite sprite = RenderUtil.sprite(METAL);
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int segments = WorkpieceVisual.SEGMENTS;
        for (int s = 0; s < segments; s++) {
            float u = (s + 0.5F) / segments;
            int zone = ThermalRules.zoneAt(u);
            float h = ThermalProfile.fraction(zone < heat.length ? heat[zone] : 0);
            int color = HeatColors.color(h, coldColor);
            int light = HeatColors.light(h, worldLight);
            float x0 = shape.x0[s], x1 = shape.x0[s + 1];
            float w = shape.width[s];
            float zc = shape.curve[s];
            float y0 = shape.lift[s];
            float y1 = y0 + shape.thickness[s];
            float z0 = zc - w / 2F, z1 = zc + w / 2F;
            // Sides. Bottom and end caps follow the same dished cross-section as the top.
            quad(solid, m, n, sprite, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, color, light);
            quad(solid, m, n, sprite, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, color, light);
            boolean capLeft = s == 0 || different(shape, s, s - 1);
            boolean capRight = s == segments - 1 || different(shape, s, s + 1);
            // Joined facets follow the same dish as picking and chalk. A lowered, disconnected centre strip left
            // open cracks in plates and made guides float over the actual surface.
            int top = brighter(color);
            for (int strip = 0; strip < 4; strip++) {
                float va = -0.5F + strip / 4F, vb = va + 0.25F;
                float za = zc + w * va, zb = zc + w * vb;
                float ya = y1 - shape.dishAt(va), yb = y1 - shape.dishAt(vb);
                float slope = (ya - yb) / Math.max(0.001F, zb - za);
                float normalY = 1F / (float) Math.sqrt(1F + slope * slope);
                quad(solid, m, n, sprite, x0, ya, za, x0, yb, zb, x1, yb, zb, x1, ya, za,
                        0, normalY, slope * normalY, top, light);
                float ba = ya - shape.thickness[s], bb = yb - shape.thickness[s];
                quad(solid, m, n, sprite, x0, ba, za, x1, ba, za, x1, bb, zb, x0, bb, zb,
                        0, -normalY, -slope * normalY, darker(color), light);
                if (capLeft) quad(solid, m, n, sprite, x0, bb, zb, x0, yb, zb, x0, ya, za, x0, ba, za, -1, 0, 0, color, light);
                if (capRight) quad(solid, m, n, sprite, x1, ba, za, x1, ya, za, x1, yb, zb, x1, bb, zb, 1, 0, 0, color, light);
            }
        }
        if (state == null) return;
        boolean highContrast = ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES);
        boolean large = ClientConfig.get(ClientConfig.LARGE_MINIGAME_TARGETS);
        VertexConsumer marks = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        TextureAtlasSprite chalk = RenderUtil.sprite(CHALK);
        int full = HeatColors.fullBright();
        // Scale flakes and marks: dark, never the only cue (the status names them too).
        int flakes = Math.min(12, state.scale / 80);
        for (int i = 0; i < flakes; i++) {
            float fu = hash(state.id.hashCode(), i, 1), fv = hash(state.id.hashCode(), i, 2) - 0.5F;
            speck(marks, m, n, chalk, shape, fu, fv * 0.8F, 0.022F, 0xB0201A18, full);
        }
        var ops = state.profile.operations();
        for (int op = 0; op < ops.size(); op++) {
            int count = Math.min(5, state.marks[op] / 180);
            ProcessProfile.Operation o = ops.get(op);
            for (int i = 0; i < count; i++) {
                float mu = o.u0() + (o.u1() - o.u0()) * hash(op, i, 3);
                float mv = o.v0() + (o.v1() - o.v0()) * hash(op, i, 4);
                speck(marks, m, n, chalk, shape, mu, state.flipped ? -mv : mv, 0.03F, 0xC0302018, full);
            }
        }
        if (!guides) return;
        int next = ShapingRules.nextOperation(state);
        for (int op = 0; op < ops.size(); op++) {
            ProcessProfile.Operation o = ops.get(op);
            if (!o.face().matches(state.flipped)) continue;
            boolean done = state.complete(op);
            boolean hovered = op == hoverOp;
            if (done && !hovered) continue;
            int alpha = hovered ? 0xF0 : op == next ? 0xC0 : 0x70;
            int rgb = hovered ? (highContrast ? 0xFFFF40 : 0xFFF0A0) : highContrast ? 0xFFFFFF : 0xE8E8E0;
            float thickness = (large ? 0.018F : 0.011F) * (hovered ? 1.6F : 1F);
            outline(marks, m, n, chalk, shape, o.u0(), o.u1(), o.v0(), o.v1(), state.flipped, thickness, (alpha << 24) | rgb, full);
        }
    }

    private static boolean different(WorkpieceShape shape, int a, int b) {
        return Math.abs(shape.width[a] - shape.width[b]) > 0.0001F
                || Math.abs(shape.thickness[a] - shape.thickness[b]) > 0.0001F
                || Math.abs(shape.lift[a] - shape.lift[b]) > 0.0001F
                || Math.abs(shape.curve[a] - shape.curve[b]) > 0.0001F;
    }

    /** The guide outline of an area, following the piece's curve. */
    private static void outline(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sprite, WorkpieceShape shape,
                                float u0, float u1, float v0, float v1, boolean flipped, float t, int argb, int light) {
        float a0 = flipped ? -v1 : v0, a1 = flipped ? -v0 : v1;
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            float ua = u0 + (u1 - u0) * i / steps, ub = u0 + (u1 - u0) * (i + 1) / steps;
            line(vc, m, n, sprite, shape.point(ua, a0 + 0.04F), shape.point(ub, a0 + 0.04F), t, argb, light);
            line(vc, m, n, sprite, shape.point(ua, a1 - 0.04F), shape.point(ub, a1 - 0.04F), t, argb, light);
        }
        line(vc, m, n, sprite, shape.point(u0 + 0.01F, a0), shape.point(u0 + 0.01F, a1), t, argb, light);
        line(vc, m, n, sprite, shape.point(u1 - 0.01F, a0), shape.point(u1 - 0.01F, a1), t, argb, light);
    }

    /** A thin quad on the surface from a to b. */
    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sprite, Vec3 a, Vec3 b, float t, int argb, int light) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;
        float px = (float) (-dz / len * t / 2), pz = (float) (dx / len * t / 2);
        float ay = (float) a.y + 0.003F, by = (float) b.y + 0.003F;
        quad(vc, m, n, sprite, (float) a.x - px, ay, (float) a.z - pz, (float) a.x + px, ay, (float) a.z + pz,
                (float) b.x + px, by, (float) b.z + pz, (float) b.x - px, by, (float) b.z - pz, 0, 1, 0, argb, light);
    }

    private static void speck(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sprite, WorkpieceShape shape,
                              float u, float v, float size, int argb, int light) {
        Vec3 c = shape.point(Math.max(0F, Math.min(1F, u)), Math.max(-0.45F, Math.min(0.45F, v)));
        float s = size / 2F, y = (float) c.y + 0.003F;
        float x = (float) c.x, z = (float) c.z;
        quad(vc, m, n, sprite, x - s, y, z - s, x - s, y, z + s, x + s, y, z + s, x + s, y, z - s, 0, 1, 0, argb, light);
    }

    /** One quad, corners in counter-clockwise order seen from outside; colour may carry alpha (else opaque). */
    static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sprite,
                     float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                     float nx, float ny, float nz, int color, int light) {
        int a = (color >>> 24) == 0 ? 255 : color >>> 24;
        int r = (color >> 16) & 255, g = (color >> 8) & 255, bl = color & 255;
        vertex(vc, m, n, sprite, ax, ay, az, nx, ny, nz, r, g, bl, a, light);
        vertex(vc, m, n, sprite, bx, by, bz, nx, ny, nz, r, g, bl, a, light);
        vertex(vc, m, n, sprite, cx, cy, cz, nx, ny, nz, r, g, bl, a, light);
        vertex(vc, m, n, sprite, dx, dy, dz, nx, ny, nz, r, g, bl, a, light);
    }

    /** Object-space UVs keep the grain continuous at the station's 16 texels/block. Clamp oversized resource-pack
     * profiles to this sprite, never to neighbouring sprites in the atlas. */
    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sprite,
                               float x, float y, float z, float nx, float ny, float nz, int r, int g, int b, int a, int light) {
        boolean top = ny != 0;
        float u = 8F + 16F * (nx != 0 ? z : x);
        float v = top ? 8F + 16F * z : 8F - 16F * y;
        vc.vertex(m, x, y, z).color(r, g, b, a)
                .uv(sprite.getU(net.minecraft.util.Mth.clamp(u, 0F, 16F)), sprite.getV(net.minecraft.util.Mth.clamp(v, 0F, 16F)))
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    private static int darker(int color) {
        return RenderUtil.mixColor(color, 0x000000, 0.35F);
    }

    private static int brighter(int color) {
        return RenderUtil.mixColor(color, 0xFFFFFF, 0.08F);
    }

    /** Deterministic 0..1 positions, so marks stay put frame to frame and for every observer. */
    private static float hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    private WorkpieceRenderer() {}
}
