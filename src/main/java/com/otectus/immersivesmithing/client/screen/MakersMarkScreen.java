package com.otectus.immersivesmithing.client.screen;

import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.SignWorkPacket;
import com.otectus.immersivesmithing.quality.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Maker's Mark on a vanilla container panel: a title and a short inscription for a piece the player just forged
 * (or brought back to the anvil), in vanilla text fields with a character count each. Purely an input form; the
 * server validates the slot, the smith and every string before writing. Room is made for the server's whole line
 * limit (up to six) within 320 x 240.
 */
public class MakersMarkScreen extends WorkshopScreen {
    private static final int FIELD_STEP = 20;

    private final int slot;
    private final ItemStack piece;
    private String titleText;
    private final List<String> lineText = new ArrayList<>();

    private EditBox titleBox;
    private final List<EditBox> lineBoxes = new ArrayList<>();
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int maxLines;

    public MakersMarkScreen(int slot, ItemStack piece) {
        super(Component.translatable("screen.immersive_smithing.makers_mark.title"), 320, 240);
        this.slot = slot;
        this.piece = piece;
        this.titleText = MakersMark.title(piece);
        this.lineText.addAll(MakersMark.inscription(piece));
    }

    @Override
    protected void initPanel() {
        lineBoxes.clear();
        maxLines = Mth.clamp(ServerConfig.get(ServerConfig.MAX_INSCRIPTION_LINES), 0, 6);
        panelWidth = Math.min(260, canvasWidth - 12);
        panelHeight = 72 + (maxLines > 0 ? maxLines * FIELD_STEP + 4 : 0) + 32;
        left = (canvasWidth - panelWidth) / 2;
        top = Math.max(2, (canvasHeight - panelHeight) / 2);

        titleBox = VanillaGui.editBox(font, left + 30, top + 42, panelWidth - 38, Component.translatable("screen.immersive_smithing.makers_mark.name"));
        titleBox.setMaxLength(ServerConfig.get(ServerConfig.MAX_TITLE_LENGTH));
        titleBox.setHint(Component.translatable("screen.immersive_smithing.makers_mark.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        titleBox.setValue(titleText);
        titleBox.setResponder(s -> titleText = s);
        addRenderableWidget(titleBox);

        int y = top + 76;
        for (int i = 0; i < maxLines; i++) {
            final int index = i;
            EditBox box = VanillaGui.editBox(font, left + 8, y + i * FIELD_STEP, panelWidth - 16,
                    Component.translatable("screen.immersive_smithing.makers_mark.line_hint", i + 1));
            box.setMaxLength(ServerConfig.get(ServerConfig.MAX_INSCRIPTION_LINE_LENGTH));
            box.setHint(Component.translatable("screen.immersive_smithing.makers_mark.line_hint", i + 1).withStyle(ChatFormatting.DARK_GRAY));
            box.setValue(index < lineText.size() ? lineText.get(index) : "");
            box.setResponder(s -> {
                while (lineText.size() <= index) lineText.add("");
                lineText.set(index, s);
            });
            addRenderableWidget(box);
            lineBoxes.add(box);
        }

        int buttonY = top + panelHeight - 28;
        int buttonWidth = (panelWidth - 24) / 2;
        addRenderableWidget(VanillaGui.button(left + 8, buttonY, buttonWidth, 20,
                Component.translatable("screen.immersive_smithing.makers_mark.sign"), b -> sign()));
        addRenderableWidget(VanillaGui.button(left + panelWidth - 8 - buttonWidth, buttonY, buttonWidth, 20,
                Component.translatable("screen.immersive_smithing.makers_mark.skip"), b -> onClose()));
        setInitialFocus(titleBox);
    }

    private void sign() {
        List<String> lines = new ArrayList<>();
        for (EditBox box : lineBoxes) lines.add(box.getValue());
        ModNetwork.CHANNEL.sendToServer(new SignWorkPacket(slot, titleBox.getValue(), lines));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            sign();
            return true;
        }
        return super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public void tick() {
        titleBox.tick();
        lineBoxes.forEach(EditBox::tick);
    }

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaGui.panel(g, left, top, panelWidth, panelHeight);
        g.drawString(font, VanillaGui.clipped(font, title, panelWidth - 16), left + 8, top + 7, VanillaGui.LABEL, false);
        String smith = minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "";
        Component subtitle = Component.translatable("screen.immersive_smithing.makers_mark.subtitle", smith);
        g.drawString(font, VanillaGui.clipped(font, subtitle, panelWidth - 16), left + 8, top + 18, VanillaGui.LABEL_DIM, false);

        VanillaGui.slot(g, left + 7, top + 41);
        g.renderItem(piece, left + 8, top + 42);
        g.drawString(font, Component.translatable("screen.immersive_smithing.makers_mark.name"), left + 30, top + 32, VanillaGui.LABEL, false);
        counter(g, titleBox, left + panelWidth - 8, top + 32);
        if (maxLines > 0) {
            g.drawString(font, Component.translatable("screen.immersive_smithing.makers_mark.inscription"), left + 8, top + 66, VanillaGui.LABEL, false);
            EditBox focused = lineBoxes.stream().filter(EditBox::isFocused).findFirst().orElse(lineBoxes.get(0));
            counter(g, focused, left + panelWidth - 8, top + 66);
        }
        super.renderPanel(g, mouseX, mouseY, partialTick);
        if (mouseX >= left + 7 && mouseX < left + 25 && mouseY >= top + 41 && mouseY < top + 59) tooltip(piece);
    }

    /** "12/32", turning dark red at the limit so the cap is never a surprise. */
    private void counter(GuiGraphics g, EditBox box, int right, int y) {
        int max = box == titleBox ? ServerConfig.get(ServerConfig.MAX_TITLE_LENGTH) : ServerConfig.get(ServerConfig.MAX_INSCRIPTION_LINE_LENGTH);
        int length = box.getValue().length();
        Component text = Component.translatable("screen.immersive_smithing.makers_mark.count", length, max);
        g.drawString(font, text, right - font.width(text), y, length >= max ? 0xFFAA0000 : VanillaGui.LABEL_DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
