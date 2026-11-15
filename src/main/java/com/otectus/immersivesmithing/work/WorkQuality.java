package com.otectus.immersivesmithing.work;

/**
 * Turns process records into the two finished-item scores, each finalized once:
 *
 * <pre>
 * ForgeScore = round(100 * clamp(w * max(0, heatPreparation - scale / 4) + (1 - w) * quenchControl))   (w = 0.70 by default)
 * AnvilScore = round(100 * clamp(sum(weight_i * operationQuality_i) / sum(weight_i)))
 * </pre>
 *
 * Components are per mille and server-derived. Legacy scores are returned unchanged, never recomputed.
 */
public final class WorkQuality {

    /** The forge score once the quench is done; the stored one for a legacy piece. */
    public static int forgeScore(ProcessState s, int stored, double heatWeight) {
        if (s.has(ProcessState.LEGACY_FORGE)) return clamp100(stored);
        double w = Math.max(0.0, Math.min(1.0, heatWeight));
        int prep = Math.max(0, Math.max(0, s.heatPrep) - s.scale / 4);
        int quench = Math.max(0, s.quenchControl);
        double value = (w * prep + (1.0 - w) * quench) / 1000.0;
        return (int) Math.round(100.0 * Math.max(0.0, Math.min(1.0, value)));
    }

    /** The anvil score when the shaping phase ends (the quench begins); the stored one for a legacy piece. */
    public static int anvilScore(ProcessState s, int stored) {
        if (s.has(ProcessState.LEGACY_ANVIL)) return clamp100(stored);
        return (int) Math.round(ShapingRules.shapingQuality(s) / 10.0);
    }

    private static int clamp100(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private WorkQuality() {}
}
