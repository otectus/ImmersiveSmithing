package com.otectus.immersivesmithing.client.screen;

import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.CancelSessionPacket;
import com.otectus.immersivesmithing.network.packet.CastIngotsPacket;
import com.otectus.immersivesmithing.network.packet.CastResultPacket;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.network.packet.SelectForgeRecipePacket;
import com.otectus.immersivesmithing.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The forge's pattern book: a compact planning aid, not the work. Craftable patterns first, with search (a word starting
 * with {@code @} matches a mod, as in JEI), category tabs, favourites (starred, kept on this client) and "Repeat last".
 * Patterns that cannot be made now are dimmed with the exact reason. Choosing one pins it beside the billet tray and the
 * book folds away; nothing is reserved until the smith commits at the tray. The Cast panel turns free molten metal back
 * into ingots or nuggets. Fully usable from the keyboard: arrows choose, Enter pins, F stars, Tab switches the category.
 */
public class PatternBookScreen extends WorkshopScreen {
    private enum Category {
        ALL, WEAPONS, TOOLS, ARMOR, OTHER;

        Component label() {
            return Component.translatable("screen.immersive_smithing.book.tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final int ROW_HEIGHT = 26;
    private static final int ROW_INNER = 24;
    private static final int CAST_ROW = 18;
    private static final long STATUS_MS = 4000L;
    private static final int REASON_TEXT = 0xFFFFB4A0;
    private static final int STAR = 0xFFFFD040;

    private final OpenForgeScreenPacket offer;
    private List<OpenForgeScreenPacket.Entry> entries;
    private List<OpenForgeScreenPacket.CastOption> castOptions;
    private int moltenUnits;
    private final List<CastRow> castRows = new ArrayList<>();
    private CastRow activeRow;
    private int castSequence;
    private boolean castPending;
    private Component status = Component.empty();
    private boolean statusFailure;
    private long statusMs = Long.MIN_VALUE;
    private final List<OpenForgeScreenPacket.Entry> filtered = new ArrayList<>();
    private Category category = Category.ALL;
    private final List<Button> tabs = new ArrayList<>();
    private EditBox search;
    private Button repeatButton;
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
    private boolean waiting;
    private boolean closedByServer;

    public PatternBookScreen(OpenForgeScreenPacket offer) {
        super(Component.translatable("screen.immersive_smithing.book"), 320, 240);
        this.offer = offer;
        this.entries = offer.entries();
        this.castOptions = offer.castOptions();
        this.moltenUnits = offer.moltenUnits();
        syncCastRows();
    }

    /** One line of the Cast panel: the ingots, or the nuggets for a remainder. */
    private final class CastRow {
        final boolean nuggets;
        int choice;
        int count = 1;
        Button prev, next, minus, plus, all, cast;

        CastRow(boolean nuggets) {
            this.nuggets = nuggets;
        }

        List<OpenForgeScreenPacket.CastOption> options() {
            return castOptions.stream().filter(o -> (o.unitsEach() == 1) == nuggets).toList();
        }

        OpenForgeScreenPacket.CastOption option() {
            List<OpenForgeScreenPacket.CastOption> options = options();
            return options.get(Mth.clamp(choice, 0, options.size() - 1));
        }

        int max() {
            return option().max();
        }

        void clamp() {
            choice = Mth.clamp(choice, 0, Math.max(0, options().size() - 1));
            count = Mth.clamp(count, 1, Math.max(1, max()));
        }
    }

    private boolean syncCastRows() {
        List<CastRow> next = new ArrayList<>();
        for (boolean nuggets : new boolean[]{false, true}) {
            if (castOptions.stream().noneMatch(o -> (o.unitsEach() == 1) == nuggets)) continue;
            CastRow row = castRows.stream().filter(r -> r.nuggets == nuggets).findFirst().orElseGet(() -> new CastRow(nuggets));
            row.clamp();
            next.add(row);
        }
        boolean changed = next.size() != castRows.size() || !castRows.containsAll(next);
        castRows.clear();
        castRows.addAll(next);
        if (activeRow == null || !castRows.contains(activeRow)) activeRow = castRows.isEmpty() ? null : castRows.get(0);
        return changed;
    }

    public int sessionId() {
        return offer.sessionId();
    }

    @Override
    protected void initPanel() {
        panelWidth = Math.min(340, canvasWidth - 12);
        String previous = search != null ? search.getValue() : "";
        search = VanillaGui.editBox(font, 0, 0, panelWidth - 16 - 72, Component.translatable("screen.immersive_smithing.forge.search"));
        search.setHint(Component.translatable("screen.immersive_smithing.forge.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(previous);
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        repeatButton = addRenderableWidget(VanillaGui.button(0, 0, 70, 16, Component.translatable("screen.immersive_smithing.book.repeat"), b -> repeatLast()));
        repeatButton.setTooltip(Tooltip.create(Component.translatable("screen.immersive_smithing.book.repeat_help")));
        helpButton = VanillaGui.button(0, 0, 16, 14, Component.literal("?"), b -> {});
        helpButton.setTooltip(Tooltip.create(help()));
        addRenderableWidget(helpButton);
        tabs.clear();
        for (Category c : Category.values()) {
            Button tab = addRenderableWidget(VanillaGui.button(0, 0, 10, 14, c.label(), b -> {
                category = c;
                refilter();
            }));
            tabs.add(tab);
        }
        for (CastRow row : castRows) {
            row.prev = addRenderableWidget(VanillaGui.button(0, 0, 12, 16, Component.literal("<"), b -> cycle(row, -1)));
            row.next = addRenderableWidget(VanillaGui.button(0, 0, 12, 16, Component.literal(">"), b -> cycle(row, 1)));
            row.minus = addRenderableWidget(VanillaGui.button(0, 0, 14, 16, Component.literal("-"), b -> step(row, hasShiftDown() ? -10 : -1)));
            row.plus = addRenderableWidget(VanillaGui.button(0, 0, 14, 16, Component.literal("+"), b -> step(row, hasShiftDown() ? 10 : 1)));
            row.all = addRenderableWidget(VanillaGui.button(0, 0, 26, 16, Component.translatable("screen.immersive_smithing.forge.cast.all"), b -> {
                activeRow = row;
                row.count = Math.max(1, row.max());
            }));
            row.cast = addRenderableWidget(VanillaGui.button(0, 0, 40, 16, Component.translatable("screen.immersive_smithing.forge.cast.button"), b -> cast(row)));
        }
        layout();
        refilter();
        setInitialFocus(search);
    }

    private void layout() {
        panelHeight = Math.min(250, canvasHeight - 4);
        left = (canvasWidth - panelWidth) / 2;
        top = (canvasHeight - panelHeight) / 2;
        int castHeight = castRows.isEmpty() ? 0 : castRows.size() * CAST_ROW + 14;
        search.setPosition(left + 8, top + 22);
        repeatButton.setPosition(left + panelWidth - 8 - 70, top + 22);
        repeatButton.active = !lastPattern().isEmpty() && entries.stream().anyMatch(e -> e.recipeId().toString().equals(lastPattern()));
        helpButton.setPosition(left + panelWidth - 22, top + 4);
        int tabY = top + 41;
        int tabX = left + 8;
        int tabWidth = (panelWidth - 16 - (tabs.size() - 1) * 2) / Math.max(1, tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            Button tab = tabs.get(i);
            tab.setWidth(tabWidth);
            tab.setPosition(tabX + i * (tabWidth + 2), tabY);
            tab.active = Category.values()[i] != category;
        }
        int castTop = tabY + 18;
        listTop = castTop + castHeight;
        listRows = Math.max(castRows.isEmpty() ? 3 : 2, (top + panelHeight - 22 - listTop) / ROW_HEIGHT);
        int right = left + panelWidth - 8;
        for (int i = 0; i < castRows.size(); i++) {
            CastRow row = castRows.get(i);
            int y = castTop + i * CAST_ROW;
            row.cast.setPosition(right - 40, y);
            row.all.setPosition(right - 40 - 2 - 26, y);
            row.plus.setPosition(right - 68 - 4 - 14, y);
            row.minus.setPosition(right - 86 - 28 - 14, y);
            row.next.setPosition(right - 128 - 4 - 12, y);
            row.prev.setPosition(right - 144 - 1 - 12, y);
            boolean several = row.options().size() > 1;
            row.prev.visible = row.next.visible = several;
        }
    }

    private int castTop() {
        return top + 59;
    }

    private Component help() {
        Component help = Component.translatable("screen.immersive_smithing.book.help").append("\n\n")
                .append(Component.translatable("jei.immersive_smithing.new_equipment"));
        return castRows.isEmpty() ? help : help.copy().append("\n\n").append(Component.translatable("screen.immersive_smithing.forge.help_cast"));
    }

    private static String lastPattern() {
        return ClientConfig.SPEC.isLoaded() ? ClientConfig.LAST_PATTERN.get() : "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> favorites() {
        return ClientConfig.SPEC.isLoaded() ? (List<String>) ClientConfig.FAVORITE_PATTERNS.get() : List.of();
    }

    private static boolean favorite(OpenForgeScreenPacket.Entry e) {
        return favorites().contains(e.recipeId().toString());
    }

    private void toggleFavorite(OpenForgeScreenPacket.Entry e) {
        if (!ClientConfig.SPEC.isLoaded()) return;
        List<String> next = new ArrayList<>(favorites());
        String id = e.recipeId().toString();
        if (!next.remove(id)) next.add(id);
        ClientConfig.FAVORITE_PATTERNS.set(next);
        ClientConfig.SPEC.save();
        refilter();
    }

    private static Category categoryOf(ItemStack stack) {
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.ARMOR) return Category.ARMOR;
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof TridentItem || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem) return Category.WEAPONS;
        if (stack.getItem() instanceof DiggerItem || stack.getItem() instanceof ShearsItem) return Category.TOOLS;
        boolean damage = !stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).isEmpty();
        return damage ? Category.WEAPONS : Category.OTHER;
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
        List<OpenForgeScreenPacket.Entry> starred = new ArrayList<>();
        for (OpenForgeScreenPacket.Entry e : entries) {
            if (category != Category.ALL && categoryOf(e.result()) != category) continue;
            String name = e.result().getHoverName().getString().toLowerCase(Locale.ROOT);
            if (!words.stream().allMatch(name::contains) || !mods.stream().allMatch(mod -> fromMod(e.result(), mod))) continue;
            if (favorite(e)) starred.add(e);
            else filtered.add(e);
        }
        // Craftable first (the server already sorts that way), favourites ahead of the rest within each group.
        starred.sort((a, b) -> Boolean.compare(!a.selectable(), !b.selectable()));
        filtered.addAll(0, starred);
        filtered.sort((a, b) -> Boolean.compare(!a.selectable(), !b.selectable()));
        scroll = Mth.clamp(scroll, 0, maxScroll());
        selectedIndex = Mth.clamp(selectedIndex, 0, Math.max(0, filtered.size() - 1));
        if (!tabs.isEmpty()) layout();
    }

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

    public void closeByServer() {
        closedByServer = true;
        onClose();
    }

    /** The server's answer to a cast or to a refused choice: the new metal, rows and cast options. */
    public void onCastResult(CastResultPacket packet) {
        castPending = false;
        waiting = false;
        moltenUnits = packet.moltenUnits();
        if (packet.closed()) {
            closeByServer();
            return;
        }
        entries = packet.entries();
        castOptions = packet.castOptions();
        boolean failed = packet.cast().isEmpty();
        setStatus(packet.message(), failed);
        if (failed) SmithingGui.playUi(ModSounds.ACTION_FAIL.get(), 1.0F, 0.6F);
        if (syncCastRows()) rebuildWidgets();
        else layout();
        refilter();
    }

    private void setStatus(Component message, boolean failure) {
        status = message;
        statusFailure = failure;
        statusMs = Util.getMillis();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean panelClicked(double mouseX, double mouseY, int button) {
        if (VanillaGui.onScrollbar(mouseX, mouseY, scrollbarX(), listTop, listRows * ROW_HEIGHT) && maxScroll() > 0 && button == 0) {
            draggingScrollbar = true;
            scrollFromMouse(mouseY);
            return true;
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            selectedIndex = index;
            if (button == 1 || mouseX < listX() + 12 && mouseY < listTop + (index - scroll) * ROW_HEIGHT + 10) toggleFavorite(filtered.get(index));
            else if (button == 0) select(filtered.get(index));
            return true;
        }
        CastRow iconRow = castRowAt(mouseX, mouseY);
        if (iconRow != null && button == 0) {
            activeRow = iconRow;
            if (iconRow.options().size() > 1) cycle(iconRow, 1);
            return true;
        }
        return super.panelClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB && !hasShiftDown() && getFocused() != search) {
            category = Category.values()[(category.ordinal() + 1) % Category.values().length];
            refilter();
            return true;
        }
        if (!filtered.isEmpty()) {
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                int amount = key == GLFW.GLFW_KEY_DOWN ? 1 : key == GLFW.GLFW_KEY_UP ? -1 : key == GLFW.GLFW_KEY_PAGE_DOWN ? listRows : -listRows;
                selectedIndex = Mth.clamp(selectedIndex + amount, 0, filtered.size() - 1);
                revealSelection();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                select(filtered.get(selectedIndex));
                return true;
            }
            if (key == GLFW.GLFW_KEY_F && getFocused() != search) {
                toggleFavorite(filtered.get(selectedIndex));
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    protected boolean panelScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
        return true;
    }

    @Override
    protected boolean panelDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) {
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
        if (waiting) return;
        if (!entry.selectable()) {
            // A dimmed row: say why right here; nothing is sent and the book stays open.
            setStatus(entry.reason(), true);
            SmithingGui.playUi(ModSounds.ACTION_FAIL.get(), 1.0F, 0.6F);
            return;
        }
        waiting = true;
        if (ClientConfig.SPEC.isLoaded()) {
            ClientConfig.LAST_PATTERN.set(entry.recipeId().toString());
            ClientConfig.SPEC.save();
        }
        ModNetwork.CHANNEL.sendToServer(new SelectForgeRecipePacket(offer.sessionId(), entry.recipeId()));
        SmithingGui.playUi(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.8F);
    }

    private void repeatLast() {
        String last = lastPattern();
        for (OpenForgeScreenPacket.Entry e : entries) {
            if (e.recipeId().toString().equals(last)) {
                select(e);
                return;
            }
        }
    }

    private void cycle(CastRow row, int direction) {
        int size = row.options().size();
        if (size == 0) return;
        activeRow = row;
        row.choice = Math.floorMod(row.choice + direction, size);
        row.clamp();
    }

    private void step(CastRow row, int amount) {
        activeRow = row;
        row.count = Mth.clamp(row.count + amount, 1, Math.max(1, row.max()));
    }

    private void cast(CastRow row) {
        activeRow = row;
        if (castPending || row.max() <= 0) return;
        castPending = true;
        OpenForgeScreenPacket.CastOption option = row.option();
        ModNetwork.CHANNEL.sendToServer(new CastIngotsPacket(offer.sessionId(), ++castSequence,
                BuiltInRegistries.ITEM.getKey(option.item().getItem()), Math.min(row.count, row.max())));
    }

    private CastRow castRowAt(double x, double y) {
        for (int i = 0; i < castRows.size(); i++) {
            int ry = castTop() + i * CAST_ROW;
            if (x >= left + 8 && x < left + 8 + castNameWidth() + 20 && y >= ry && y < ry + 16) return castRows.get(i);
        }
        return null;
    }

    private int castNameWidth() {
        return Math.max(30, panelWidth - 16 - 20 - 160);
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
        if (search != null) search.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!closedByServer) ModNetwork.CHANNEL.sendToServer(new CancelSessionPacket(offer.sessionId()));
        super.removed();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        VanillaGui.panel(g, left, top, panelWidth, panelHeight);
        Component title = Component.translatable("screen.immersive_smithing.book.title", offer.familyName());
        Component units = Component.translatable("screen.immersive_smithing.book.free", MaterialUnits.describe(moltenUnits));
        int unitsWidth = Math.min(font.width(units), (panelWidth - 40) / 2);
        int titleWidth = panelWidth - 40 - unitsWidth;
        g.drawString(font, VanillaGui.clipped(font, title, titleWidth), left + 8, top + 7, VanillaGui.LABEL, false);
        g.drawString(font, VanillaGui.clipped(font, units, unitsWidth), left + panelWidth - 26 - unitsWidth, top + 7, VanillaGui.LABEL_DIM, false);
        if (mouseY >= top + 5 && mouseY < top + 18 && mouseX >= left + 8 && mouseX < left + panelWidth - 26
                && (VanillaGui.isClipped(font, title, titleWidth) || VanillaGui.isClipped(font, units, unitsWidth))) {
            tooltip(List.of(title, units));
        }
        renderCastPanel(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        super.renderPanel(g, mouseX, mouseY, partialTick);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int x = listX();
        int w = listWidth();
        int listHeight = listRows * ROW_HEIGHT;
        VanillaGui.inset(g, x - 1, listTop - 1, w + 2, listHeight + 1);
        if (filtered.isEmpty()) {
            String empty = !entries.isEmpty() ? "screen.immersive_smithing.forge.no_match"
                    : castRows.isEmpty() ? "screen.immersive_smithing.forge.nothing_offered" : "screen.immersive_smithing.forge.only_casting";
            VanillaGui.wrappedCentered(g, font, Component.translatable(empty), x + w / 2, listTop + listHeight / 2 - 8, w - 16, VanillaGui.LABEL, false);
        }
        int hovered = rowAt(mouseX, mouseY);
        ResourceLocation plan = offer.plan();
        scissor(g, x, listTop, x + w, listTop + listHeight);
        for (int row = 0; row < listRows; row++) {
            int index = scroll + row;
            if (index >= filtered.size()) break;
            OpenForgeScreenPacket.Entry e = filtered.get(index);
            int ry = listTop + row * ROW_HEIGHT;
            boolean highlighted = hovered == index || (index == selectedIndex && hovered < 0);
            boolean choosable = e.selectable();
            VanillaGui.row(g, x, ry, w, ROW_INNER, choosable, highlighted);
            g.renderItem(e.result(), x + 4, ry + 4);
            if (favorite(e)) g.drawString(font, "★", x + 1, ry + 1, STAR, true);
            int shownAux = Math.min(4, e.auxiliary().size());
            int auxSpace = shownAux * 17 + (e.auxiliary().size() > shownAux ? 14 : 0);
            int textWidth = Math.max(40, w - 30 - auxSpace - 6);
            String marker = index == selectedIndex ? "» " : "";
            Component name = e.result().getHoverName();
            if (e.recipeId().equals(plan)) name = Component.translatable("screen.immersive_smithing.book.pinned", name);
            g.drawString(font, marker + VanillaGui.clipped(font, name, textWidth - font.width(marker)),
                    x + 24, ry + 3, choosable ? VanillaGui.ROW_TEXT : VanillaGui.ROW_TEXT_DISABLED, true);
            Component cost = choosable ? Component.translatable("screen.immersive_smithing.forge.cost", MaterialUnits.describe(e.metalUnits()),
                    MaterialUnits.describe(moltenUnits - e.metalUnits())) : e.reason();
            g.drawString(font, VanillaGui.clipped(font, cost, textWidth), x + 24, ry + 13, choosable ? VanillaGui.ROW_TEXT_DIM : REASON_TEXT, true);
            int ax = x + w - 20;
            for (int i = 0; i < shownAux; i++) {
                ItemStack aux = e.displayAuxiliary(i, Util.getMillis() / 1000);
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
        boolean showStatus = Util.getMillis() - statusMs < STATUS_MS && !status.getString().isEmpty();
        Component hint = showStatus ? status : Component.translatable(waiting ? "screen.immersive_smithing.book.pinning" : "screen.immersive_smithing.book.choose");
        VanillaGui.centered(g, font, Component.literal(VanillaGui.clipped(font, hint, panelWidth - 16)), left + panelWidth / 2,
                top + panelHeight - 14, showStatus ? (statusFailure ? 0xFFAA0000 : VanillaGui.LABEL) : VanillaGui.LABEL_DIM);
        if (showStatus && VanillaGui.isClipped(font, hint, panelWidth - 16) && mouseY >= top + panelHeight - 16 && mouseY < top + panelHeight - 4) {
            tooltip(hint);
        }
        if (hovered >= 0) rowTooltip(filtered.get(hovered), mouseX);
    }

    private void renderCastPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (castRows.isEmpty()) return;
        int x = left + 8;
        int nameWidth = castNameWidth();
        for (int i = 0; i < castRows.size(); i++) {
            CastRow row = castRows.get(i);
            int y = castTop() + i * CAST_ROW;
            OpenForgeScreenPacket.CastOption option = row.option();
            boolean available = row.max() > 0;
            g.renderItem(option.item(), x, y);
            g.drawString(font, VanillaGui.clipped(font, option.item().getHoverName(), nameWidth), x + 20, y + 4,
                    available ? VanillaGui.LABEL : VanillaGui.LABEL_DIM, false);
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) tooltip(option.item());
            int fieldX = row.minus.getX() + 15;
            int fieldW = row.plus.getX() - fieldX - 1;
            VanillaGui.inset(g, fieldX, y + 1, fieldW, 14);
            String count = available ? Integer.toString(Math.min(row.count, row.max())) : "-";
            g.drawString(font, count, fieldX + (fieldW - font.width(count)) / 2, y + 4, 0xFFFFFFFF, true);
            row.minus.active = available && row.count > 1;
            row.plus.active = available && row.count < row.max();
            row.all.active = available && row.count < row.max();
            row.cast.active = available && !castPending;
            if (row == activeRow) g.fill(x - 3, y + 3, x - 1, y + 13, VanillaGui.LABEL);
        }
        int previewY = castTop() + castRows.size() * CAST_ROW + 1;
        Component preview = castPreview(activeRow);
        g.drawString(font, VanillaGui.clipped(font, preview, panelWidth - 16), x, previewY, VanillaGui.LABEL_DIM, false);
        if (VanillaGui.isClipped(font, preview, panelWidth - 16) && mouseY >= previewY - 1 && mouseY < previewY + 9) tooltip(preview);
    }

    private Component castPreview(CastRow row) {
        OpenForgeScreenPacket.CastOption option = row.option();
        if (row.max() <= 0) {
            return moltenUnits < option.unitsEach()
                    ? Component.translatable("screen.immersive_smithing.forge.cast.not_enough", option.item().getHoverName())
                    : Component.translatable("screen.immersive_smithing.forge.cast.no_room");
        }
        int count = Math.min(row.count, row.max());
        int leftOver = moltenUnits - count * option.unitsEach();
        return leftOver <= 0 ? Component.translatable("screen.immersive_smithing.forge.cast.preview_empty", count, option.item().getHoverName())
                : Component.translatable("screen.immersive_smithing.forge.cast.preview", count, option.item().getHoverName(), MaterialUnits.describe(leftOver));
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
                tooltip(List.of(e.componentNames(i)));
                return;
            }
            ax -= 17;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(e.result().getHoverName());
        if (!e.selectable()) lines.add(e.reason().copy().withStyle(ChatFormatting.RED));
        lines.add(e.affordable() ? Component.translatable("screen.immersive_smithing.forge.cost", MaterialUnits.describe(e.metalUnits()),
                MaterialUnits.describe(moltenUnits - e.metalUnits())).withStyle(ChatFormatting.GRAY)
                : Component.translatable("screen.immersive_smithing.forge.costs_total", MaterialUnits.describe(e.metalUnits()),
                MaterialUnits.describe(moltenUnits)).withStyle(ChatFormatting.GRAY));
        if (!e.auxiliary().isEmpty()) {
            lines.add(Component.translatable("screen.immersive_smithing.forge.components").withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < e.auxiliary().size(); i++) {
                lines.add(Component.translatable("screen.immersive_smithing.forge.component_line", e.auxiliary().get(i).getCount(),
                        e.componentNames(i)).withStyle(ChatFormatting.GRAY));
            }
        }
        // Keep the recipe's costs and every component here. General instructions belong to the help button;
        // repeating them on every new-equipment row covered the neighbouring choices at small GUI sizes.
        if (e.upgrade()) lines.add(Component.translatable("jei.immersive_smithing.structural_upgrade").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("screen.immersive_smithing.book.star_hint").withStyle(ChatFormatting.DARK_GRAY));
        tooltip(lines);
    }

    @Override
    public Component getNarrationMessage() {
        if (filtered.isEmpty()) return super.getNarrationMessage();
        OpenForgeScreenPacket.Entry e = filtered.get(Mth.clamp(selectedIndex, 0, filtered.size() - 1));
        return Component.translatable("screen.immersive_smithing.book.narrate", e.result().getHoverName(),
                e.selectable() ? Component.translatable("screen.immersive_smithing.forge.cost", MaterialUnits.describe(e.metalUnits()),
                        MaterialUnits.describe(moltenUnits - e.metalUnits())) : e.reason());
    }
}
