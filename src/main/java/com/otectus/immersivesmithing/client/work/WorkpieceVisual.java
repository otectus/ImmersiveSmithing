package com.otectus.immersivesmithing.client.work;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * How a kind of workpiece looks as it is worked, from {@code assets/<namespace>/immersive_smithing/workpiece_profiles/<id>.json}.
 * Presentation only: it may decorate the authoritative operation areas but never changes them. The piece is a strip of
 * {@link #SEGMENTS} segments along its length; each segment eases from the billet's size to its target size as the
 * operations over it progress, with optional in-plane curve, vertical lift and a dished cross-section for plates.
 *
 * <pre>
 * { "billet": {"length": 0.42, "width": 0.12, "thickness": 0.07},
 *   "length": 0.92,
 *   "width":     [[0.0, 0.05], [0.24, 0.05], [0.26, 0.12], [0.9, 0.1], [1.0, 0.02]],
 *   "thickness": [[0.0, 0.05], [0.3, 0.025], [1.0, 0.015]],
 *   "curve":     [[0.0, 0.0], [1.0, 0.0]],
 *   "lift":      [[0.0, 0.0], [1.0, 0.0]],
 *   "dish": 0.0,
 *   "sockets": {"haft": [0.06, 0.0]} }
 * </pre>
 * Keyframes are {@code [u, value]} in blocks; values between them are interpolated.
 */
public record WorkpieceVisual(ResourceLocation id, float billetLength, float billetWidth, float billetThickness, float length,
                              float[] width, float[] thickness, float[] curve, float[] lift, float dish, Map<String, float[]> sockets) {
    public static final int SEGMENTS = 12;

    public static WorkpieceVisual fallback(ResourceLocation id) {
        float[] width = new float[SEGMENTS];
        float[] thick = new float[SEGMENTS];
        for (int i = 0; i < SEGMENTS; i++) {
            float u = (i + 0.5F) / SEGMENTS;
            width[i] = u > 0.85F ? 0.07F : 0.11F;
            thick[i] = 0.04F;
        }
        return new WorkpieceVisual(id, 0.42F, 0.12F, 0.07F, 0.72F, width, thick, new float[SEGMENTS], new float[SEGMENTS], 0F, Map.of());
    }

    public static WorkpieceVisual fromJson(ResourceLocation id, JsonObject json) {
        JsonObject billet = GsonHelper.getAsJsonObject(json, "billet", new JsonObject());
        float bl = bounded(GsonHelper.getAsFloat(billet, "length", 0.42F), 0.05F, 1.5F, "billet.length");
        float bw = bounded(GsonHelper.getAsFloat(billet, "width", 0.12F), 0.01F, 1.0F, "billet.width");
        float bt = bounded(GsonHelper.getAsFloat(billet, "thickness", 0.07F), 0.005F, 0.5F, "billet.thickness");
        float length = bounded(GsonHelper.getAsFloat(json, "length", 0.8F), 0.05F, 2.0F, "length");
        float[] width = sample(json, "width", 0.1F, 0.005F, 1.5F);
        float[] thick = sample(json, "thickness", 0.04F, 0.003F, 0.5F);
        float[] curve = sample(json, "curve", 0F, -0.6F, 0.6F);
        float[] lift = sample(json, "lift", 0F, -0.4F, 0.6F);
        float dish = bounded(GsonHelper.getAsFloat(json, "dish", 0F), 0F, 0.3F, "dish");
        Map<String, float[]> sockets = new HashMap<>();
        if (json.has("sockets")) {
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "sockets").entrySet()) {
                JsonArray p = e.getValue().getAsJsonArray();
                if (p.size() != 2) throw new JsonParseException("sockets." + e.getKey() + " must be [u, v]");
                sockets.put(e.getKey(), new float[]{bounded(p.get(0).getAsFloat(), 0F, 1F, "socket u"), bounded(p.get(1).getAsFloat(), -0.5F, 0.5F, "socket v")});
            }
        }
        return new WorkpieceVisual(id, bl, bw, bt, length, width, thick, curve, lift, dish, Map.copyOf(sockets));
    }

    private static float bounded(float value, float min, float max, String field) {
        if (!Float.isFinite(value) || value < min || value > max) throw new JsonParseException(field + " must be from " + min + " to " + max);
        return value;
    }

    /** Samples keyframes at the segment centres. */
    private static float[] sample(JsonObject json, String key, float fallback, float min, float max) {
        float[] out = new float[SEGMENTS];
        java.util.Arrays.fill(out, fallback);
        if (!json.has(key)) return out;
        JsonArray frames = GsonHelper.getAsJsonArray(json, key);
        if (frames.isEmpty() || frames.size() > 32) throw new JsonParseException(key + " needs 1 to 32 keyframes");
        float[] us = new float[frames.size()];
        float[] vs = new float[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            JsonArray f = frames.get(i).getAsJsonArray();
            if (f.size() != 2) throw new JsonParseException(key + "[" + i + "] must be [u, value]");
            us[i] = bounded(f.get(0).getAsFloat(), 0F, 1F, key + "[" + i + "].u");
            vs[i] = bounded(f.get(1).getAsFloat(), min, max, key + "[" + i + "].value");
            if (i > 0 && us[i] < us[i - 1]) throw new JsonParseException(key + " keyframes must be in increasing u");
        }
        for (int s = 0; s < SEGMENTS; s++) {
            float u = (s + 0.5F) / SEGMENTS;
            out[s] = interpolate(us, vs, u);
        }
        return out;
    }

    private static float interpolate(float[] us, float[] vs, float u) {
        if (u <= us[0]) return vs[0];
        for (int i = 1; i < us.length; i++) {
            if (u <= us[i]) {
                float span = us[i] - us[i - 1];
                float t = span <= 0 ? 1F : (u - us[i - 1]) / span;
                return vs[i - 1] + (vs[i] - vs[i - 1]) * t;
            }
        }
        return vs[vs.length - 1];
    }

    /** Where a named socket sits on the piece; components without a named socket spread along the first fifth. */
    public float[] socket(String name, int index) {
        float[] p = sockets.get(name);
        return p != null ? p : new float[]{0.06F + 0.05F * index, 0F};
    }
}
