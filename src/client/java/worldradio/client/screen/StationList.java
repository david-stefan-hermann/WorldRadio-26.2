package worldradio.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A scrolling list of text rows (left text, right text, click action) inside a fixed rectangle; the screen's background
 * texture draws the cloth and frame behind it. A row with a rename action shows a pencil at its right end while the
 * mouse is on the row.
 */
public final class StationList {
    public static final int ROW = 12;
    /** Width of the pencil area at the right end of a row. */
    private static final int PENCIL = 14;

    public record Row(String left, String right, boolean marked, Runnable action, Runnable rename) {
        public Row(String left, String right, boolean marked, Runnable action) {
            this(left, right, marked, action, null);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int x;
    private int y;
    private int width;
    private int height;
    private int scroll;
    private String empty = "";

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    /** Replaces the rows; the scroll position stays where it can. */
    public void setRows(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        clampScroll();
    }

    public void resetScroll() {
        scroll = 0;
    }

    /** Text shown when the list is empty (loading, error, "no favorites yet"). */
    public void setEmptyText(String text) {
        empty = text;
    }

    public int size() {
        return rows.size();
    }

    /** The names of the marked rows (dev tests). */
    public String markedRows() {
        return rows.stream().filter(Row::marked).map(Row::left).toList().toString();
    }

    public int x() {
        return x;
    }

    public int width() {
        return width;
    }

    /** Top edge of a row, or -1 when it is scrolled out of sight. */
    public int rowTop(int index) {
        int top = y + (index - scroll) * ROW;
        return index < scroll || top + ROW > y + height ? -1 : top;
    }

    public void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (rows.isEmpty()) {
            g.textWithWordWrap(font, Component.literal(empty), x + 6, y + 6, width - 12, RadioUi.LIST_SOFT, false);
            return;
        }
        g.enableScissor(x, y, x + width, y + height);
        int visible = height / ROW + 1;
        for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) {
            Row row = rows.get(i);
            int top = y + (i - scroll) * ROW;
            boolean hover = mouseX >= x && mouseX < x + width - 6 && mouseY >= top && mouseY < top + ROW && mouseY < y + height;
            if (hover) g.fill(x, top, x + width - 6, top + ROW, RadioUi.ROW_HOVER);
            else if (i % 2 == 1) g.fill(x, top, x + width - 6, top + ROW, RadioUi.ROW_ALT);
            boolean pencil = hover && row.rename() != null;
            String right = pencil ? "" : row.right();
            int rightWidth = right.isEmpty() ? 0 : font.width(right) + 8;
            if (pencil) rightWidth = PENCIL;
            String left = (row.marked() ? "★ " : "") + row.left();
            g.text(font, RadioUi.ellipsize(font, left, width - 16 - rightWidth), x + 4, top + 2,
                    row.marked() ? RadioUi.MARKED : RadioUi.LIST_TEXT, false);
            if (pencil) {
                boolean onPencil = mouseX >= x + width - 6 - PENCIL;
                g.text(font, "✎", x + width - 6 - PENCIL + 3, top + 2, onPencil ? RadioUi.MARKED : RadioUi.LIST_SOFT, false);
                if (onPencil) g.setTooltipForNextFrame(font, Component.translatable("worldradio.favorite.rename"), mouseX, mouseY);
            } else if (rightWidth > 0) {
                g.text(font, right, x + width - 8 - rightWidth + 4, top + 2, RadioUi.LIST_SOFT, false);
            }
        }
        g.disableScissor();
        int max = maxScroll();
        if (max > 0) {
            int barHeight = Math.max(12, height * height / (rows.size() * ROW));
            int barTop = y + (height - barHeight) * scroll / max;
            g.fill(x + width - 5, y, x + width - 1, y + height, RadioUi.SCROLL_TRACK);
            g.fill(x + width - 5, barTop, x + width - 1, barTop + barHeight, RadioUi.SCROLL_THUMB);
        }
    }

    public boolean click(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width - 6 || mouseY < y || mouseY >= y + height) return false;
        int i = scroll + (int) ((mouseY - y) / ROW);
        if (i < 0 || i >= rows.size()) return false;
        Row row = rows.get(i);
        Runnable action = row.rename() != null && mouseX >= x + width - 6 - PENCIL ? row.rename() : row.action();
        if (action != null) action.run();
        return true;
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        scroll -= (int) Math.signum(amount) * 3;
        clampScroll();
        return true;
    }

    private int maxScroll() {
        return Math.max(0, rows.size() - height / ROW);
    }

    private void clampScroll() {
        scroll = Math.clamp(scroll, 0, maxScroll());
    }
}
