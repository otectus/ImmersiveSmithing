package com.otectus.immersivesmithing.work;

/**
 * The deterministic heat model shared by server, client mirrors and tests. Heat is an integer in
 * {@code 0..ThermalProfile.SCALE} per thermal zone (a billet has {@link #ZONES} zones along its length). Time is counted in
 * simulation ticks of the holder that has the piece, so unloaded chunks and offline players never cool anything, and a
 * slow server never makes metal expire faster than its own ticks.
 *
 * <p>Cooling in air is exponential ({@link #cool}); a forge pulls each zone toward the fire's temperature at a rate scaled by
 * how directly the zone sits over the fire ({@link #heatStep}). All arithmetic uses {@link StrictMath} so the same inputs
 * always give the same integers.</p>
 */
public final class ThermalRules {
    public static final int ZONES = 3;
    /** A freshly cast billet: dark, solid, not yet workable. */
    public static final int FRESH_BILLET = 2500;
    /** What a migrated legacy workpiece starts at: comfortably workable, so old hot pieces stay hot. */
    public static final int LEGACY_HEAT = 6800;
    public static final int BURN_THRESHOLD = 9500;
    public static final int BURN_WARNING = 9200;

    /** Air cooling per tick at multiplier 1 (about 30 s from the top of the ideal band to the bottom of the workable one). */
    static final double AIR_COOLING = 0.00085;
    /** Heating toward the fire's temperature per tick for a zone directly over the fire. */
    static final double FORGE_HEATING = 0.005;
    /** Fire with no air: low in the workable band. */
    public static final int FIRE_BASE = 5000;
    /** Fire with the bellows at full blast: hotter than any band, so air has to be managed. */
    public static final int FIRE_MAX = 9800;

    public static final int AIR_MAX = 1000;
    public static final int AIR_PER_PUMP = 400;
    public static final int AIR_DECAY = 4;
    /** One compression and release of the bellows; faster requests are not extra air. */
    public static final int PUMP_COOLDOWN_TICKS = 12;

    /** Billet positions on the tray: which end sits over the hottest coals. Rows: position, columns: zone, per mille. */
    private static final int[][] TRAY_EXPOSURE = {
            {1000, 650, 350},
            {650, 1000, 650},
            {350, 650, 1000}};
    public static final int TRAY_POSITIONS = TRAY_EXPOSURE.length;
    /** The reheat rest holds the whole piece in the mouth of the fire. */
    public static final int REST_EXPOSURE = 850;

    /** Exponential cooling in air over {@code ticks}; {@code assist} >= 1 slows it (a progression perk, never presentation). */
    public static int cool(int heat, long ticks, ThermalProfile profile, double assist) {
        if (ticks <= 0 || heat <= 0) return Math.max(0, heat);
        double k = AIR_COOLING * profile.cooling() / 1000.0 / Math.max(1.0, assist);
        return (int) Math.round(heat * StrictMath.exp(-k * Math.min(ticks, 1_000_000L)));
    }

    public static int[] cool(int[] heat, long ticks, ThermalProfile profile, double assist) {
        int[] out = new int[heat.length];
        for (int i = 0; i < heat.length; i++) out[i] = cool(heat[i], ticks, profile, assist);
        return out;
    }

    /** Water takes a piece from any heat to near cold within one quench duration. */
    public static int quenchCool(int heat, int ticksImmersed, int durationTicks) {
        if (ticksImmersed <= 0) return heat;
        double k = 3.0 / Math.max(1, durationTicks);
        return (int) Math.round(heat * StrictMath.exp(-k * ticksImmersed));
    }

    /** The temperature the fire pulls toward: 0 without fire, from {@link #FIRE_BASE} to {@link #FIRE_MAX} with the air. */
    public static int fireTarget(boolean lit, int air) {
        if (!lit) return 0;
        int a = Math.max(0, Math.min(AIR_MAX, air));
        return FIRE_BASE + (int) ((long) (FIRE_MAX - FIRE_BASE) * a / AIR_MAX);
    }

    /** One tick in the forge: toward the fire if hotter, otherwise ordinary air cooling. */
    public static int heatStep(int heat, int exposure, int fireTarget, ThermalProfile profile) {
        if (fireTarget > heat) {
            double rate = FORGE_HEATING * exposure / 1000.0 * profile.heating() / 1000.0;
            int gain = (int) Math.round((fireTarget - heat) * rate);
            return Math.min(fireTarget, heat + Math.max(gain, exposure > 0 ? 1 : 0));
        }
        return cool(heat, 1, profile, 1.0);
    }

    public static int trayExposure(int position, int zone) {
        int p = Math.floorMod(position, TRAY_POSITIONS);
        return TRAY_EXPOSURE[p][Math.max(0, Math.min(ZONES - 1, zone))];
    }

    /** The air left after one tick of leakage. */
    public static int airStep(int air) {
        return Math.max(0, air - AIR_DECAY);
    }

    /**
     * How well a blow or a draw at this heat works, per mille: 0 below the workable band (refused), rising to 1000 across
     * the lower workable band, flat across the optimum, easing to 750 at the top of the workable band and down to 300 when
     * overheated. The flat optimum means careful work at the right heat is exactly perfect, whatever the input device.
     */
    public static int heatQuality(int heat, ThermalProfile p) {
        if (heat < p.workableMin()) return 0;
        if (heat < p.optimalMin()) return 500 + scale(heat - p.workableMin(), 500, p.optimalMin() - p.workableMin());
        if (heat <= p.optimalMax()) return 1000;
        if (heat <= p.workableMax()) return 1000 - scale(heat - p.optimalMax(), 250, p.workableMax() - p.optimalMax());
        return Math.max(300, 750 - scale(heat - p.workableMax(), 450, Math.max(1, ThermalProfile.SCALE - p.workableMax())));
    }

    /** Hot metal moves more under the hammer: 70% of a blow's work at the bottom of the workable band, all of it at the top. */
    public static int workFactor(int heat, ThermalProfile p) {
        if (heat < p.workableMin()) return 0;
        if (heat >= p.workableMax()) return 1000;
        return 700 + scale(heat - p.workableMin(), 300, p.workableMax() - p.workableMin());
    }

    public static int mean(int[] heat) {
        if (heat.length == 0) return 0;
        long sum = 0;
        for (int h : heat) sum += h;
        return (int) (sum / heat.length);
    }

    public static int spread(int[] heat) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int h : heat) {
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        return heat.length == 0 ? 0 : max - min;
    }

    public static int coolest(int[] heat) {
        int min = Integer.MAX_VALUE;
        for (int h : heat) min = Math.min(min, h);
        return heat.length == 0 ? 0 : min;
    }

    /** Spread below this (0.06) counts as even. */
    public static final int EVEN_SPREAD = 600;

    /**
     * The heat-preparation component recorded when the billet is drawn: the mean quality of the zones, less up to 40% for
     * uneven heating. Scale is kept separately because hammering can still knock it off.
     */
    public static int heatPreparation(int[] heat, ThermalProfile p) {
        if (heat.length == 0) return 0;
        long sum = 0;
        for (int h : heat) sum += heatQuality(h, p);
        int quality = (int) (sum / heat.length);
        int penalty = Math.max(0, Math.min(400, (spread(heat) - EVEN_SPREAD) / 4));
        return Math.max(0, Math.min(1000, quality - penalty));
    }

    /** Scale gained in one tick: only above the workable band, faster the hotter it is. Total scale is capped at 1000. */
    public static int scaleStep(int[] heat, ThermalProfile p) {
        int gain = 0;
        for (int h : heat) if (h > p.workableMax()) gain += Math.max(1, (h - p.workableMax()) / 300);
        return gain;
    }

    /** Whether any zone is hot enough to burn this tick. */
    public static boolean burning(int[] heat) {
        for (int h : heat) if (h >= BURN_THRESHOLD) return true;
        return false;
    }

    /** The thermal zone an operation's centre lies in. */
    public static int zoneAt(float u) {
        return Math.max(0, Math.min(ZONES - 1, (int) (u * ZONES)));
    }

    private static int scale(int value, int range, int over) {
        if (over <= 0) return range;
        return (int) ((long) Math.max(0, Math.min(value, over)) * range / over);
    }

    private ThermalRules() {}
}
