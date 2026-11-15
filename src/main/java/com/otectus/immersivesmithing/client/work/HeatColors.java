package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.client.renderer.RenderUtil;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.work.ThermalProfile;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;

/**
 * What heat looks like. Cold metal keeps its own colour and the world's light; from dull red through cherry, orange and
 * yellow to near white it glows by itself. The words of {@link ThermalProfile.HeatBand} always accompany the colour, so no
 * state is told by colour alone.
 */
public final class HeatColors {
    /** Chosen so the default ideal band (0.58..0.72) reads as orange to yellow-orange, the colour smiths forge at. */
    private static final float[] STOPS = {0.36F, 0.44F, 0.52F, 0.60F, 0.70F, 0.86F};
    private static final int[] COLORS = {0x000000, 0x5C1A0E, 0xA32611, 0xE8601A, 0xFFAA3C, 0xFFF0C0};

    /** The surface colour at a heat (0..1) over a cold colour. */
    public static int color(float heat, int cold) {
        if (heat <= STOPS[0]) return cold;
        for (int i = 1; i < STOPS.length; i++) {
            if (heat <= STOPS[i]) {
                float t = (heat - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
                int from = i == 1 ? RenderUtil.mixColor(cold, COLORS[1], 0.35F) : COLORS[i - 1];
                return RenderUtil.mixColor(from, COLORS[i], t);
            }
        }
        return COLORS[COLORS.length - 1];
    }

    /** Packed light: hot metal lights itself, cold metal is lit by the world. */
    public static int light(float heat, int worldLight) {
        if (heat <= 0.38F) return worldLight;
        int block = Math.min(15, Math.round(6 + (heat - 0.38F) / 0.36F * 9));
        return RenderUtil.atLeastBlockLight(worldLight, block);
    }

    /** A safe, legible colour for a band in the HUD (palette follows the colour-blind option). */
    public static int bandColor(ThermalProfile.HeatBand band) {
        boolean safe = ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS);
        return switch (band) {
            case COLD -> safe ? 0xFF5A8CFF : 0xFF7F9FBF;
            case WARM -> 0xFFE0A050;
            case IDEAL -> safe ? 0xFFFFFFFF : 0xFF7FE07F;
            case HOT -> 0xFFFFC040;
            case OVERHEATED, BURNING -> safe ? 0xFFFF9F1C : 0xFFFF5040;
        };
    }

    public static Component bandName(ThermalProfile.HeatBand band) {
        return Component.translatable("heat.immersive_smithing." + band.key());
    }

    /** The colour of cold stock for a family id path (datapack tints describe molten metal, not cold stock). */
    public static int coldColor(String familyPath) {
        return switch (familyPath) {
            case "gold" -> 0xE8C850;
            case "copper" -> 0xC98A6A;
            case "bronze" -> 0xB88C5E;
            case "netherite" -> 0x5A4F58;
            case "steel" -> 0x9AA8B1;
            case "invar" -> 0xB8C4B4;
            case "nickel" -> 0xC4BE9C;
            case "lead" -> 0x8C849C;
            case "electrum" -> 0xD8C87E;
            default -> 0xB4B8BC;
        };
    }

    public static int fullBright() {
        return LightTexture.FULL_BRIGHT;
    }

    private HeatColors() {}
}
