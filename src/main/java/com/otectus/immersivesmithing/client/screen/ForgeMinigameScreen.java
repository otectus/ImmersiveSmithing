package com.otectus.immersivesmithing.client.screen;

import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.minigame.ForgeMinigame;
import com.otectus.immersivesmithing.minigame.ForgeRun;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.CancelSessionPacket;
import com.otectus.immersivesmithing.network.packet.ForgeActionPacket;
import com.otectus.immersivesmithing.network.packet.ForgeActionResultPacket;
import com.otectus.immersivesmithing.network.packet.ForgeStartPacket;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.network.packet.SelectForgeRecipePacket;
import com.otectus.immersivesmithing.network.packet.SessionResultPacket;
import com.otectus.immersivesmithing.quality.SmithingQuality;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.registry.ModTags;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Forge screen: recipe selection, a short countdown, the forming-rhythm minigame and the result, on a vanilla
 * container panel. Recipes are vanilla button rows (as in a villager's trade list) under a vanilla search field, with
 * the creative scroller; the timing track sits in a dark display so its zones stay clear. The client predicts feedback
 * locally but only ever sends input events; the score shown at the end is the server's.
 */
public class ForgeMinigameScreen extends WorkshopScreen {
    private enum Stage { SELECT, WAITING, READY, PLAYING, RESULT }

    private static final int ROW_HEIGHT = 26;
    private static final int ROW_INNER = 24;

    private final OpenForgeScreenPacket offer;
    private final List<OpenForgeScreenPacket.Entry> filtered = new ArrayList<>();
    private Stage stage = Stage.SELECT;
    private EditBox search;
    private Button doneButton;
    private Button helpButton;
    private int scroll;
    private int selectedIndex;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listRows;
    private boolean draggingScrollbar;

    private ItemStack selected = ItemStack.EMPTY;
    private long clientStartMs;
    private int allowedMs;
    private ForgeRun run;
    private final List<Float> phaseResults = new ArrayList<>();
    private float lastAccuracy = -1F;
    private long lastFeedbackMs;
    private long earlyMs = Long.MIN_VALUE;
    /** Action keys held down: their key repeats are not new presses. */
    private final Set<Integer> heldKeys = new HashSet<>();
    private boolean markerInZone;
    private int resultScore;
    private boolean resultTimedOut;
    private boolean closedByServer;

    public ForgeMinigameScreen(OpenForgeScreenPacket offer) {
        super(Component.translatable("screen.immersive_smithing.forge"), 320, 240);
        this.offer = offer;
    }

    public int sessionId() {
        return offer.sessionId();
    }

    @Override
    protected void initPanel() {
        panelWidth = Math.min(340, canvasWidth - 12);
        String previous = search != null ? search.getValue() : "";
        search = VanillaGui.editBox(font, 0, 0, panelWidth - 16, Component.translatable("screen.immersive_smithing.forge.search"));
        search.setHint(Component.translatable("screen.immersive_smithing.forge.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(previous);
        search.setResponder(s -> refilter());
        search.visible = stage == Stage.SELECT;
        addRenderableWidget(search);
        doneButton = VanillaGui.button(0, 0, 100, 20, Component.translatable("gui.done"), b -> onClose());
        doneButton.visible = stage == Stage.RESULT;
        addRenderableWidget(doneButton);
        helpButton = VanillaGui.button(0, 0, 16, 14, Component.literal("?"), b -> {});
        helpButton.setTooltip(Tooltip.create(Component.translatable(stage == Stage.SELECT
                ? "screen.immersive_smithing.forge.help_select" : "screen.immersive_smithing.forge.help_play")));
        addRenderableWidget(helpButton);
        layout();
        refilter();
        if (stage == Stage.SELECT) setInitialFocus(search);
    }

    /**
     * Fits the panel to its stage: the full height for the recipe list, and one compact height for every forging stage
     * (the tallest of them), so the panel holds still from the countdown to the result.
     */
    private void layout() {
        panelHeight = Math.min(stage == Stage.SELECT ? 236 : forgingHeight(), canvasHeight - 4);
        left = (canvasWidth - panelWidth) / 2;
        top = (canvasHeight - panelHeight) / 2;
        listTop = top + 46;
        listRows = Math.max(3, (panelHeight - 46 - 22) / ROW_HEIGHT);
        search.setPosition(left + 8, top + 22);
        doneButton.setPosition(left + panelWidth / 2 - 50, top + panelHeight - 26);
        helpButton.setPosition(left + panelWidth - 22, top + 4);
    }

    /** Bottom of the lowest content in the countdown, minigame and result stages, plus a bottom margin. */
    private int forgingHeight() {
        int track = Math.round(16 * SmithingGui.markerScale());
        int playing = 98 + track + 42 + 6 + 10 * lines("screen.immersive_smithing.forge.instructions", panelWidth - 24) + 8;
        int ready = 122 + 10 * lines("screen.immersive_smithing.forge.instructions", panelWidth - 32) + 6
                + 10 * lines("screen.immersive_smithing.forge.durability_explainer", panelWidth - 32) + 8;
        int result = 124 + 10 * lines("screen.immersive_smithing.forge.next", panelWidth - 40) + 6 + 26;
        return Math.max(playing, Math.max(ready, result));
    }

    private int lines(String key, int width) {
        return font.split(Component.translatable(key), width).size();
    }

    /** Every word must appear in the item's name; a word starting with @ must match its mod instead, as in JEI. */
    private void refilter() {
        filtered.clear();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> words = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        for (String token : query.split("\\s+")) {
            if (token.startsWith("@")) {
                String mod = squash(token.substring(1));
                if (!mod.isEmpty()) mods.add(mod);
            } else if (!token.isEmpty()) {
                words.add(token);
            }
        }
        for (OpenForgeScreenPacket.Entry e : offer.entries()) {
            String name = e.result().getHoverName().getString().toLowerCase(Locale.ROOT);
            if (words.stream().allMatch(name::contains) && mods.stream().allMatch(mod -> fromMod(e.result(), mod))) filtered.add(e);
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        selectedIndex = Mth.clamp(selectedIndex, 0, Math.max(0, filtered.size() - 1));
    }

    /** Whether {@code stack} comes from a mod whose id or name contains {@code mod} (letters and digits only). */
    private static boolean fromMod(ItemStack stack, String mod) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        return squash(namespace).contains(mod) || ModList.get().getModContainerById(namespace)
                .map(container -> squash(container.getModInfo().getDisplayName()).contains(mod)).orElse(false);
    }

    private static String squash(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private int maxScroll() {
        return Math.max(0, filtered.size() - listRows);
    }

    private void revealSelection() {
        if (selectedIndex < scroll) scroll = selectedIndex;
        if (selectedIndex >= scroll + listRows) scroll = selectedIndex - listRows + 1;
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    // ------------------------------------------------------------------ server events

    public void onStart(ForgeStartPacket packet) {
        if (packet.sessionId() != offer.sessionId()) return;
        selected = packet.result();
        allowedMs = packet.allowedMs();
        run = new ForgeRun(ForgeMinigame.phases(packet.pattern(), packet.seed(), packet.metalUnits()), packet.pattern().transitionMs());
        clientStartMs = Util.getMillis() + packet.readyDelayMs();
        stage = Stage.READY;
        search.visible = false;
        setFocused(null);
        layout();
        helpButton.setTooltip(Tooltip.create(Component.translatable("screen.immersive_smithing.forge.help_play")));
    }

    public void onActionResult(ForgeActionResultPacket packet) {
        if (packet.sessionId() != offer.sessionId()) return;
        if (packet.phase() >= 0 && packet.phase() < phaseResults.size()) {
            phaseResults.set(packet.phase(), packet.accuracy());
            if (packet.phase() == phaseResults.size() - 1) lastAccuracy = packet.accuracy();
        }
    }

    public void onResult(SessionResultPacket packet) {
        if (packet.sessionId() != offer.sessionId()) return;
        resultScore = packet.score();
        resultTimedOut = packet.timedOut();
        stage = Stage.RESULT;
        layout();
        doneButton.visible = true;
        SmithingGui.playUi(ModSounds.FORGE_READY.get(), 1.0F, 0.8F);
    }

    public void closeByServer() {
        closedByServer = true;
        onClose();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        if (stage == Stage.SELECT && button == 0) {
            if (VanillaGui.onScrollbar(mouseX, mouseY, scrollbarX(), listTop, listRows * ROW_HEIGHT) && maxScroll() > 0) {
                draggingScrollbar = true;
                scrollFromMouse(mouseY);
                return true;
            }
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                selectedIndex = index;
                select(filtered.get(index));
                return true;
            }
        }
        if (stage == Stage.PLAYING && button == 0 && inside(mouseX, mouseY) && !helpButton.isMouseOver(mouseX, mouseY)) {
            act();
            return true;
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean actionKey = key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        boolean fresh = !actionKey || heldKeys.add(key);
        if (stage == Stage.PLAYING && actionKey) {
            if (fresh) act();
            return true;
        }
        if (stage == Stage.SELECT && !filtered.isEmpty()) {
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                int amount = key == GLFW.GLFW_KEY_DOWN ? 1 : key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_PAGE_DOWN ? listRows : -listRows;
                selectedIndex = Mth.clamp(selectedIndex + amount, 0, filtered.size() - 1);
                revealSelection();
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && fresh) {
                select(filtered.get(selectedIndex));
                return true;
            }
        }
        // Outside the minigame an action key never presses a focused button by accident.
        if (actionKey && stage != Stage.RESULT && getFocused() instanceof Button) return true;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected boolean panelScrolled(double mouseX, double mouseY, double delta) {
        if (stage == Stage.SELECT) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
            return true;
        }
        return super.panelScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected boolean panelDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (stage == Stage.SELECT && button == 0 && draggingScrollbar) {
            scrollFromMouse(mouseY);
            return true;
        }
        return super.panelDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    protected boolean panelReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.panelReleased(mouseX, mouseY, button);
    }

    private void select(OpenForgeScreenPacket.Entry entry) {
        selected = entry.result();
        stage = Stage.WAITING;
        search.visible = false;
        setFocused(null);
        layout();
        ModNetwork.CHANNEL.sendToServer(new SelectForgeRecipePacket(offer.sessionId(), entry.recipeId()));
        SmithingGui.playUi(ModSounds.TONGS_GRAB.get(), 1.0F, 0.6F);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        heldKeys.remove(key);
        return super.keyReleased(key, scanCode, modifiers);
    }

    private void act() {
        if (run == null || run.isComplete()) return;
        int now = elapsed();
        if (now < 0 || now > allowedMs) return;
        int phase = run.index();
        float accuracy = run.press(now);
        if (accuracy < 0) {
            // Between passes: nothing is scored or sent, but the player learns why nothing happened.
            earlyMs = Util.getMillis();
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new ForgeActionPacket(offer.sessionId(), phase, now));
        phaseResults.add(accuracy);
        lastAccuracy = accuracy;
        lastFeedbackMs = Util.getMillis();
        SmithingGui.playUi(ModSounds.FORGE_WORK.get(), 0.9F + accuracy * 0.3F, 0.8F);
        // A stab into the forge; vanilla broadcasts the swing, so other players see it too.
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.swing(minecraft.player.getMainHandItem().is(ModTags.TONGS) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
    }

    private int elapsed() {
        return (int) (Util.getMillis() - clientStartMs);
    }

    private boolean inside(double x, double y) {
        return x >= left && x < left + panelWidth && y >= top && y < top + panelHeight;
    }

    private int listX() {
        return left + 8;
    }

    private int listWidth() {
        return panelWidth - 16 - 16;
    }

    private int scrollbarX() {
        return listX() + listWidth() + 2;
    }

    private int rowAt(double x, double y) {
        if (x < listX() || x >= listX() + listWidth() || y < listTop || y >= listTop + listRows * ROW_HEIGHT) return -1;
        int offset = (int) (y - listTop);
        if (offset % ROW_HEIGHT >= ROW_INNER) return -1;
        int index = scroll + offset / ROW_HEIGHT;
        return index < filtered.size() ? index : -1;
    }

    private void scrollFromMouse(double mouseY) {
        float ratio = (float) ((mouseY - listTop - 7) / Math.max(1, listRows * ROW_HEIGHT - 15));
        scroll = Mth.clamp(Math.round(ratio * maxScroll()), 0, maxScroll());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        if (stage == Stage.READY && elapsed() >= 0) stage = Stage.PLAYING;
        if (search != null) search.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (stage != Stage.RESULT && !closedByServer) {
            ModNetwork.CHANNEL.sendToServer(new CancelSessionPacket(offer.sessionId()));
        }
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = Component.translatable("screen.immersive_smithing.forge.title", offer.familyName());
        Component units = Component.translatable("screen.immersive_smithing.forge.molten", MaterialUnits.describe(offer.moltenUnits()));
        int titleWidth = panelWidth - 40 - font.width(units);
        g.drawString(font, VanillaGui.clipped(font, title, titleWidth), left + 8, top + 7, VanillaGui.LABEL, false);
        g.drawString(font, units, left + panelWidth - 26 - font.width(units), top + 7, VanillaGui.LABEL_DIM, false);

        switch (stage) {
            case SELECT -> renderSelection(g, mouseX, mouseY);
            case WAITING -> renderWaiting(g);
            case READY -> renderReady(g, mouseX, mouseY);
            case PLAYING -> renderPlaying(g);
            case RESULT -> renderResult(g);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (VanillaGui.isClipped(font, title, titleWidth) && mouseX >= left + 8 && mouseX < left + 8 + titleWidth
                && mouseY >= top + 5 && mouseY < top + 17) {
            tooltip(title);
        }
    }

    private void renderSelection(GuiGraphics g, int mouseX, int mouseY) {
        int x = listX();
        int w = listWidth();
        int listHeight = listRows * ROW_HEIGHT;
        VanillaGui.inset(g, x - 1, listTop - 1, w + 2, listHeight + 1);
        if (filtered.isEmpty()) {
            VanillaGui.wrappedCentered(g, font, Component.translatable(offer.entries().isEmpty()
                            ? "screen.immersive_smithing.forge.nothing_offered" : "screen.immersive_smithing.forge.no_match"),
                    x + w / 2, listTop + listHeight / 2 - 8, w - 16, VanillaGui.LABEL, false);
        }
        int hovered = rowAt(mouseX, mouseY);
        scissor(g, x, listTop, x + w, listTop + listHeight);
        for (int row = 0; row < listRows; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            OpenForgeScreenPacket.Entry e = filtered.get(index);
            int ry = listTop + row * ROW_HEIGHT;
            boolean highlighted = hovered == index || (index == selectedIndex && hovered < 0);
            VanillaGui.row(g, x, ry, w, ROW_INNER, true, highlighted);
            g.renderItem(e.result(), x + 4, ry + 4);
            int shownAux = Math.min(4, e.auxiliary().size());
            int auxSpace = shownAux * 17 + (e.auxiliary().size() > shownAux ? 14 : 0);
            int textWidth = Math.max(40, w - 30 - auxSpace - 6);
            String marker = index == selectedIndex ? "» " : "";
            g.drawString(font, marker + VanillaGui.clipped(font, e.result().getHoverName(), textWidth - font.width(marker)),
                    x + 24, ry + 3, VanillaGui.ROW_TEXT, true);
            Component cost = Component.translatable("screen.immersive_smithing.forge.cost", MaterialUnits.describe(e.metalUnits()),
                    MaterialUnits.describe(offer.moltenUnits() - e.metalUnits()));
            g.drawString(font, VanillaGui.clipped(font, cost, textWidth), x + 24, ry + 13, VanillaGui.ROW_TEXT_DIM, true);
            int ax = x + w - 20;
            for (int i = 0; i < shownAux; i++) {
                ItemStack aux = e.auxiliary().get(i);
                g.renderItem(aux, ax, ry + 4);
                g.renderItemDecorations(font, aux, ax, ry + 4);
                ax -= 17;
            }
            if (e.auxiliary().size() > shownAux) {
                Component more = Component.translatable("screen.immersive_smithing.forge.more_components", e.auxiliary().size() - shownAux);
                g.drawString(font, more, ax + 16 - font.width(more), ry + 8, VanillaGui.ROW_TEXT, true);
            }
        }
        g.disableScissor();
        VanillaGui.scrollbar(g, scrollbarX(), listTop - 1, listHeight + 1, maxScroll() == 0 ? 0F : scroll / (float) maxScroll(), maxScroll() > 0);
        Component hint = Component.translatable("screen.immersive_smithing.forge.choose", offer.timeMs() / 1000);
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, hint, panelWidth - 16)), left + panelWidth / 2,
                top + panelHeight - 14, VanillaGui.LABEL_DIM);
        if (hovered >= 0) rowTooltip(filtered.get(hovered), mouseX);
    }

    private void rowTooltip(OpenForgeScreenPacket.Entry e, int mouseX) {
        int x = listX();
        if (mouseX < x + 22) {
            tooltip(e.result());
            return;
        }
        int ax = x + listWidth() - 20;
        for (int i = 0; i < Math.min(4, e.auxiliary().size()); i++) {
            if (mouseX >= ax && mouseX < ax + 16) {
                tooltip(e.auxiliary().get(i));
                return;
            }
            ax -= 17;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(e.result().getHoverName());
        lines.add(Component.translatable("screen.immersive_smithing.forge.cost", MaterialUnits.describe(e.metalUnits()),
                MaterialUnits.describe(offer.moltenUnits() - e.metalUnits())).withStyle(ChatFormatting.GRAY));
        if (!e.auxiliary().isEmpty()) {
            lines.add(Component.translatable("screen.immersive_smithing.forge.components").withStyle(ChatFormatting.GOLD));
            for (ItemStack auxiliary : e.auxiliary()) {
                lines.add(Component.translatable("screen.immersive_smithing.forge.component_line", auxiliary.getCount(),
                        auxiliary.getHoverName()).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip(lines);
    }

    private void renderWorkpiece(GuiGraphics g, int cx, int cy, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx - 8 * scale, cy - 8 * scale, 0);
        g.pose().scale(scale, scale, 1F);
        g.renderItem(selected, 0, 0);
        g.pose().popPose();
    }

    /** The workpiece in a large slot. */
    private void framedWorkpiece(GuiGraphics g, int cx, int cy) {
        VanillaGui.inset(g, cx - 21, cy - 21, 42, 42);
        renderWorkpiece(g, cx, cy, 2F);
    }

    private void renderWaiting(GuiGraphics g) {
        int cx = left + panelWidth / 2;
        framedWorkpiece(g, cx, top + panelHeight / 2 - 26);
        VanillaGui.centered(g, font, Component.translatable("screen.immersive_smithing.preparing"), cx, top + panelHeight / 2 + 4, VanillaGui.LABEL);
        VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.forge.preparing_detail"),
                cx, top + panelHeight / 2 + 18, panelWidth - 40, VanillaGui.LABEL_DIM, false);
    }

    private void renderReady(GuiGraphics g, int mouseX, int mouseY) {
        int remaining = -elapsed();
        int cx = left + panelWidth / 2;
        framedWorkpiece(g, cx, top + 50);
        Component name = selected.getHoverName();
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, name, panelWidth - 30)), cx, top + 78, VanillaGui.LABEL);
        if (VanillaGui.isClipped(font, name, panelWidth - 30) && Math.abs(mouseX - cx) < (panelWidth - 30) / 2
                && mouseY >= top + 76 && mouseY < top + 88) tooltip(name);
        Component ready = Component.translatable("screen.immersive_smithing.ready", Math.max(1, (remaining + 999) / 1000));
        VanillaGui.display(g, cx - 60, top + 94, 120, 18);
        VanillaGui.centered(g, font, ready, cx, top + 99, VanillaGui.DISPLAY_TEXT);
        int y = top + 122;
        y += 10 * VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.forge.instructions"),
                cx, y, panelWidth - 32, VanillaGui.LABEL, false) + 6;
        VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.forge.durability_explainer"),
                cx, y, panelWidth - 32, VanillaGui.LABEL_DIM, false);
    }

    private void renderPlaying(GuiGraphics g) {
        int now = elapsed();
        float remaining = 1F - now / (float) Math.max(1, allowedMs);
        int x = left + 8;
        int w = panelWidth - 16;
        g.drawString(font, Component.translatable("screen.immersive_smithing.forge.step"), x, top + 22, VanillaGui.LABEL, false);
        Component time = Component.translatable("screen.immersive_smithing.time_remaining", Math.max(0, (allowedMs - now + 999) / 1000));
        g.drawString(font, time, x + w - font.width(time), top + 22, VanillaGui.LABEL, false);
        SmithingGui.timeBar(g, x + 1, top + 33, w - 2, 5, remaining);

        framedWorkpiece(g, x + 21, top + 66);
        int phaseCount = run.phases().size();
        int shown = Math.min(run.index() + 1, phaseCount);
        int infoX = x + 50;
        g.drawString(font, Component.translatable("screen.immersive_smithing.forge.phase", shown, phaseCount), infoX, top + 46, VanillaGui.LABEL, false);
        int cell = Math.min(24, (w - 50) / Math.max(1, phaseCount));
        for (int i = 0; i < phaseCount; i++) {
            int px = infoX + i * cell;
            VanillaGui.inset(g, px, top + 58, cell - 3, 14);
            if (i < phaseResults.size()) g.fill(px + 1, top + 59, px + cell - 4, top + 71, SmithingGui.ratingColor(phaseResults.get(i)));
            if (cell >= 10) {
                Component number = Component.literal(Integer.toString(i + 1));
                g.drawString(font, number, px + (cell - 3 - font.width(number)) / 2, top + 61, i < phaseResults.size() ? 0xFF000000 : VanillaGui.LABEL, false);
            }
            if (i == run.index()) g.renderOutline(px - 1, top + 57, cell - 1, 16, 0xFF000000);
        }
        if (!phaseResults.isEmpty()) {
            float sum = 0;
            for (float f : phaseResults) sum += f;
            int projected = Math.round(sum / phaseCount * 100F);
            // Quality colours (white Standard, aqua Fine) need the dark display to read.
            VanillaGui.display(g, infoX, top + 76, w - 50, 14);
            g.drawString(font, VanillaGui.clipped(font, Component.translatable("screen.immersive_smithing.projected",
                    SmithingQuality.fromScore(projected).displayName().getString()), w - 58), infoX + 4, top + 79, VanillaGui.DISPLAY_TEXT, false);
        }

        // Track, in a dark display so the zone colours stay clear.
        float scale = SmithingGui.markerScale();
        int trackH = Math.round(16 * scale);
        int displayTop = top + 98;
        int displayHeight = trackH + 42;
        VanillaGui.display(g, x, displayTop, w, displayHeight);
        int trackWidth = w - 24;
        int tx = x + 12;
        int ty = displayTop + 10;
        g.fill(tx, ty, tx + trackWidth, ty + trackH, 0xFF3A3A3A);
        for (int i = 0; i <= 12; i++) {
            int tickX = tx + i * (trackWidth - 1) / 12;
            g.fill(tickX, ty + trackH - (i % 3 == 0 ? 5 : 3), tickX + 1, ty + trackH, 0xFF8B8B8B);
        }
        if (run.isComplete() || now > allowedMs) {
            VanillaGui.centered(g, font, Component.translatable(now > allowedMs ? "screen.immersive_smithing.times_up" : "screen.immersive_smithing.finishing"),
                    left + panelWidth / 2, ty + trackH / 2 - 4, VanillaGui.DISPLAY_TEXT);
        } else {
            ForgeMinigame.Phase phase = run.phases().get(run.index());
            int zoneL = tx + Math.round((phase.center() - phase.halfWidth()) * trackWidth);
            int zoneR = tx + Math.round((phase.center() + phase.halfWidth()) * trackWidth);
            int perfL = tx + Math.round((phase.center() - phase.halfWidth() * 0.2F) * trackWidth);
            int perfR = tx + Math.round((phase.center() + phase.halfWidth() * 0.2F) * trackWidth);
            if (ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES)) g.fill(zoneL - 1, ty - 1, zoneR + 1, ty + trackH + 1, 0xFF000000);
            g.fill(zoneL, ty, zoneR, ty + trackH, SmithingGui.zoneColor());
            g.fill(perfL, ty, Math.max(perfR, perfL + 1), ty + trackH, SmithingGui.perfectColor());
            // Brackets and a centre notch make the timing zone readable without relying on hue.
            g.fill(zoneL, ty - 3, zoneR, ty - 2, VanillaGui.DISPLAY_TEXT);
            g.fill(zoneL, ty - 3, zoneL + 1, ty, VanillaGui.DISPLAY_TEXT);
            g.fill(zoneR - 1, ty - 3, zoneR, ty, VanillaGui.DISPLAY_TEXT);
            int centre = tx + Math.round(phase.center() * trackWidth);
            g.fill(centre - 2, ty + trackH + 3, centre + 3, ty + trackH + 5, SmithingGui.perfectColor());

            int local = now - run.phaseStartMs();
            if (local >= 0) {
                float position = ForgeMinigame.position(phase, local / 1000F);
                int mx = tx + Math.round(position * trackWidth);
                int half = Math.max(1, Math.round(1.5F * scale));
                g.fill(mx - half - 1, ty - 5, mx + half + 1, ty + trackH + 5,
                        ClientConfig.get(ClientConfig.HIGH_CONTRAST_MINIGAMES) ? 0xFFFFFFFF : 0xFF090D10);
                g.fill(mx - half, ty - 4, mx + half, ty + trackH + 4, SmithingGui.markerColor());
                g.fill(mx - half - 2, ty - 6, mx + half + 2, ty - 4, SmithingGui.markerColor());
                boolean inZone = Math.abs(position - phase.center()) <= phase.halfWidth();
                if (inZone && !markerInZone) SmithingGui.playCue();
                markerInZone = inZone;
            } else {
                markerInZone = false;
            }
        }

        long wall = Util.getMillis();
        int labelMs = ClientConfig.get(ClientConfig.FEEDBACK_LABEL_MS);
        boolean early = earlyMs > lastFeedbackMs && wall - earlyMs < labelMs;
        boolean feedback = !early && lastAccuracy >= 0 && wall - lastFeedbackMs < labelMs;
        Component label = early ? Component.translatable("screen.immersive_smithing.forge.too_early")
                : feedback ? SmithingGui.rating(lastAccuracy) : Component.translatable("screen.immersive_smithing.forge.target_label");
        VanillaGui.centered(g, font, Component.literal(font.plainSubstrByWidth(label.getString(), w - 16)), left + panelWidth / 2,
                displayTop + displayHeight - 16, early ? 0xFFFFD66B : feedback ? SmithingGui.ratingColor(lastAccuracy) : VanillaGui.DISPLAY_TEXT);
        VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.forge.instructions"),
                left + panelWidth / 2, displayTop + displayHeight + 6, w - 8, VanillaGui.LABEL_DIM, false);
    }

    private void renderResult(GuiGraphics g) {
        int cx = left + panelWidth / 2;
        framedWorkpiece(g, cx, top + 50);
        Component headline = Component.translatable(resultTimedOut ? "screen.immersive_smithing.forge.timeout" : "screen.immersive_smithing.forge.complete");
        VanillaGui.centered(g, font, headline, cx, top + 80, resultTimedOut ? 0xFFAA0000 : VanillaGui.LABEL);
        Component quality = SmithingQuality.fromScore(resultScore).displayName();
        if (ClientConfig.get(ClientConfig.SHOW_NUMERIC_QUALITY_SCORES)) quality = Component.translatable("screen.immersive_smithing.quality_with_score", quality, resultScore);
        VanillaGui.display(g, left + 16, top + 94, panelWidth - 32, 20);
        VanillaGui.centered(g, font, Component.translatable("screen.immersive_smithing.forge.durability", quality), cx, top + 100, VanillaGui.DISPLAY_TEXT);
        VanillaGui.wrappedCentered(g, font, Component.translatable("screen.immersive_smithing.forge.next"), cx, top + 124,
                panelWidth - 40, VanillaGui.LABEL_DIM, false);
    }
}
