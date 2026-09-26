package com.otectus.immersivesmithing.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Stoneborn workshop surfaces, with minigame colors and cues kept accessible. */
public final class SmithingGui extends StonebornGui {
    public static final ResourceLocation TEXTURE = ImmersiveSmithing.id("textures/gui/smithing.png");

    public static final int SOOT = SURFACE;
    public static final int SOOT_DEEP = INSET;
    public static final int TIMBER = BORDER;
    public static final int BRONZE = BRASS;
    public static final int HOT_ORANGE = 0xFFF08A38;
    public static final int ROW_FOCUSED = ROW_SELECTED;

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        StonebornGui.panel(g, x, y, w, h);
        g.fill(x + 6, y + 6, x + w - 6, y + 22, SURFACE);
        g.fill(x + 8, y + 24, x + w - 8, y + h - 8, SURFACE);
    }

    public static int drawWrappedCentered(GuiGraphics g, net.minecraft.client.gui.Font font, Component text,
                                          int centerX, int y, int width, int color) {
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawCenteredString(font, lines.get(i), centerX, y + i * 10, color);
        }
        return lines.size();
    }

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

    /** A horizontal time bar that warms from gold to red as it empties. */
    public static void timeBar(GuiGraphics g, int x, int y, int w, int h, float remaining) {
        well(g, x - 1, y - 1, w + 2, h + 2);
        int filled = Math.round(w * Math.max(0F, Math.min(1F, remaining)));
        int color = remaining > 0.25F ? 0xFFE0A030 : (ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFFFF9F1C : 0xFFD04030);
        if (filled > 0) g.fill(x, y, x + filled, y + h, color);
        if (filled > 1 && h > 2) g.fill(x, y, x + filled, y + 1, 0xFFFFD999);
        for (int i = 1; i < 4; i++) g.fill(x + w * i / 4, y, x + w * i / 4 + 1, y + h, 0x80302A24);
    }

    public static int zoneColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFFFFF;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFF3D8BFF : 0xFFE07A2C;
    }

    /** Fill of an anvil strike target: the same hot orange as the forge zone, with a yellow centre on top. */
    public static int targetColor() {
        if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) return 0xFFFFFFFF;
        return ClientConfig.get(ClientConfig.COLORBLIND_SAFE_TARGETS) ? 0xFF3D8BFF : 0xFFE07A2C;
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

    /** "Perfect!", "Good", "Rough" or "Miss" for an accuracy in 0..1. */
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
