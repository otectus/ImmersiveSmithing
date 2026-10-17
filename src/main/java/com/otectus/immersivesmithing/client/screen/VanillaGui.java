package com.otectus.immersivesmithing.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The one look every Immersive Smithing screen and the JEI category share: Minecraft's own container panel, slots,
 * buttons, text fields, scroller and book. Every surface is drawn from Minecraft's textures through the resource
 * manager, so a resource pack that restyles vanilla containers restyles these screens too, and nothing here is a
 * texture of this mod. Text follows vanilla containers: dark grey labels on the light panel, black ink in the book.
 */
public final class VanillaGui {
    public static final ResourceLocation PANEL = new ResourceLocation("textures/gui/demo_background.png");
    public static final ResourceLocation SLOTS = new ResourceLocation("textures/gui/container/generic_54.png");
    public static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    public static final ResourceLocation SCROLLER = new ResourceLocation("textures/gui/container/creative_inventory/tabs.png");
    public static final ResourceLocation BOOK = new ResourceLocation("textures/gui/book.png");
    public static final ResourceLocation FURNACE = new ResourceLocation("textures/gui/container/furnace.png");

    /** Container titles and labels (vanilla {@code 0x404040}). */
    public static final int LABEL = 0xFF404040;
    /** Secondary text on the light panel. */
    public static final int LABEL_DIM = 0xFF6B6B6B;
    /** Text on button rows, as on vanilla buttons. */
    public static final int ROW_TEXT = 0xFFFFFFFF;
    public static final int ROW_TEXT_DIM = 0xFFD0D0D0;
    public static final int ROW_TEXT_DISABLED = 0xFFA0A0A0;
    /** Book ink. */
    public static final int INK = 0xFF000000;
    public static final int INK_DIM = 0xFF4E4234;
    /** Light text inside dark gameplay displays. */
    public static final int DISPLAY_TEXT = 0xFFE0E0E0;
    public static final int DISPLAY_TEXT_DIM = 0xFFA8A8A8;

    public static final int BOOK_WIDTH = 192;
    public static final int BOOK_HEIGHT = 192;

    /** A vanilla container panel of any size: the standard bevelled light grey frame. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.blitNineSliced(PANEL, x, y, w, h, 4, 248, 166, 0, 0);
    }

    /** A recessed field in the colours of a vanilla slot (dark top-left edge, white bottom-right edge). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    /** A recessed dark display for gameplay indicators that need contrast (the forge track, the anvil's work area). */
    public static void display(GuiGraphics g, int x, int y, int w, int h) {
        inset(g, x, y, w, h);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF262626);
    }

    /** The standard 18 px item slot. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.blit(SLOTS, x, y, 7, 17, 18, 18);
    }

    /** A list row drawn as a vanilla button: disabled, normal, or highlighted (hovered or keyboard-selected). */
    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean highlighted) {
        int v = !active ? 46 : highlighted ? 86 : 66;
        g.blitNineSliced(WIDGETS, x, y, w, h, 20, 4, 200, 20, 0, v);
    }

    /** A vanilla scroll bar: a recessed track and the creative inventory's scroller handle. */
    public static void scrollbar(GuiGraphics g, int x, int y, int h, float position, boolean enabled) {
        inset(g, x, y, 14, h);
        int travel = Math.max(0, h - 17);
        int thumb = y + 1 + Math.round(travel * Math.max(0F, Math.min(1F, position)));
        g.blit(SCROLLER, x + 1, thumb, enabled ? 232 : 244, 0, 12, 15);
    }

    /** True when (mx, my) is on a scroll bar drawn with {@link #scrollbar}. */
    public static boolean onScrollbar(double mx, double my, int x, int y, int h) {
        return mx >= x && mx < x + 14 && my >= y && my < y + h;
    }

    /** The furnace's progress arrow, empty or filled to {@code progress}. */
    public static void arrow(GuiGraphics g, int x, int y, float progress) {
        g.blit(FURNACE, x, y, 79, 34, 24, 17);
        int filled = Math.round(24 * Math.max(0F, Math.min(1F, progress)));
        if (filled > 0) g.blit(FURNACE, x, y, 176, 14, filled, 17);
    }

    /** A vanilla-style bar: recessed track, flat fill and a one-pixel highlight on top. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        inset(g, x - 1, y - 1, w + 2, h + 2);
        g.fill(x, y, x + w, y + h, 0xFF373737);
        int filled = Math.round(w * Math.max(0F, Math.min(1F, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            if (h > 2) g.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
        }
    }

    /** A thin separating rule in the panel's own shading. */
    public static void rule(GuiGraphics g, int x, int y, int width) {
        g.fill(x, y, x + width, y + 1, 0xFF8B8B8B);
        g.fill(x, y + 1, x + width, y + 2, 0xFFFFFFFF);
    }

    /** The left half of the vanilla book: a cream page with its leather edge (192 x 192). */
    public static void bookPage(GuiGraphics g, int x, int y) {
        g.blit(BOOK, x, y, 0, 0, BOOK_WIDTH, BOOK_HEIGHT);
    }

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress).bounds(x, y, width, height).build();
    }

    /** A vanilla bordered text field (black box, grey border, white text). */
    public static EditBox editBox(Font font, int x, int y, int width, Component label) {
        return new EditBox(font, x, y, width, 18, label);
    }

    /** The text cut to {@code width} with an ellipsis; the full text belongs in a tooltip. */
    public static String clipped(Font font, Component text, int width) {
        if (font.width(text) <= width) return text.getString();
        return font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("..."))) + "...";
    }

    public static boolean isClipped(Font font, Component text, int width) {
        return font.width(text) > width;
    }

    /** Wrapped text, at most {@code maxLines} lines; returns the number of lines drawn. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int n = Math.min(lines.size(), Math.max(0, maxLines));
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        return n;
    }

    /** Centred wrapped text; returns the number of lines drawn. */
    public static int wrappedCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int width, int color, boolean shadow) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), centerX - font.width(lines.get(i)) / 2, y + i * 10, color, shadow);
        }
        return lines.size();
    }

    public static void centered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private VanillaGui() {}
}
