package worldradio.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import worldradio.WorldRadio;
import worldradio.item.GuideBook;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The guide book: the tuner's teak case around one sheet of paper (textures/gui/book.png, made by tools/MakeBook.java),
 * the blocks' tabs on the left and the topics' on the right, piano keys to turn the pages and the page number below.
 * The screen wraps the chapters' sections and breaks them into pages itself: a section that does not fit on the page
 * starts the next one, so no text can run off the page. Turned with the tabs, the keys, the arrow and page keys or
 * the mouse wheel.
 */
public class GuideBookScreen extends Screen {
    private static final Identifier PANEL = WorldRadio.id("textures/gui/book.png");
    private static final int PANEL_W = 232, PANEL_H = 230;
    private static final int TAB_W = 26, TAB_H = 24, TAB_GAP = 3;
    private static final int PAGE_MARGIN = 8, TEXT_MARGIN = 6, HEADER_H = 20, FOOTER_H = 26;
    private static final int TEXT_W = PANEL_W - 2 * PAGE_MARGIN - 2 * TEXT_MARGIN;
    private static final int CONTENT_H = PANEL_H - 2 * PAGE_MARGIN - HEADER_H - FOOTER_H;
    private static final int SLOT_SIZE = 18, PICTURE = 96;
    private static final int RECIPE_W = 3 * SLOT_SIZE + 6 + 13 + 6 + SLOT_SIZE; // grid, arrow, result

    /** Ink on the paper. */
    private static final int TITLE = 0xFF4A2A12, HEADING = 0xFFB45A14, BODY = 0xFF2A2018, SOFT = 0xFF7A6A52;
    private static final int RULE = 0xFFD9C9A2, SLOT = 0xFFE4D6B4, SLOT_EDGE = 0xFFB8A67C, ARROW = 0xFF8A6A30;

    private static final Set<String> LOGGED = new HashSet<>();
    /** The page open when the book was last closed; this session only. */
    private static int lastPage;

    private final List<GuideBook.Chapter> chapters = GuideBook.chapters();
    /** Laid out in {@link #init}: every page with its chapter and its sections, each with its wrapped lines. */
    private final List<Page> pages = new ArrayList<>();
    private final List<TabKey> tabs = new ArrayList<>();
    private int page;
    private int left, top;
    private KeyButton previous, next;
    private ItemStack hovered = ItemStack.EMPTY;
    private int mouseX, mouseY;

    private record Placed(GuideBook.Section section, List<FormattedCharSequence> lines, int height) {
    }

    private record Page(int chapter, List<Placed> sections) {
    }

    public GuideBookScreen() {
        this(lastPage);
    }

    public GuideBookScreen(int page) {
        super(Component.translatable("item.worldradio.guide_book"));
        this.page = Math.max(0, page);
    }

    /** Known once the screen is open: the pages depend on the font and the language. */
    public int pageCount() {
        return pages.size();
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = Math.max(2, (height - PANEL_H) / 2);
        layOut();
        page = Math.min(page, pages.size() - 1);
        tabs.clear();
        int blocks = 0, topics = 0;
        for (int i = 0; i < chapters.size(); i++) {
            boolean right = !chapters.get(i).block();
            int row = right ? topics++ : blocks++;
            tabs.add(addRenderableWidget(new TabKey(right ? left + PANEL_W - 3 : left - TAB_W + 3,
                    top + 8 + row * (TAB_H + TAB_GAP), i)));
        }
        int buttonY = top + PANEL_H - FOOTER_H + 4;
        previous = addRenderableWidget(new KeyButton(left + PAGE_MARGIN, buttonY, 22, 18, Component.literal("<"),
                button -> turnTo(page - 1)));
        next = addRenderableWidget(new KeyButton(left + PANEL_W - PAGE_MARGIN - 22, buttonY, 22, 18,
                Component.literal(">"), button -> turnTo(page + 1)));
        updateButtons();
    }

    /**
     * Breaks every chapter into pages: sections go onto a page until the next one would not fit. A heading never ends
     * a page: it moves over together with what follows it.
     */
    private void layOut() {
        pages.clear();
        StringBuilder count = new StringBuilder();
        for (int c = 0; c < chapters.size(); c++) {
            int first = pages.size();
            List<Placed> current = new ArrayList<>();
            int used = 0;
            List<GuideBook.Section> sections = chapters.get(c).sections();
            for (int i = 0; i < sections.size(); i++) {
                Placed placed = place(sections.get(i));
                int needed = placed.height();
                for (int j = i; j + 1 < sections.size() && sections.get(j) instanceof GuideBook.Heading; j++) {
                    needed += place(sections.get(j + 1)).height();
                }
                boolean turn = sections.get(i) instanceof GuideBook.Break || used + needed > CONTENT_H;
                if (turn && !current.isEmpty()) {
                    pages.add(new Page(c, current));
                    current = new ArrayList<>();
                    used = 0;
                }
                if (placed.height() > CONTENT_H) {
                    WorldRadio.LOGGER.warn("BOOK OVERFLOW {} section {}: {} of {} pixels", chapters.get(c).title(), i,
                            placed.height(), CONTENT_H);
                }
                current.add(placed);
                used += placed.height();
            }
            pages.add(new Page(c, current));
            count.append(' ').append(chapters.get(c).title()).append('=').append(pages.size() - first);
        }
        String language = minecraft.getLanguageManager().getSelected();
        if (FabricLoader.getInstance().isDevelopmentEnvironment() && LOGGED.add(language)) {
            WorldRadio.LOGGER.info("BOOK PAGES {}: {} pages,{}", language, pages.size(), count);
            for (String key : GuideBook.keys()) {
                if (!Language.getInstance().has(key)) WorldRadio.LOGGER.error("BOOK KEY MISSING {}: {}", language, key);
            }
        }
    }

    private Placed place(GuideBook.Section section) {
        return switch (section) {
            case GuideBook.Heading heading -> text(section, Component.translatable(heading.key()), 10, 5);
            case GuideBook.Text text -> text(section, Component.translatable(text.key(), text.args()), 9, 5);
            case GuideBook.Announcement line -> text(section, Component.translatable(line.key())
                    .withStyle(s -> s.withItalic(true)), 9, 5);
            case GuideBook.ItemLine line -> {
                List<FormattedCharSequence> lines = font.split(Component.translatable(line.key()),
                        TEXT_W - line.icons().size() * SLOT_SIZE - 2);
                yield new Placed(section, lines, Math.max(17, lines.size() * 9 + 1) + 4);
            }
            case GuideBook.Recipe recipe -> new Placed(section, List.of(), 3 * SLOT_SIZE + 4);
            case GuideBook.Picture picture -> new Placed(section, List.of(), PICTURE + 4);
            case GuideBook.Break turn -> new Placed(section, List.of(), 0);
        };
    }

    private Placed text(GuideBook.Section section, Component text, int lineHeight, int gap) {
        List<FormattedCharSequence> lines = font.split(text, TEXT_W);
        return new Placed(section, lines, lines.size() * lineHeight + gap);
    }

    private int firstPageOf(int chapter) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).chapter() == chapter) {
                return i;
            }
        }
        return 0;
    }

    /** Also used by the dev screenshots to go through the pages. */
    public void turnTo(int target) {
        int clamped = Math.clamp(target, 0, pages.size() - 1);
        if (clamped != page) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
        }
        page = clamped;
        lastPage = page;
        updateButtons();
    }

    /** The open chapter's tab stays pressed, like the open tab of the station screen. */
    private void updateButtons() {
        previous.active = page > 0;
        next.active = page < pages.size() - 1;
        for (TabKey tab : tabs) tab.active = pages.get(page).chapter() != tab.chapter;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turnTo(page - 1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turnTo(page + 1);
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turnTo(page + (scrollY > 0 ? -1 : 1));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        RadioUi.panel(graphics, PANEL, left, top, PANEL_W, PANEL_H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hovered = ItemStack.EMPTY;
        Page current = pages.get(page);
        GuideBook.Chapter chapter = chapters.get(current.chapter());
        int x = left + PAGE_MARGIN + TEXT_MARGIN;
        int y = top + PAGE_MARGIN + 3;
        graphics.item(chapter.icon(), x, y);
        graphics.text(font, Component.translatable(chapter.title()), x + 20, y + 4, TITLE, false);
        graphics.fill(x, y + HEADER_H - 4, x + TEXT_W, y + HEADER_H - 3, RULE);

        int cursorY = top + PAGE_MARGIN + HEADER_H + 2;
        for (Placed placed : current.sections()) {
            List<FormattedCharSequence> lines = placed.lines();
            switch (placed.section()) {
                case GuideBook.Heading heading -> lines(graphics, lines, x, cursorY + 3, 10, HEADING);
                case GuideBook.Text text -> lines(graphics, lines, x, cursorY, 9, BODY);
                case GuideBook.Announcement line -> lines(graphics, lines, x, cursorY, 9, SOFT);
                case GuideBook.ItemLine line -> {
                    for (int i = 0; i < line.icons().size(); i++) {
                        item(graphics, line.icons().get(i), x + i * SLOT_SIZE, cursorY);
                    }
                    lines(graphics, lines, x + line.icons().size() * SLOT_SIZE + 2, cursorY + (lines.size() == 1 ? 4 : 0),
                            9, BODY);
                }
                case GuideBook.Recipe recipe -> recipe(graphics, recipe, x + (TEXT_W - RECIPE_W) / 2, cursorY);
                case GuideBook.Picture picture -> graphics.blit(RenderPipelines.GUI_TEXTURED, picture.texture(),
                        x + (TEXT_W - PICTURE) / 2, cursorY, 0, 0, PICTURE, PICTURE, PICTURE, PICTURE);
                case GuideBook.Break turn -> {
                }
            }
            cursorY += placed.height();
        }
        graphics.centeredText(font, Component.literal((page + 1) + " / " + pages.size()), left + PANEL_W / 2,
                top + PANEL_H - FOOTER_H + 9, RadioUi.BODY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- pieces

    private void lines(GuiGraphicsExtractor graphics, List<FormattedCharSequence> lines, int x, int y, int lineHeight,
                       int colour) {
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x, y, colour, false);
            y += lineHeight;
        }
    }

    /** A crafting grid, always three by three, an arrow and the result. */
    private void recipe(GuiGraphicsExtractor graphics, GuideBook.Recipe recipe, int x, int y) {
        for (int i = 0; i < 9; i++) {
            slot(graphics, recipe.grid().get(i), x + i % 3 * SLOT_SIZE, y + i / 3 * SLOT_SIZE);
        }
        int arrow = x + 3 * SLOT_SIZE + 6, mid = y + 3 * SLOT_SIZE / 2;
        graphics.fill(arrow, mid - 1, arrow + 9, mid + 1, ARROW);
        for (int i = 0; i < 4; i++) {
            graphics.fill(arrow + 9 + i, mid - 4 + i, arrow + 10 + i, mid + 4 - i, ARROW);
        }
        slot(graphics, recipe.result(), arrow + 13 + 6, mid - SLOT_SIZE / 2);
    }

    private void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT);
        item(graphics, stack, x + 1, y + 1);
    }

    private void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        graphics.item(stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            hovered = stack;
        }
    }

    /** A chapter tab: the chapter's icon on a piano key, the title as tooltip. */
    private class TabKey extends KeyButton {
        private final int chapter;

        TabKey(int x, int y, int chapter) {
            super(x, y, TAB_W, TAB_H, Component.translatable(chapters.get(chapter).title()),
                    button -> turnTo(firstPageOf(chapter)));
            this.chapter = chapter;
            tooltip(getMessage());
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            RadioUi.key(graphics, getX(), getY(), width, height, active, isHoveredOrFocused());
            graphics.item(chapters.get(chapter).icon(), getX() + 5, getY() + 4);
        }
    }
}
