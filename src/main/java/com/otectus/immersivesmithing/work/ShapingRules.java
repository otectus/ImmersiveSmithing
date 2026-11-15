package com.otectus.immersivesmithing.work;

/**
 * Deterministic hammer rules. A blow is aimed at one operation's area of the piece; where inside the area it lands does not
 * matter, so a pointer, a keyboard selection and a controller all play by the same rules. What matters is choosing the right
 * operation in the right order, on the right face, at the right heat, with a sensible blow:
 *
 * <ul>
 *   <li>below the workable band nothing happens ("reheat");</li>
 *   <li>each accepted blow adds work (more when the metal is hotter, more for a heavy blow on rough work) and records its
 *       precision, which is the heat quality times the blow's suitability;</li>
 *   <li>a completed operation saturates: further blows add no work, they mar it, unless they are clean corrections of an
 *       existing mark (a bounded number per operation);</li>
 *   <li>imprecise blows leave marks; marks cost up to 30% of the operation's quality.</li>
 * </ul>
 */
public final class ShapingRules {
    public static final int BASE_WORK = 100;
    public static final int MAX_CORRECTIONS = 4;
    public static final int SCALE_PER_BLOW = 40;

    public enum Strength { NORMAL, HEAVY }

    public enum Outcome {
        ACCEPTED, CORRECTED, OVERWORKED, TOO_COLD, WRONG_FACE, NEEDS_PREREQUISITE, NOT_SHAPEABLE, NO_OPERATION;

        public boolean changed() {
            return this == ACCEPTED || this == CORRECTED || this == OVERWORKED;
        }
    }

    public record StrikeResult(Outcome outcome, int operation, int quality, int workAdded, boolean finishedOperation, boolean finishedAll) {
        static StrikeResult refused(Outcome outcome, int op) {
            return new StrikeResult(outcome, op, 0, 0, false, false);
        }
    }

    /** Applies one blow to {@code s} (a copy owned by the caller); a refused blow leaves it untouched. */
    public static StrikeResult strike(ProcessState s, int op, Strength strength, int heat) {
        if (!s.stage.shapeable()) return StrikeResult.refused(Outcome.NOT_SHAPEABLE, op);
        if (op < 0 || op >= s.profile.operations().size()) return StrikeResult.refused(Outcome.NO_OPERATION, op);
        ProcessProfile.Operation o = s.profile.operations().get(op);
        if (!o.face().matches(s.flipped)) return StrikeResult.refused(Outcome.WRONG_FACE, op);
        if (!s.prerequisitesMet(op)) return StrikeResult.refused(Outcome.NEEDS_PREREQUISITE, op);
        int heatQuality = ThermalRules.heatQuality(heat, s.thermal);
        if (heatQuality == 0) return StrikeResult.refused(Outcome.TOO_COLD, op);

        int q = heatQuality * precision(o.kind(), strength) / 1000;
        s.revision++;
        s.blows[op]++;
        s.scale = Math.max(0, s.scale - SCALE_PER_BLOW);
        if (s.complete(op)) {
            if (s.marks[op] > 0 && q >= 950 && s.corrections[op] < MAX_CORRECTIONS) {
                s.marks[op] = Math.max(0, s.marks[op] - 200);
                s.corrections[op]++;
                return new StrikeResult(Outcome.CORRECTED, op, q, 0, false, s.allComplete());
            }
            s.marks[op] = Math.min(1000, s.marks[op] + 150);
            return new StrikeResult(Outcome.OVERWORKED, op, q, 0, false, s.allComplete());
        }
        int base = BASE_WORK * multiplier(o.kind(), strength) / 1000 * ThermalRules.workFactor(heat, s.thermal) / 1000;
        int added = Math.min(s.required(op) - s.work[op], Math.max(1, base));
        s.work[op] += added;
        s.qualitySum[op] += q * added;
        if (q < 800) {
            s.marks[op] = Math.min(1000, s.marks[op] + (800 - q) * 2 / 5);
        } else if (q >= 950 && s.marks[op] > 0 && s.corrections[op] < MAX_CORRECTIONS) {
            s.marks[op] = Math.max(0, s.marks[op] - 200);
            s.corrections[op]++;
        }
        boolean finishedOp = s.complete(op);
        boolean all = s.allComplete();
        if (s.stage == WorkStage.BILLET) s.stage = WorkStage.SHAPING;
        if (all) s.stage = WorkStage.SHAPED;
        return new StrikeResult(Outcome.ACCEPTED, op, q, added, finishedOp, all);
    }

    /** Work done by a blow, per mille of {@link #BASE_WORK}. */
    public static int multiplier(ProcessProfile.Kind kind, Strength strength) {
        if (strength == Strength.NORMAL) return 1000;
        if (kind.rough()) return 1600;
        return kind == ProcessProfile.Kind.FINISH ? 1000 : 1250;
    }

    /** How suitable the blow is for the work, per mille: a heavy blow mars finishing work. */
    public static int precision(ProcessProfile.Kind kind, Strength strength) {
        if (strength == Strength.NORMAL || kind.rough()) return 1000;
        return kind == ProcessProfile.Kind.FINISH ? 750 : 900;
    }

    /** One operation's quality, per mille: the work-weighted precision of its blows less up to 30% for marks. */
    public static int operationQuality(ProcessState s, int op) {
        if (s.work[op] <= 0) return 0;
        int average = (int) Math.min(1000, (long) s.qualitySum[op] / s.work[op]);
        int completion = s.progress(op);
        int quality = average * completion / 1000 - s.marks[op] * 300 / 1000;
        return Math.max(0, Math.min(1000, quality));
    }

    /** The shaping component of quality, per mille: required operations weighted by their weights. Nothing done is 0. */
    public static int shapingQuality(ProcessState s) {
        long weighted = 0, weights = 0;
        for (int i = 0; i < s.profile.operations().size(); i++) {
            int w = s.profile.operations().get(i).weight();
            weighted += (long) w * operationQuality(s, i);
            weights += w;
        }
        return weights == 0 ? 0 : (int) (weighted / weights);
    }

    /** The first operation, in profile order, that a blow can usefully go to now; -1 when all are done. */
    public static int nextOperation(ProcessState s) {
        for (int i = 0; i < s.profile.operations().size(); i++) {
            if (!s.complete(i) && s.prerequisitesMet(i)) return i;
        }
        return -1;
    }

    /** Whether the next useful operation needs the piece turned over. */
    public static boolean needsFlip(ProcessState s) {
        int next = nextOperation(s);
        return next >= 0 && !s.profile.operations().get(next).face().matches(s.flipped);
    }

    /** The operation under a point on the current face, preferring one that still needs work. -1 for none. */
    public static int operationAt(ProcessState s, float u, float v) {
        if (!Float.isFinite(u) || !Float.isFinite(v)) return -1;
        int fallback = -1;
        for (int i = 0; i < s.profile.operations().size(); i++) {
            ProcessProfile.Operation o = s.profile.operations().get(i);
            if (!o.face().matches(s.flipped) || !o.contains(u, v)) continue;
            if (!s.complete(i) && s.prerequisitesMet(i)) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }

    private ShapingRules() {}
}
