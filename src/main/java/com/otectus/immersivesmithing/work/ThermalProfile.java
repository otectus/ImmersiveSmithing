package com.otectus.immersivesmithing.work;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * How a piece behaves in the fire, in heat units of {@link #SCALE} (10000 = the hottest the forge can make anything).
 * Bands are ordered {@code 0 <= workableMin < optimalMin < optimalMax < workableMax <= SCALE}; {@code cooling} and
 * {@code heating} are per-mille multipliers of the base rates in {@link ThermalRules}. This is readable game design, not
 * a metallurgical claim: a datapack may give a fantasy metal any bands it likes, as long as they are ordered.
 */
public record ThermalProfile(int workableMin, int workableMax, int optimalMin, int optimalMax, int cooling, int heating) {
    public static final int SCALE = 10_000;
    public static final ThermalProfile DEFAULT = new ThermalProfile(4500, 8000, 5800, 7200, 1000, 1000);

    public ThermalProfile {
        String problem = problem(workableMin, workableMax, optimalMin, optimalMax, cooling, heating);
        if (problem != null) throw new IllegalArgumentException(problem);
    }

    @Nullable
    private static String problem(int wMin, int wMax, int oMin, int oMax, int cooling, int heating) {
        if (wMin < 0 || wMax > SCALE) return "heat bands must lie within 0..1";
        if (!(wMin < oMin && oMin < oMax && oMax < wMax)) {
            return "heat bands must be ordered workable_min < optimal_min < optimal_max < workable_max";
        }
        if (oMax - oMin < 200) return "the optimal band must be at least 0.02 wide";
        if (cooling < 100 || cooling > 10_000) return "cooling must be between 0.1 and 10";
        if (heating < 100 || heating > 10_000) return "heating must be between 0.1 and 10";
        return null;
    }

    /**
     * Reads a {@code "heat"} or {@code "thermal"} block. Every field is optional and falls back to {@code base}; the result
     * is validated as a whole, so an unordered set of bands fails with the field names in the message.
     */
    public static ThermalProfile fromJson(JsonObject json, ThermalProfile base) {
        int wMin = band(json, "workable_min", base.workableMin);
        int wMax = band(json, "workable_max", base.workableMax);
        int oMin = band(json, "optimal_min", base.optimalMin);
        int oMax = band(json, "optimal_max", base.optimalMax);
        int cooling = multiplier(json, "cooling", base.cooling);
        int heating = multiplier(json, "heating", base.heating);
        String problem = problem(wMin, wMax, oMin, oMax, cooling, heating);
        if (problem != null) throw new JsonParseException(problem);
        return new ThermalProfile(wMin, wMax, oMin, oMax, cooling, heating);
    }

    private static int band(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        float value = GsonHelper.getAsFloat(json, key);
        if (!Float.isFinite(value) || value < 0F || value > 1F) throw new JsonParseException(key + " must be a number from 0 to 1");
        return Math.round(value * SCALE);
    }

    private static int multiplier(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        float value = GsonHelper.getAsFloat(json, key);
        if (!Float.isFinite(value) || value < 0.1F || value > 10F) throw new JsonParseException(key + " must be a number from 0.1 to 10");
        return Math.round(value * 1000F);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Bands", new int[]{workableMin, optimalMin, optimalMax, workableMax, cooling, heating});
        return tag;
    }

    /** Reads a saved profile; anything malformed gives the default rather than an unusable piece. */
    public static ThermalProfile load(CompoundTag tag) {
        int[] b = tag.getIntArray("Bands");
        if (b.length != 6) return DEFAULT;
        String problem = problem(b[0], b[3], b[1], b[2], b[4], b[5]);
        return problem == null ? new ThermalProfile(b[0], b[3], b[1], b[2], b[4], b[5]) : DEFAULT;
    }

    public HeatBand band(int heat) {
        if (heat < workableMin) return HeatBand.COLD;
        if (heat < optimalMin) return HeatBand.WARM;
        if (heat <= optimalMax) return HeatBand.IDEAL;
        if (heat <= workableMax) return HeatBand.HOT;
        return heat >= ThermalRules.BURN_THRESHOLD ? HeatBand.BURNING : HeatBand.OVERHEATED;
    }

    public boolean workable(int heat) {
        return heat >= workableMin;
    }

    /** Midpoint of the optimal band. */
    public int optimalMid() {
        return (optimalMin + optimalMax) / 2;
    }

    public static float fraction(int heat) {
        return Mth.clamp(heat / (float) SCALE, 0F, 1F);
    }

    /** What the heat looks like: one band per state, so presentation never needs the numbers. */
    public enum HeatBand {
        COLD, WARM, IDEAL, HOT, OVERHEATED, BURNING;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
