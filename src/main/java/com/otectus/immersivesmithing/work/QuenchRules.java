package com.otectus.immersivesmithing.work;

/**
 * The quench as a short physical operation. Lowering takes {@link #BOUNDARY_TICKS}; the water is charged when the piece
 * crosses that boundary and never again for the same piece. Immersion then counts up; at the profile's duration the hiss
 * subsides (the end cue) and lifting completes the quench. The window is broad: anything from the cue to three durations
 * is a perfect quench. Lifting early keeps the progress (lower it again to continue) but costs a little control per extra
 * dip; leaving it in very long costs a little more. Nothing here can reroll a finished quench.
 */
public final class QuenchRules {
    public static final int BOUNDARY_TICKS = 10;
    public static final int DIP_PENALTY = 120;
    public static final int MAX_DIP_PENALTY = 360;
    public static final int MAX_OVERSTAY_PENALTY = 250;

    public static int duration(ProcessState s, double multiplier) {
        return Math.max(20, (int) Math.round(s.profile.quenchTicks() * Math.max(0.25, Math.min(4.0, multiplier))));
    }

    public static boolean done(ProcessState s, int duration) {
        return s.quenchTicks >= duration;
    }

    /** Immersion stops counting after six durations; the piece is long cold by then. */
    public static int maxTicks(int duration) {
        return duration * 6;
    }

    /** Quench control, per mille, for a quench lifted now. */
    public static int control(ProcessState s, int duration) {
        int control = 1000 - Math.min(MAX_DIP_PENALTY, DIP_PENALTY * Math.max(0, s.dips - 1));
        int overstay = s.quenchTicks - duration * 3;
        if (overstay > 0) control -= Math.min(MAX_OVERSTAY_PENALTY, overstay * MAX_OVERSTAY_PENALTY / (duration * 3));
        return Math.max(0, Math.min(1000, control));
    }

    /** How far the hiss has subsided, per mille, for presentation. */
    public static int progress(ProcessState s, int duration) {
        return (int) Math.min(1000, (long) s.quenchTicks * 1000 / Math.max(1, duration));
    }

    private QuenchRules() {}
}
