package com.otectus.immersivesmithing.client.work;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.client.renderer.RenderUtil;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.ThermalRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** A flat blade's real silhouette, shared by its rough blank and its finished item placement. */
public final class BladeWorkpieceModel {
    private static final float DIAGONAL = (float) Math.sqrt(0.5);
    private static final Map<BakedModel, BladeWorkpieceModel> CACHE = new IdentityHashMap<>();
    private static int generation = -1;
    private static final int MAX_PIXELS = 32;
    private final TextureAtlasSprite sprite;
    private final Pixel[] pixels;
    private final float start, end, across, bottom, depth;
    private final Map<WorkpieceVisual, WorkpieceVisual> profiles = new IdentityHashMap<>();

    private record Pixel(float x0, float y0, float x1, float y1, int edges) {}

    private BladeWorkpieceModel(TextureAtlasSprite sprite, Pixel[] pixels, float start, float end, float across,
                                float bottom, float depth) {
        this.sprite = sprite;
        this.pixels = pixels;
        this.start = start;
        this.end = end;
        this.across = across;
        this.bottom = bottom;
        this.depth = depth;
    }

    /** Custom, layered and three-dimensional models keep their authored rendering path. Cache dies on resource reload. */
    @Nullable
    public static BladeWorkpieceModel get(ItemStack target, WorkpieceVisual visual) {
        if (!visual.id().getPath().endsWith("_blade") || target.isEmpty()) return null;
        if (generation != WorkpieceVisuals.generation()) {
            CACHE.clear();
            generation = WorkpieceVisuals.generation();
        }
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(target, Minecraft.getInstance().level, null, 0);
        if (!CACHE.containsKey(model)) {
            if (CACHE.size() >= 256) CACHE.clear();
            CACHE.put(model, bake(model));
        }
        return CACHE.get(model);
    }

    @Nullable
    private static BladeWorkpieceModel bake(BakedModel model) {
        if (model.isCustomRenderer() || model.isGui3d()) return null;
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, RandomSource.create(42)));
        for (Direction face : Direction.values()) quads.addAll(model.getQuads(null, face, RandomSource.create(42)));
        TextureAtlasSprite sprite = null;
        float bottom = Float.POSITIVE_INFINITY, top = Float.NEGATIVE_INFINITY;
        boolean front = false;
        for (BakedQuad quad : quads) {
            if (sprite != null && sprite != quad.getSprite()) return null;
            sprite = quad.getSprite();
            front |= quad.getDirection() == Direction.SOUTH;
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int v = 0; v < 4; v++) {
                float x = Float.intBitsToFloat(vertices[v * stride]);
                float y = Float.intBitsToFloat(vertices[v * stride + 1]);
                float z = Float.intBitsToFloat(vertices[v * stride + 2]) - 0.5F;
                if (x < -0.001F || x > 1.001F || y < -0.001F || y > 1.001F) return null;
                bottom = Math.min(bottom, z);
                top = Math.max(top, z);
            }
        }
        if (!front || sprite == null || top - bottom > 0.126F) return null;
        int w = sprite.contents().width(), h = sprite.contents().height();
        // Keep the exact alpha outline; high-resolution/custom packs use the profile mesh rather than resampling it.
        if (w > MAX_PIXELS || h > MAX_PIXELS || sprite.contents().getUniqueFrames().count() != 1) return null;
        boolean[][] solid = new boolean[h][w];
        int frame = sprite.contents().getUniqueFrames().findFirst().orElse(0);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) solid[y][x] = !sprite.contents().isTransparent(frame, x, y);
        List<Pixel> pixels = new ArrayList<>();
        float start = Float.POSITIVE_INFINITY, end = Float.NEGATIVE_INFINITY;
        float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!solid[y][x]) continue;
            float x0 = x / (float) w - 0.5F, x1 = (x + 1F) / w - 0.5F;
            float y0 = 0.5F - (y + 1F) / h, y1 = 0.5F - y / (float) h;
            start = Math.min(start, (x0 + y0) * DIAGONAL);
            end = Math.max(end, (x1 + y1) * DIAGONAL);
            low = Math.min(low, (y0 - x1) * DIAGONAL);
            high = Math.max(high, (y1 - x0) * DIAGONAL);
            int edges = (x == 0 || !solid[y][x - 1] ? 1 : 0) | (x == w - 1 || !solid[y][x + 1] ? 2 : 0)
                    | (y == h - 1 || !solid[y + 1][x] ? 4 : 0) | (y == 0 || !solid[y - 1][x] ? 8 : 0);
            pixels.add(new Pixel(x0, y0, x1, y1, edges));
        }
        return pixels.isEmpty() ? null : new BladeWorkpieceModel(sprite, pixels.toArray(Pixel[]::new), start, end,
                (low + high) / 2F, bottom, top - bottom);
    }

    /** Sampling the same silhouette also makes the hammer targets and the growing blank agree with the eventual blade. */
    public WorkpieceVisual profile(WorkpieceVisual source) {
        WorkpieceVisual cached = profiles.get(source);
        if (cached != null) return cached;
        float[] width = new float[WorkpieceVisual.SEGMENTS], curve = new float[WorkpieceVisual.SEGMENTS];
        float[] thickness = new float[WorkpieceVisual.SEGMENTS];
        float scale = source.length() / (end - start);
        for (int s = 0; s < width.length; s++) {
            float a = start + (end - start) * s / width.length, b = start + (end - start) * (s + 1F) / width.length;
            float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
            for (Pixel p : pixels) {
                if ((p.x1 + p.y1) * DIAGONAL < a || (p.x0 + p.y0) * DIAGONAL > b) continue;
                low = Math.min(low, (p.y0 - p.x1) * DIAGONAL);
                high = Math.max(high, (p.y1 - p.x0) * DIAGONAL);
            }
            width[s] = Float.isFinite(low) ? Math.max(0.005F, (high - low) * scale) : source.width()[s];
            curve[s] = Float.isFinite(low) ? (across - (low + high) / 2F) * scale : source.curve()[s];
            thickness[s] = depth * scale;
        }
        WorkpieceVisual profile = new WorkpieceVisual(source.id(), source.billetLength(), source.billetWidth(), source.billetThickness(),
                source.length(), width, thickness, curve, source.lift(), 0F, source.sockets());
        profiles.put(source, profile);
        return profile;
    }

    /** Apply inside the piece's frame (length +x, tip forward). NONE bypasses item-frame display transforms. */
    public void pose(PoseStack pose, float length, boolean flipped) {
        float scale = length / (end - start);
        if (flipped) {
            pose.translate(0, depth * scale, 0);
            pose.mulPose(Axis.XP.rotationDegrees(180));
        }
        pose.translate(-(start + end) / 2F * scale, -bottom * scale, across * scale);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.ZP.rotationDegrees(-45));
        pose.scale(scale, scale, scale);
    }

    /** Once shaped, show the actual blade outline in unfinished metal, with fitted grip/guard material in its final place. */
    public void render(PoseStack pose, MultiBufferSource buffers, WorkpieceVisual visual, ProcessState state, int[] heat,
                       int coldColor, int light) {
        pose.pushPose();
        pose(pose, visual.length(), state.flipped);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        TextureAtlasSprite metal = RenderUtil.sprite(ImmersiveSmithing.id("block/billet"));
        for (Pixel p : pixels) {
            float u = (((p.x0 + p.x1 + p.y0 + p.y1) / 2F) * DIAGONAL - start) / (end - start);
            int zone = ThermalRules.zoneAt(u);
            float h = ThermalProfile.fraction(zone < heat.length ? heat[zone] : 0);
            boolean fitted = fitted(state, u);
            int color = fitted ? 0xFFFFFF : HeatColors.color(h, coldColor);
            int emitted = fitted ? light : HeatColors.light(h, light);
            TextureAtlasSprite texture = fitted ? sprite : metal;
            face(vc, pose, texture, p.x0, p.y0, p.x1, p.y1, bottom + depth, color, emitted, 1);
            face(vc, pose, texture, p.x0, p.y0, p.x1, p.y1, bottom, RenderUtil.mixColor(color, 0, 0.2F), emitted, -1);
            if ((p.edges & 1) != 0) side(vc, pose, metal, p.x0, p.y0, p.x0, p.y1, color, emitted, -1, 0);
            if ((p.edges & 2) != 0) side(vc, pose, metal, p.x1, p.y1, p.x1, p.y0, color, emitted, 1, 0);
            if ((p.edges & 4) != 0) side(vc, pose, metal, p.x1, p.y0, p.x0, p.y0, color, emitted, 0, -1);
            if ((p.edges & 8) != 0) side(vc, pose, metal, p.x0, p.y1, p.x1, p.y1, color, emitted, 0, 1);
        }
        pose.popPose();
    }

    private static boolean fitted(ProcessState state, float u) {
        for (ProcessState.Socket socket : state.sockets) {
            if (!socket.attached()) continue;
            if ((socket.name().equals("grip") || socket.name().equals("haft") || socket.name().equals("strap")) && u < 0.3F) return true;
            if (socket.name().equals("guard") && u >= 0.2F && u < 0.4F) return true;
        }
        return false;
    }

    private static void face(VertexConsumer vc, PoseStack pose, TextureAtlasSprite texture, float x0, float y0, float x1,
                             float y1, float z, int color, int light, float nz) {
        vertex(vc, pose, texture, x0, y0, z, color, light, 0, 0, nz);
        vertex(vc, pose, texture, x1, y0, z, color, light, 0, 0, nz);
        vertex(vc, pose, texture, x1, y1, z, color, light, 0, 0, nz);
        vertex(vc, pose, texture, x0, y1, z, color, light, 0, 0, nz);
    }

    private void side(VertexConsumer vc, PoseStack pose, TextureAtlasSprite texture, float ax, float ay, float bx, float by,
                      int color, int light, float nx, float ny) {
        vertex(vc, pose, texture, ax, ay, bottom, color, light, nx, ny, 0);
        vertex(vc, pose, texture, bx, by, bottom, color, light, nx, ny, 0);
        vertex(vc, pose, texture, bx, by, bottom + depth, color, light, nx, ny, 0);
        vertex(vc, pose, texture, ax, ay, bottom + depth, color, light, nx, ny, 0);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, TextureAtlasSprite texture, float x, float y, float z,
                               int color, int light, float nx, float ny, float nz) {
        float u = (x + 0.5F) * 16F, v = (0.5F - y) * 16F;
        vc.vertex(pose.last().pose(), x, y, z).color((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
                .uv(texture.getU(u), texture.getV(v)).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.last().normal(), nx, ny, nz).endVertex();
    }
}
