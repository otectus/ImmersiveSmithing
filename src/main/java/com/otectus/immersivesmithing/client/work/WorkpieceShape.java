package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The current geometry of a piece, derived deterministically from its visual profile and its authoritative progress: each
 * segment eases from billet size to target size with the progress of the operations over it. Piece-local frame: x runs
 * along the length from {@code -length/2} (u = 0, tang or socket end) to {@code +length/2} (u = 1, tip), z across (v), y up
 * from the work surface. {@link #point} and {@link #toCanonical} work in visible-face coordinates; a turned-over piece
 * mirrors v ({@link #visibleV}). The same object serves drawing, picking and effects.
 */
public final class WorkpieceShape {
    public final WorkpieceVisual visual;
    public final float[] x0 = new float[WorkpieceVisual.SEGMENTS + 1];
    public final float[] width = new float[WorkpieceVisual.SEGMENTS];
    public final float[] thickness = new float[WorkpieceVisual.SEGMENTS];
    public final float[] curve = new float[WorkpieceVisual.SEGMENTS];
    public final float[] lift = new float[WorkpieceVisual.SEGMENTS];
    public final float[] progress = new float[WorkpieceVisual.SEGMENTS];
    public final float dish;
    public final float length;

    private WorkpieceShape(WorkpieceVisual visual, float[] segmentProgress, boolean flipped) {
        this.visual = visual;
        int n = WorkpieceVisual.SEGMENTS;
        float total = 0;
        float[] lengths = new float[n];
        float overall = 0;
        for (int i = 0; i < n; i++) {
            float p = ease(segmentProgress[i]);
            progress[i] = p;
            overall += p;
            lengths[i] = lerp(visual.billetLength() / n, visual.length() / n, p);
            width[i] = lerp(visual.billetWidth(), visual.width()[i], p);
            thickness[i] = lerp(visual.billetThickness(), visual.thickness()[i], p);
            // Turned over, an asymmetric piece shows its other side: the in-plane curve mirrors.
            curve[i] = visual.curve()[i] * p * (flipped ? -1F : 1F);
            lift[i] = visual.lift()[i] * p;
            total += lengths[i];
        }
        length = total;
        x0[0] = -total / 2F;
        for (int i = 0; i < n; i++) x0[i + 1] = x0[i] + lengths[i];
        dish = visual.dish() * overall / n;
        // A dished sheet keeps its thickness: lift its rim so the lowest underside still rests on the anvil.
        // Without this, the centre of deep plates passed through both the bottom face and the work surface.
        for (int i = 0; i < n; i++) lift[i] += dish;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float ease(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return t * t * (3F - 2F * t);
    }

    /** Shape of a piece in progress. */
    public static WorkpieceShape of(WorkpieceData piece) {
        WorkpieceVisual visual = WorkpieceVisuals.get(piece.process().profile.visual());
        BladeWorkpieceModel blade = BladeWorkpieceModel.get(piece.target(), visual);
        return of(blade == null ? visual : blade.profile(visual), piece.process());
    }

    /** Shape without a target model, used for raw billets and resource-profile previews. */
    public static WorkpieceShape of(WorkpieceVisual visual, @Nullable ProcessState p) {
        float[] seg = new float[WorkpieceVisual.SEGMENTS];
        if (p != null) {
            var ops = p.profile.operations();
            for (int s = 0; s < seg.length; s++) {
                float u = (s + 0.5F) / seg.length;
                float sum = 0;
                int count = 0;
                for (int i = 0; i < ops.size(); i++) {
                    ProcessProfile.Operation o = ops.get(i);
                    if (u >= o.u0() && u <= o.u1()) {
                        sum += p.progress(i) / 1000F;
                        count++;
                    }
                }
                seg[s] = count == 0 ? p.progress() / 1000F : sum / count;
            }
        }
        return new WorkpieceShape(visual, seg, p != null && p.flipped);
    }

    /** A fresh billet (nothing worked yet). */
    public static WorkpieceShape billet(WorkpieceVisual visual) {
        return new WorkpieceShape(visual, new float[WorkpieceVisual.SEGMENTS], false);
    }

    /** The visible-face v for a canonical v (turning the piece over mirrors it). */
    public static float visibleV(float v, boolean flipped) {
        return flipped ? -v : v;
    }

    public int segmentAt(float u) {
        return Math.max(0, Math.min(WorkpieceVisual.SEGMENTS - 1, (int) (u * WorkpieceVisual.SEGMENTS)));
    }

    /** x along the piece for a canonical u (segments are not equally long once worked). */
    public float xAt(float u) {
        float scaled = Math.max(0F, Math.min(1F, u)) * WorkpieceVisual.SEGMENTS;
        int s = Math.min(WorkpieceVisual.SEGMENTS - 1, (int) scaled);
        float f = scaled - s;
        return x0[s] + (x0[s + 1] - x0[s]) * f;
    }

    /** The top surface point for a canonical (u, v), in the piece's frame. */
    public Vec3 point(float u, float v) {
        int s = segmentAt(u);
        float z = curve[s] + v * width[s];
        float y = lift[s] + thickness[s] - dishAt(v);
        return new Vec3(xAt(u), y, z);
    }

    public float dishAt(float v) {
        // Four connected facets, shared by rendering, target projection and surface marks.
        float t = Math.max(0F, Math.min(4F, (v + 0.5F) * 4F));
        int strip = Math.min(3, (int) t);
        float a = -0.5F + strip / 4F, b = a + 0.25F;
        return dish * lerp(1F - 4F * a * a, 1F - 4F * b * b, t - strip);
    }

    /** Canonical (u, v) for a point on the surface plane in the piece frame, or null when it is off the piece. */
    @Nullable
    public float[] toCanonical(double x, double z) {
        if (x < x0[0] - 0.02 || x > x0[WorkpieceVisual.SEGMENTS] + 0.02) return null;
        for (int s = 0; s < WorkpieceVisual.SEGMENTS; s++) {
            if (x <= x0[s + 1] || s == WorkpieceVisual.SEGMENTS - 1) {
                float span = x0[s + 1] - x0[s];
                float f = span <= 0 ? 0F : (float) ((x - x0[s]) / span);
                float u = (s + Math.max(0F, Math.min(1F, f))) / WorkpieceVisual.SEGMENTS;
                float v = (float) ((z - curve[s]) / Math.max(0.005F, width[s]));
                if (v < -0.6F || v > 0.6F) return null;
                return new float[]{Math.max(0F, Math.min(1F, u)), Math.max(-0.5F, Math.min(0.5F, v))};
            }
        }
        return null;
    }

    public float maxThickness() {
        float m = 0;
        for (int i = 0; i < thickness.length; i++) m = Math.max(m, thickness[i] + lift[i]);
        return m;
    }
}
