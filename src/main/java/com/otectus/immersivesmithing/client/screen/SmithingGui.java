package com.otectus.immersivesmithing.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * The minigames' own indicators, drawn inside the dark displays of the vanilla screens ({@link VanillaGui#display}):
 * timing zones, strike targets, ratings and cue sounds. Colours honour the client's high-contrast and colorblind
 * options, every state also has a label or a shape, and none of this changes a scoring window.
 */
public final class SmithingGui {
    /** Target sprites: ring {@code (96,0,64,64)} and disc {@code (160,0,64,64)}. Replaceable by resource packs. */
    public static final ResourceLocation TEXTURE = ImmersiveSmithing.id("textures/gui/smithing.png");

    /** Draws the ring (outline) or disc sprite centred on (cx, cy), tinted with an ARGB colour. */
    public static void circle(GuiGraphics g, float cx, float cy, float radius, boolean ring, int argb) {
        int size = Math.max(2, Math.round(radius * 2));
        int x = Math.round(cx - size / 2F);
        int y = Math.round(cy - size / 2F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((argb >> 16) & 0xFF) / 255F, ((argb >> 8) & 0xFF) / 255F, (argb & 0xFF) / 255F, ((argb >>> 24) & 0xFF) / 255F);
        g.blit(TEXTURE, x, y, size, size, ring ? 96 : 160, 0, 64, 64, 256, 256);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    /** The time left as a vanilla bar: amber while there is time, then red (orange when colorblind-safe). */
    public static void timeBar(GuiGraphics g, int x, int y, int w, int h, float remaining) {
        int color = remaining > 0.25F ? 0xFFE0A030 : (ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFFFF9F1C : 0xFFD04030);
        VanillaGui.bar(g, x, y, w, h, remaining, color);
        // Quarter marks make the remaining time readable without colour.
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + h, 0x80000000);
    }

    public static int zoneColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFFFFF;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFF3D8BFF : 0xFFE07A2C;
    }

    /** Fill of an anvil strike target: the same colour as the forge zone, with a bright centre on top. */
    public static int targetColor() {
        return zoneColor();
    }

    public static int perfectColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFE600;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFFBFE0FF : 0xFFFFD66B;
    }

    public static int markerColor() {
        return ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES) ? 0xFF000000 : 0xFFFFFFFF;
    }

    public static int goodColor() {
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFF3D8BFF : 0xFF5AD05A;
    }

    public static int missColor() {
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFFFF9F1C : 0xFFE04848;
    }

    /** "Perfect!", "Good", "Rough" or "Miss" for an accuracy in 0..1: the words carry the result, colour only adds to it. */
    public static Component rating(float accuracy) {
        String key = accuracy >= 0.9F ? "perfect" : accuracy >= 0.6F ? "good" : accuracy > 0F ? "rough" : "miss";
        return Component.translatable("minigame.immersive_smithing.rating." + key);
    }

    public static int ratingColor(float accuracy) {
        if (accuracy >= 0.9F) return perfectColor();
        if (accuracy >= 0.6F) return goodColor();
        if (accuracy > 0F) return 0xFFE0C050;
        return missColor();
    }

    public static float markerScale() {
        return ClientConfig.get(ClientConfig.LARGE_MINIGAME_TARGETS) ? 1.6F : 1.0F;
    }

    public static void playUi(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    public static void playCue() {
        if (!ClientConfig.get(ClientConfig.TIMING_CUE_SOUNDS)) return;
        float volume = (float) ClientConfig.get(ClientConfig.SOUND_CUE_VOLUME);
        if (volume > 0F) playUi(ModSounds.TIMING_CUE.get(), 1.4F, volume);
    }

    private SmithingGui() {}
}
