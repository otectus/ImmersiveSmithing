package com.otectus.immersivesmithing.client.screen;

import com.otectus.immersivesmithing.guide.GuideData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Smithing Guide as a vanilla book: Minecraft's own book page with black ink, "Page x of y" and the vanilla page
 * buttons. The contents (a search field and the chapter list) sit on a container panel beside the book, the way the
 * recipe book sits beside a crafting table; on a narrow window they take turns, switched by a button. Translated
 * paragraphs may start with "## " for a heading.
 */
public class GuideScreen extends WorkshopScreen {
    private static final int LINE_HEIGHT = 9;
    private static final int TEXT_WIDTH = 114;
    private static final int TEXT_LINES = 14;
    private static final int INDEX_WIDTH = 150;
    private static final int ROW_HEIGHT = 18;
    private static int lastChapter = 1;
    private static int lastPage;

    private record Line(FormattedCharSequence text, boolean heading, boolean blank) {}
    private final List<List<Line>> pages = new ArrayList<>();
    private final List<Integer> matches = new ArrayList<>();
    private final List<Button> chapterButtons = new ArrayList<>();
    private int chapter;
    private int page;
    private int listScroll;
    private int bookLeft, bookTop, indexLeft, indexTop, indexHeight;
    private boolean compact;
    private boolean contentsOpen;
    private String query = "";
    private EditBox search;
    private PageButton prev, next;

    public GuideScreen(int chapter) {
        super(Component.translatable("item.immersive_smithing.smithing_guide"), 320, 240);
        this.chapter = chapter > 0 ? Mth.clamp(chapter, 1, GuideData.chapterCount()) : lastChapter;
        this.page = chapter > 0 ? 0 : lastPage;
    }

    @Override
    protected void initPanel() {
        compact = canvasWidth < INDEX_WIDTH + 6 + VanillaGui.BOOK_WIDTH + 12;
        int groupWidth = compact ? VanillaGui.BOOK_WIDTH : INDEX_WIDTH + 6 + VanillaGui.BOOK_WIDTH;
        int groupLeft = (canvasWidth - groupWidth) / 2;
        int groupTop = Math.max(2, (canvasHeight - VanillaGui.BOOK_HEIGHT - 26) / 2);
        bookLeft = compact ? groupLeft : groupLeft + INDEX_WIDTH + 6;
        bookTop = groupTop;
        indexLeft = groupLeft;
        indexTop = groupTop;
        indexHeight = VanillaGui.BOOK_HEIGHT;

        int buttonsY = bookTop + VanillaGui.BOOK_HEIGHT + 4;
        if (compact) {
            addRenderableWidget(VanillaGui.button(bookLeft, buttonsY, 94, 20,
                    Component.translatable(contentsOpen ? "screen.immersive_smithing.guide.read" : "screen.immersive_smithing.guide.contents"), b -> {
                        contentsOpen = !contentsOpen;
                        rebuildWidgets();
                    }));
            addRenderableWidget(VanillaGui.button(bookLeft + VanillaGui.BOOK_WIDTH - 94, buttonsY, 94, 20, Component.translatable("gui.done"), b -> onClose()));
        } else {
            addRenderableWidget(VanillaGui.button(bookLeft + (VanillaGui.BOOK_WIDTH - 98) / 2, buttonsY, 98, 20, Component.translatable("gui.done"), b -> onClose()));
        }
        search = VanillaGui.editBox(font, indexLeft + 8, indexTop + 18, INDEX_WIDTH - 16, Component.translatable("screen.immersive_smithing.guide.search"));
        search.setMaxLength(80);
        search.setValue(query);
        search.setHint(Component.translatable("screen.immersive_smithing.guide.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(value -> {
            query = value;
            listScroll = 0;
            filterChapters();
            rebuildChapters();
        });
        search.visible = indexVisible();
        addRenderableWidget(search);
        prev = addRenderableWidget(new PageButton(bookLeft + 43, bookTop + 159, false, b -> turn(-1), true));
        next = addRenderableWidget(new PageButton(bookLeft + 116, bookTop + 159, true, b -> turn(1), true));
        prev.setTooltip(Tooltip.create(Component.translatable("screen.immersive_smithing.guide.page_keys")));
        next.setTooltip(Tooltip.create(Component.translatable("screen.immersive_smithing.guide.page_keys")));
        paginate();
        filterChapters();
        revealChapter();
        rebuildChapters();
    }

    private boolean bookVisible() { return !compact || !contentsOpen; }
    private boolean indexVisible() { return !compact || contentsOpen; }
    private int visibleChapters() { return Math.max(1, (indexHeight - 18 - 22 - 20) / ROW_HEIGHT); }
    private int listTop() { return indexTop + 42; }
    private int listWidth() { return INDEX_WIDTH - 16 - (matches.size() > visibleChapters() ? 16 : 0); }

    private void filterChapters() {
        matches.clear();
        String needle = query.strip().toLowerCase(Locale.ROOT);
        for (int i = 1; i <= GuideData.chapterCount(); i++) {
            String haystack = GuideData.chapterTitle(i).getString() + " " + Component.translatable(GuideData.textKey(i)).getString();
            if (haystack.toLowerCase(Locale.ROOT).contains(needle)) matches.add(i);
        }
    }

    private void revealChapter() {
        int index = matches.indexOf(chapter);
        if (index >= 0 && index < listScroll) listScroll = index;
        if (index >= listScroll + visibleChapters()) listScroll = index - visibleChapters() + 1;
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, matches.size() - visibleChapters()));
    }

    private void rebuildChapters() {
        chapterButtons.forEach(this::removeWidget);
        chapterButtons.clear();
        if (!indexVisible()) return;
        int width = listWidth();
        for (int row = 0; row < visibleChapters() && row + listScroll < matches.size(); row++) {
            int index = matches.get(row + listScroll);
            Component label = Component.translatable("screen.immersive_smithing.guide.chapter", index, GuideData.chapterTitle(index));
            Button button = new Button(indexLeft + 8, listTop() + row * ROW_HEIGHT, width, ROW_HEIGHT - 1, label,
                    b -> selectChapter(index), narration -> narration.get()) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float partialTick) {
                    boolean current = index == chapter;
                    VanillaGui.row(g, getX(), getY(), getWidth(), getHeight(), true, isHoveredOrFocused() || current);
                    // The open chapter is marked with a sign as well as the highlight.
                    String marker = current ? "» " : "";
                    g.drawString(font, marker + VanillaGui.clipped(font, getMessage(), getWidth() - 10 - font.width(marker)),
                            getX() + 5, getY() + (getHeight() - 8) / 2, current ? 0xFFFFFFA0 : VanillaGui.ROW_TEXT, true);
                }
            };
            if (VanillaGui.isClipped(font, label, width - 14)) button.setTooltip(Tooltip.create(label));
            chapterButtons.add(addRenderableWidget(button));
        }
    }

    private void selectChapter(int index) {
        chapter = index;
        page = 0;
        contentsOpen = false;
        remember();
        rebuildWidgets();
    }

    private void paginate() {
        pages.clear();
        List<Line> current = new ArrayList<>();
        // The chapter title opens the first page. It is not a section heading: the text simply flows on below it.
        for (FormattedCharSequence line : font.split(GuideData.chapterTitle(chapter).copy().withStyle(ChatFormatting.BOLD), TEXT_WIDTH)) {
            current.add(new Line(line, false, false));
        }
        int titleLines = current.size();
        current.add(new Line(FormattedCharSequence.EMPTY, false, true));
        String raw = Component.translatable(GuideData.textKey(chapter)).getString();
        for (String paragraph : raw.split("\n")) {
            boolean heading = paragraph.startsWith("## ");
            boolean blank = paragraph.isBlank();
            Component text = Component.literal(heading ? paragraph.substring(3) : paragraph);
            if (heading) text = text.copy().withStyle(ChatFormatting.BOLD);
            List<FormattedCharSequence> wrapped = blank ? List.of(FormattedCharSequence.EMPTY) : font.split(text, TEXT_WIDTH);
            // Keep short paragraphs together, and never orphan a heading at the bottom of a page.
            int needed = wrapped.size() + (heading ? 2 : 0);
            boolean onlyTitle = pages.isEmpty() && current.size() <= titleLines + 1;
            if (!current.isEmpty() && !onlyTitle && needed <= TEXT_LINES && current.size() + needed > TEXT_LINES) {
                int carryFrom = current.size();
                while (carryFrom > 0 && current.get(carryFrom - 1).blank()) carryFrom--;
                int headingEnd = carryFrom;
                while (carryFrom > 0 && current.get(carryFrom - 1).heading()) carryFrom--;
                List<Line> carry = carryFrom < headingEnd ? new ArrayList<>(current.subList(carryFrom, current.size())) : new ArrayList<>();
                if (!carry.isEmpty()) current.subList(carryFrom, current.size()).clear();
                addPage(current);
                current = carry;
            }
            for (FormattedCharSequence line : wrapped) {
                if (current.size() == TEXT_LINES) {
                    addPage(current);
                    current = new ArrayList<>();
                }
                if (blank && current.isEmpty()) continue;
                current.add(new Line(line, heading, blank));
            }
        }
        if (!current.isEmpty()) addPage(current);
        if (pages.isEmpty()) pages.add(List.of());
        page = Mth.clamp(page, 0, pages.size() - 1);
        prev.visible = next.visible = bookVisible();
        prev.active = page > 0 || chapter > 1;
        next.active = page < pages.size() - 1 || chapter < GuideData.chapterCount();
        prev.visible &= prev.active;
        next.visible &= next.active;
    }

    private void addPage(List<Line> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).blank()) lines.remove(lines.size() - 1);
        if (!lines.isEmpty()) pages.add(List.copyOf(lines));
    }

    private void turn(int delta) {
        int target = page + delta;
        if (target < 0 && chapter > 1) {
            chapter--;
            page = Integer.MAX_VALUE;
        } else if (target >= pages.size() && chapter < GuideData.chapterCount()) {
            chapter++;
            page = 0;
        } else page = Mth.clamp(target, 0, pages.size() - 1);
        rebuildWidgets();
        remember();
    }

    private void remember() { lastChapter = chapter; lastPage = page; }

    @Override
    protected boolean panelScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        if (indexVisible() && x >= indexLeft && x < indexLeft + INDEX_WIDTH && y >= indexTop && y < indexTop + indexHeight) {
            listScroll = Mth.clamp(listScroll - (int) Math.signum(delta), 0, Math.max(0, matches.size() - visibleChapters()));
            rebuildChapters();
            return true;
        }
        if (bookVisible() && x >= bookLeft && x < bookLeft + VanillaGui.BOOK_WIDTH && y >= bookTop && y < bookTop + VanillaGui.BOOK_HEIGHT) {
            turn(delta > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (search.isFocused()) return super.keyPressed(key, scanCode, modifiers);
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            if (bookVisible()) turn(key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            selectChapter(Mth.clamp(chapter + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1), 1, GuideData.chapterCount()));
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
            selectChapter(key == GLFW.GLFW_KEY_HOME ? 1 : GuideData.chapterCount());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void tick() { search.tick(); }

    @Override
    protected void renderPanel(GuiGraphics g, int mx, int my, float partialTick) {
        if (indexVisible()) renderIndex(g);
        if (bookVisible()) renderBook(g);
        super.renderPanel(g, mx, my, partialTick);
    }

    private void renderIndex(GuiGraphics g) {
        VanillaGui.panel(g, indexLeft, indexTop, INDEX_WIDTH, indexHeight);
        g.drawString(font, VanillaGui.clipped(font, title, INDEX_WIDTH - 16), indexLeft + 8, indexTop + 6, VanillaGui.LABEL, false);
        if (matches.isEmpty()) {
            VanillaGui.wrapped(g, font, Component.translatable("screen.immersive_smithing.guide.no_results"),
                    indexLeft + 10, listTop() + 4, INDEX_WIDTH - 20, VanillaGui.LABEL, 6);
        }
        if (matches.size() > visibleChapters()) {
            int track = visibleChapters() * ROW_HEIGHT - 1;
            VanillaGui.scrollbar(g, indexLeft + INDEX_WIDTH - 22, listTop(), track,
                    listScroll / (float) Math.max(1, matches.size() - visibleChapters()), true);
        }
        Component count = Component.translatable("screen.immersive_smithing.guide.topics", matches.size());
        g.drawString(font, count, indexLeft + 8, indexTop + indexHeight - 14, VanillaGui.LABEL_DIM, false);
    }

    private void renderBook(GuiGraphics g) {
        VanillaGui.bookPage(g, bookLeft, bookTop);
        Component indicator = Component.translatable("book.pageIndicator", page + 1, pages.size());
        g.drawString(font, indicator, bookLeft + 148 - font.width(indicator), bookTop + 16, VanillaGui.INK, false);
        Component chapterLabel = Component.translatable("screen.immersive_smithing.guide.chapter_short", chapter);
        if (font.width(chapterLabel) + font.width(indicator) + 6 <= TEXT_WIDTH) {
            g.drawString(font, chapterLabel, bookLeft + 36, bookTop + 16, VanillaGui.INK_DIM, false);
        }
        List<Line> lines = pages.get(page);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i).text(), bookLeft + 36, bookTop + 30 + i * LINE_HEIGHT, VanillaGui.INK, false);
        }
    }

    /** The page buttons, for the client pack test (vanilla page buttons carry no text to find them by). */
    public Button pageButton(boolean forward) { return forward ? next : prev; }

    @Override
    public void removed() { remember(); super.removed(); }

    @Override
    public boolean isPauseScreen() { return false; }
}
