package worldradio.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import worldradio.Config;
import worldradio.WorldRadio;

/**
 * The look of the radio and amplifier screens: a teak case with a black glass dial, speaker cloth behind the lists and
 * white piano keys (the "walnut-teak" design made by tools/MakeGuiTextures.java), its colours and small helpers.
 */
public final class RadioUi {
    /** Station name and titles: gold on the black glass. */
    public static final int TEXT = 0xFFF1D9A0;
    /** Soft text on the glass and the cloth: range line, stream state, hints. */
    public static final int TEXT_SOFT = 0xFFB89A62;
    public static final int LIST_TEXT = 0xFFF3E9D2;
    public static final int LIST_SOFT = 0xFFB8A07A;
    /** A station that plays. */
    public static final int ACCENT = 0xFF9FE08A;
    /** The row of the station this radio is tuned to. */
    public static final int MARKED = 0xFFFFD36A;
    public static final int WARN = 0xFFFFB347;
    public static final int ERROR = 0xFFFF7A6A;
    public static final int ROW_ALT = 0x12FFFFFF;
    public static final int ROW_HOVER = 0x30FFE0A0;
    public static final int KEY_TEXT = 0xFF1A1A1A;
    public static final int KEY_TEXT_OFF = 0xFF6A665E;
    public static final int SCROLL_TRACK = 0x40000000;
    public static final int SCROLL_THUMB = 0xFFC8A050;

    public static final Identifier RADIO_PANEL = WorldRadio.id("textures/gui/radio.png");
    public static final Identifier AMPLIFIER_PANEL = WorldRadio.id("textures/gui/amplifier.png");
    static final Identifier KEY = WorldRadio.id("key");
    static final Identifier KEY_HIGHLIGHTED = WorldRadio.id("key_highlighted");
    static final Identifier KEY_DISABLED = WorldRadio.id("key_disabled");
    private static final int HINT_WIDTH = 220;
    /** Where the last range line was drawn (GUI pixels); dev screenshots hover it. */
    public static int lastRangeX = -1;
    public static int lastRangeY = -1;

    private RadioUi() {
    }

    /** The screen's background texture, drawn 1:1 (the textures have the screens' exact sizes). */
    public static void panel(GuiGraphicsExtractor g, Identifier texture, int x, int y, int width, int height) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, width, height, width, height);
    }

    /** A piano key (nine-slice sprite): up, lit while the mouse is on it, pressed while inactive (the open tab). */
    static void key(GuiGraphicsExtractor g, int x, int y, int width, int height, boolean active, boolean lit) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, !active ? KEY_DISABLED : lit ? KEY_HIGHLIGHTED : KEY, x, y, width, height);
    }

    /** "Range 56 blocks (3 antenna blocks)". */
    public static Component range(int range, int antenna) {
        if (antenna <= 0) return Component.translatable("worldradio.range.none", range);
        if (antenna == 1) return Component.translatable("worldradio.range.one", range);
        return Component.translatable("worldradio.range", range, antenna);
    }

    /**
     * Draws the range line with a "(?)" after it; while the mouse is over the line, a tooltip says how to extend the
     * range. The step comes from this client's config, which is the server's own in single player.
     */
    public static void rangeLine(GuiGraphicsExtractor g, Font font, int range, int antenna, int x, int y, int mouseX, int mouseY) {
        String text = range(range, antenna).getString() + "  (?)";
        g.text(font, text, x, y, TEXT_SOFT, false);
        lastRangeX = x;
        lastRangeY = y;
        if (mouseX >= x && mouseX < x + font.width(text) && mouseY >= y - 2 && mouseY < y + 10) {
            Component hint = Component.translatable("worldradio.range.hint", Config.get().antennaStep());
            // the default positioner puts the box 12 right of and 12 above the point: this lands it under the line
            g.setTooltipForNextFrame(font, font.split(hint, HINT_WIDTH), x - 12 + 4, y + 12 + 14);
        }
    }

    /** Cuts the text to the width with an ellipsis. */
    public static String ellipsize(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String dots = "…";
        int keep = text.length();
        while (keep > 0 && font.width(text.substring(0, keep)) + font.width(dots) > width) keep--;
        return text.substring(0, keep) + dots;
    }
}
