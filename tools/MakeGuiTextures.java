import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipFile;

/**
 * GUI designs for the station screen (340x236) and the amplifier screen (330x190), three "old school" themes:
 * WALNUT (1950s tube radio: walnut case, ivory tuning dial, brass trim, speaker cloth, bakelite buttons), CLASSIC
 * (the vanilla container look: light grey bevels, slot-style list, vanilla buttons) and HIFI (1970s receiver: brushed
 * aluminium, walnut cheeks, a green VFD display, aluminium keys), and three variations of WALNUT the user asked for:
 * AMBER (a backlit amber dial, gold-thread cloth, ivory bakelite keys), TEAK (1960s: teak, black glass dial with a gold
 * scale, white piano keys) and MAHOGANY (brass corner fittings with rivets, a green magic-eye tuning tube, fretwork
 * behind the list, brass keys).
 *
 * <p>For each theme it writes the panel backgrounds and nine-slice button sprites (200x20, border 3) to
 * art/gui/&lt;theme&gt;/, and a preview: the textures with the real layout, text in the Minecraft font (read from the
 * loom client jar) at GUI scale 3 on top of a dev-client screenshot, to art/gui/preview/.
 * The chosen theme (TEAK, 2026-09-26) also goes into the mod: the two panels to assets/worldradio/textures/gui/ and
 * the keys as nine-slice GUI sprites (worldradio:key, key_highlighted, key_disabled).
 * Run: java tools/MakeGuiTextures.java (from the project folder).
 */
public class MakeGuiTextures {
    static final Path OUT = Path.of("art/gui");
    static final File CLIENT_JAR = new File(System.getProperty("user.home"), ".gradle/caches/fabric-loom/26.2/minecraft-client.jar");
    static final int SCALE = 3;

    // layout, copied from RadioScreen / AmplifierScreen / StationList
    static final int RW = 340, RH = 236, R_HEADER = 46;
    static final int AW = 330, AH = 190, A_HEADER = 34;
    static final int[] R_LIST = {8, 78, 324, 128};
    static final int[] A_LIST = {8, 40, 314, 142};
    static final int ROW = 12;

    /** Text colours of a theme. */
    record Colours(int title, int playing, int soft, int body, int list, int listSoft, int marked, int rowAlt, int rowHover,
                   int button, int buttonDisabled, boolean buttonShadow, boolean listShadow, boolean bodyShadow) {
    }

    interface Theme {
        String name();

        Colours colours();

        BufferedImage panel(int w, int h, int header, int[] list, boolean radio);

        /** state 0 = normal, 1 = highlighted, 2 = disabled / active tab; null = vanilla buttons. */
        BufferedImage button(int state);
    }

    public static void main(String[] args) throws IOException {
        Font font = Font.load();
        BufferedImage vanillaButton = readJar("assets/minecraft/textures/gui/sprites/widget/button.png");
        BufferedImage vanillaButtonDisabled = readJar("assets/minecraft/textures/gui/sprites/widget/button_disabled.png");
        BufferedImage vanillaButtonHover = readJar("assets/minecraft/textures/gui/sprites/widget/button_highlighted.png");
        BufferedImage radioBack = ImageIO.read(new File("run/screenshots/favourites-b.png"));
        BufferedImage ampBack = ImageIO.read(new File("run/screenshots/amplifier-screen-b.png"));
        Files.createDirectories(OUT.resolve("preview"));
        List<BufferedImage> amps = new ArrayList<>();
        for (Theme theme : new Theme[]{new Walnut(), new Classic(), new HiFi(), new Amber(), new Teak(), new Mahogany()}) {
            Path dir = OUT.resolve(theme.name());
            Files.createDirectories(dir);
            BufferedImage radioPanel = theme.panel(RW, RH, R_HEADER, R_LIST, true);
            BufferedImage ampPanel = theme.panel(AW, AH, A_HEADER, A_LIST, false);
            write(radioPanel, dir.resolve("radio_panel.png"));
            write(ampPanel, dir.resolve("amplifier_panel.png"));
            BufferedImage[] buttons = new BufferedImage[3];
            for (int s = 0; s < 3; s++) {
                BufferedImage b = theme.button(s);
                if (b == null) b = s == 0 ? vanillaButton : s == 1 ? vanillaButtonHover : vanillaButtonDisabled;
                else write(b, dir.resolve(new String[]{"button", "button_highlighted", "button_disabled"}[s] + ".png"));
                buttons[s] = b;
            }
            BufferedImage radio = radioMock(theme, radioPanel, buttons, font);
            BufferedImage amp = ampMock(theme, ampPanel, font);
            write(compose(radioBack, radio, 43, 2), OUT.resolve("preview/" + theme.name() + "-radio.png"));
            BufferedImage ampShot = compose(ampBack, amp, 48, 25);
            write(ampShot, OUT.resolve("preview/" + theme.name() + "-amplifier.png"));
            amps.add(ampShot);
        }
        // the amplifier screens side by side at half size: the first three themes, then the walnut variations
        overview(amps.subList(0, 3), OUT.resolve("preview/amplifiers.png"));
        overview(amps.subList(3, 6), OUT.resolve("preview/walnut-variants-amplifiers.png"));
        install(new Teak());
    }

    /** Writes a theme's panels and keys into the mod's resources. */
    static void install(Theme theme) throws IOException {
        Path gui = Path.of("src/main/resources/assets/worldradio/textures/gui");
        Files.createDirectories(gui.resolve("sprites"));
        write(theme.panel(RW, RH, R_HEADER, R_LIST, true), gui.resolve("radio.png"));
        write(theme.panel(AW, AH, A_HEADER, A_LIST, false), gui.resolve("amplifier.png"));
        String[] names = {"key", "key_highlighted", "key_disabled"};
        for (int s = 0; s < 3; s++) {
            write(theme.button(s), gui.resolve("sprites/" + names[s] + ".png"));
            Files.writeString(gui.resolve("sprites/" + names[s] + ".png.mcmeta"), """
                    {
                      "gui": {
                        "scaling": {
                          "type": "nine_slice",
                          "width": 200,
                          "height": 20,
                          "border": 3
                        }
                      }
                    }
                    """);
        }
    }

    static void overview(List<BufferedImage> shots, Path file) throws IOException {
        BufferedImage overview = new BufferedImage(shots.size() * 640, 360, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < shots.size(); i++) {
            overview.getGraphics().drawImage(shots.get(i).getScaledInstance(640, 360, java.awt.Image.SCALE_SMOOTH), i * 640, 0, null);
        }
        write(overview, file);
    }

    // ---------------------------------------------------------------- mock screens

    static BufferedImage radioMock(Theme t, BufferedImage panel, BufferedImage[] buttons, Font f) {
        Colours c = t.colours();
        BufferedImage g = copy(panel);
        f.draw(g, "Radio Dismuke", 8, 8, c.title(), false);
        f.draw(g, "♪ Thomas Waller – Numb Fumblin'", 8, 20, c.playing(), false);
        f.draw(g, "Range 96 blocks (2 antenna blocks)  (?)", 8, 32, c.soft(), false);
        button(g, buttons[0], RW - 28, 7, 20, 20, "☆", c, f, false);
        String[] tabs = {"Favourites", "Browse", "URL", "Search"};
        int tabWidth = (RW - 16 - 4 * 3) / 4;
        for (int i = 0; i < 4; i++) {
            boolean active = i == 0;
            button(g, buttons[active ? 2 : i == 2 ? 1 : 0], 8 + i * (tabWidth + 4), R_HEADER + 6, tabWidth, 20, tabs[i], c, f, active);
        }
        String[][] rows = {{"98.8 KISS FM Berlin", "DE"}, {"★ Radio Dismuke", ""}, {"Deutschlandfunk", "DE"},
                {"SomaFM Groove Salad", "US"}, {"Radio Swiss Jazz", "CH"}};
        list(g, R_LIST, rows, 2, c, f);
        button(g, buttons[0], 8, RH - 24, 60, 18, "Clear", c, f, false);
        button(g, buttons[0], 72, RH - 24, 70, 18, "Turn off", c, f, false);
        f.draw(g, "Volume: Options → Music & Sounds →", 148, RH - 23, c.body(), c.bodyShadow());
        f.draw(g, "Radio", 148, RH - 14, c.body(), c.bodyShadow());
        return g;
    }

    static BufferedImage ampMock(Theme t, BufferedImage panel, Font f) {
        Colours c = t.colours();
        BufferedImage g = copy(panel);
        f.draw(g, "Radio Amplifier", 8, 8, c.title(), false);
        f.draw(g, "Range 128 blocks (3 antenna blocks)  (?)", 8, 20, c.soft(), false);
        String count = "2 signals";
        f.draw(g, count, AW - 8 - f.width(count), 8, c.soft(), false);
        String[][] rows = {{"Kiss FM", "0, -60, 0 · 20 m · hops: 1 · 50 %"},
                {"Radio Dismuke", "40, -60, 0 · 20 m · hops: 1 · 50 %"}};
        list(g, A_LIST, rows, 1, c, f);
        return g;
    }

    static void list(BufferedImage g, int[] r, String[][] rows, int hovered, Colours c, Font f) {
        for (int i = 0; i < rows.length; i++) {
            int top = r[1] + i * ROW;
            if (i == hovered) blendRect(g, r[0], top, r[2] - 6, ROW, c.rowHover());
            else if (i % 2 == 1) blendRect(g, r[0], top, r[2] - 6, ROW, c.rowAlt());
            boolean marked = rows[i][0].startsWith("★");
            f.draw(g, rows[i][0], r[0] + 4, top + 2, marked ? c.marked() : c.list(), c.listShadow());
            if (!rows[i][1].isEmpty()) {
                int w = f.width(rows[i][1]);
                f.draw(g, rows[i][1], r[0] + r[2] - 8 - w, top + 2, c.listSoft(), c.listShadow());
            }
        }
    }

    static void button(BufferedImage g, BufferedImage sprite, int x, int y, int w, int h, String text, Colours c, Font f,
                       boolean disabled) {
        nineSlice(g, sprite, x, y, w, h, 3);
        int tx = x + (w - f.width(text)) / 2;
        f.draw(g, text, tx, y + (h - 8) / 2 + 1, disabled ? c.buttonDisabled() : c.button(), c.buttonShadow());
    }

    /** Panel scaled up 3x onto the screenshot, at the GUI position the screen uses. */
    static BufferedImage compose(BufferedImage back, BufferedImage gui, int left, int top) {
        BufferedImage out = new BufferedImage(back.getWidth(), back.getHeight(), BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(back, 0, 0, null);
        for (int y = 0; y < gui.getHeight(); y++) {
            for (int x = 0; x < gui.getWidth(); x++) {
                int p = gui.getRGB(x, y);
                if ((p >>> 24) == 0) continue;
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        int ox = (left + x) * SCALE + dx, oy = (top + y) * SCALE + dy;
                        out.setRGB(ox, oy, blend(out.getRGB(ox, oy), p));
                    }
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- theme 1: walnut tube radio

    static final class Walnut implements Theme {
        public String name() {
            return "walnut";
        }

        public Colours colours() {
            return new Colours(0x2E1E0E, 0x2F6B2F, 0x6E5638, 0xE9D9B0, 0xF0E2C0, 0xB89C6E, 0xE8B84A,
                    0x18FFE0B0, 0x40FFD890, 0xF2E6C8, 0xA89070, true, false, false);
        }

        public BufferedImage panel(int w, int h, int header, int[] list, boolean radio) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            wood(img, 0, 0, w, h, 7, false);
            bevelFrame(img, 0, 0, w, h, 0x1A0F07, 0x8A5F3A, 0x2E1C0E);
            // ivory tuning dial with a brass rim and a scale along its bottom edge
            int dx0 = 5, dy0 = 4, dx1 = w - 6, dy1 = header - 1;
            rect(img, dx0 - 1, dy0 - 1, dx1 + 1, dy1 + 1, 0x7A5E24);
            outline(img, dx0 - 1, dy0 - 1, dx1 + 1, dy1 + 1, 0xC9A24A);
            for (int y = dy0; y <= dy1; y++) {
                int c = mix(0xEFE4C2, 0xD8C898, (y - dy0) / (double) (dy1 - dy0));
                for (int x = dx0; x <= dx1; x++) set(img, x, y, c);
            }
            hLine(img, dx0, dx1, dy0, 0xFFF6DC);
            for (int x = dx0 + 4; x <= dx1 - 4; x += 4) {
                int len = (x - dx0 - 4) % 20 == 0 ? 3 : 1;
                vLine(img, x, dy1 - len, dy1 - 1, (x - dx0 - 4) % 20 == 0 ? 0x5A4630 : 0x8A7450);
            }
            int needle = dx0 + (int) ((dx1 - dx0) * 0.62);
            vLine(img, needle, dy1 - 5, dy1 - 1, 0xB03020);
            // the list sits in speaker cloth behind a brass frame
            int lx = list[0], ly = list[1], lw = list[2], lh = list[3];
            outline(img, lx - 2, ly - 2, lx + lw + 1, ly + lh + 1, 0xC9A24A);
            hLine(img, lx - 2, lx + lw + 1, ly + lh + 1, 0x7A5E24);
            vLine(img, lx + lw + 1, ly - 2, ly + lh + 1, 0x7A5E24);
            outline(img, lx - 1, ly - 1, lx + lw, ly + lh, 0x0E0804);
            for (int y = ly; y < ly + lh; y++) {
                for (int x = lx; x < lx + lw; x++) {
                    boolean warp = ((x >> 1) + (y >> 1)) % 2 == 0;
                    set(img, x, y, warp ? 0x241A11 : 0x1C140C);
                }
            }
            return img;
        }

        public BufferedImage button(int state) {
            BufferedImage img = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);
            int body = state == 1 ? 0x5A3A22 : state == 2 ? 0x2E1D11 : 0x4A2F1C;
            rect(img, 0, 0, 199, 19, body);
            if (state == 2) {
                hLine(img, 1, 198, 1, 0x1C1109);
                hLine(img, 1, 198, 18, 0x4A2F1C);
            } else {
                hLine(img, 1, 198, 1, state == 1 ? 0x8A6040 : 0x6E4A2E);
                hLine(img, 1, 198, 2, state == 1 ? 0x6A4630 : 0x563924);
                hLine(img, 1, 198, 17, 0x2A1A0E);
                hLine(img, 1, 198, 18, 0x21140A);
            }
            outline(img, 0, 0, 199, 19, state == 1 ? 0xC9A24A : 0x140C06);
            corners(img, 199, 19);
            return img;
        }
    }

    // ---------------------------------------------------------------- theme 2: classic minecraft container

    static final class Classic implements Theme {
        public String name() {
            return "classic";
        }

        public Colours colours() {
            return new Colours(0x404040, 0x1F7A1F, 0x5E5E5E, 0x404040, 0xFFFFFF, 0xE0E0E0, 0xFFFF55,
                    0x14FFFFFF, 0x50FFFFFF, 0xFFFFFF, 0xA0A0A0, true, true, false);
        }

        public BufferedImage panel(int w, int h, int header, int[] list, boolean radio) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            rect(img, 0, 0, w - 1, h - 1, 0xC6C6C6);
            // vanilla window edge: black outline with cut corners, 2 px light top/left, 2 px dark bottom/right
            outline(img, 0, 0, w - 1, h - 1, 0x000000);
            for (int i = 1; i <= 2; i++) {
                hLine(img, i, w - 1 - i, i, 0xFFFFFF);
                vLine(img, i, i, h - 1 - i, 0xFFFFFF);
                hLine(img, i, w - 1 - i, h - 1 - i, 0x555555);
                vLine(img, w - 1 - i, i, h - 1 - i, 0x555555);
            }
            set(img, w - 3, 2, 0xC6C6C6);
            set(img, 2, h - 3, 0xC6C6C6);
            corners(img, w - 1, h - 1);
            // engraved line under the header
            hLine(img, 6, w - 7, header, 0x8B8B8B);
            hLine(img, 6, w - 7, header + 1, 0xFFFFFF);
            // slot-style list
            int lx = list[0], ly = list[1], lw = list[2], lh = list[3];
            rect(img, lx, ly, lx + lw - 1, ly + lh - 1, 0x8B8B8B);
            hLine(img, lx - 1, lx + lw, ly - 1, 0x373737);
            vLine(img, lx - 1, ly - 1, ly + lh, 0x373737);
            hLine(img, lx - 1, lx + lw, ly + lh, 0xFFFFFF);
            vLine(img, lx + lw, ly - 1, ly + lh, 0xFFFFFF);
            return img;
        }

        public BufferedImage button(int state) {
            return null; // vanilla buttons
        }
    }

    // ---------------------------------------------------------------- theme 3: hi-fi receiver

    static final class HiFi implements Theme {
        public String name() {
            return "hifi";
        }

        public Colours colours() {
            return new Colours(0x9FFFE6, 0x6CFFB0, 0x3FA58C, 0x2C3034, 0xD6F2EA, 0x7FA39A, 0xFFB347,
                    0x10FFFFFF, 0x2858D6B8, 0x1A1D20, 0x4A4E52, false, false, false);
        }

        public BufferedImage panel(int w, int h, int header, int[] list, boolean radio) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Random rnd = new Random(7);
            double[] row = new double[h];
            for (int y = 0; y < h; y++) row[y] = rnd.nextGaussian() * 3.5;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double v = 0xC0 + row[y] + 3 * Math.sin(x * 0.045 + y * 1.7) + rnd.nextGaussian() * 1.2;
                    int g = (int) Math.round(Math.clamp(v, 0, 255));
                    set(img, x, y, (Math.clamp(g - 3, 0, 255) << 16) | (g << 8) | Math.clamp(g + 4, 0, 255));
                }
            }
            // walnut cheeks left and right with two screws each
            wood(img, 1, 1, 4, h - 2, 3, true);
            wood(img, w - 5, 1, 4, h - 2, 3, true);
            vLine(img, 5, 1, h - 2, 0x1A1C1E);
            vLine(img, w - 6, 1, h - 2, 0x1A1C1E);
            for (int sy : new int[]{6, h - 10}) {
                screw(img, 1, sy);
                screw(img, w - 5, sy);
            }
            outline(img, 0, 0, w - 1, h - 1, 0x101214);
            hLine(img, 6, w - 7, 1, 0xEEF0F2);
            hLine(img, 6, w - 7, h - 2, 0x7C8084);
            // green VFD display, recessed; the radio keeps room for the star key on its right
            int dx0 = 6, dy0 = 4, dx1 = radio ? w - 33 : w - 8, dy1 = header - 2;
            hLine(img, dx0 - 1, dx1 + 1, dy0 - 1, 0x2C3134);
            vLine(img, dx0 - 1, dy0 - 1, dy1 + 1, 0x2C3134);
            hLine(img, dx0 - 1, dx1 + 1, dy1 + 1, 0xE4E7EA);
            vLine(img, dx1 + 1, dy0 - 1, dy1 + 1, 0xE4E7EA);
            for (int y = dy0; y <= dy1; y++) {
                for (int x = dx0; x <= dx1; x++) {
                    int c = y % 2 == 0 ? 0x06100C : 0x081512;
                    if (x % 2 == 0 && y % 2 == 1) c = 0x0B1C16;
                    set(img, x, y, c);
                }
            }
            if (radio) {
                int[] lamps = {0xFFB347, 0x3A2A10, 0x3A2A10};
                for (int i = 0; i < 3; i++) rect(img, dx1 - 4, dy0 + 4 + i * 6, dx1 - 2, dy0 + 6 + i * 6, lamps[i]);
            }
            // smoked glass list
            int lx = list[0], ly = list[1], lw = list[2], lh = list[3];
            rect(img, lx, ly, lx + lw - 1, ly + lh - 1, 0x0D1215);
            hLine(img, lx - 1, lx + lw, ly - 1, 0x050708);
            vLine(img, lx - 1, ly - 1, ly + lh, 0x050708);
            hLine(img, lx - 1, lx + lw, ly + lh, 0xE4E7EA);
            vLine(img, lx + lw, ly - 1, ly + lh, 0xE4E7EA);
            hLine(img, lx, lx + lw - 1, ly, 0x1A2226);
            return img;
        }

        public BufferedImage button(int state) {
            BufferedImage img = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);
            int top = state == 2 ? 0x9DA1A5 : state == 1 ? 0xF2F4F6 : 0xE6E8EA;
            int bottom = state == 2 ? 0xC4C8CC : state == 1 ? 0xB8BCC0 : 0xA8ACB0;
            for (int y = 1; y < 19; y++) hLine(img, 1, 198, y, mix(top, bottom, (y - 1) / 17.0));
            if (state == 2) {
                hLine(img, 2, 197, 2, 0xFFB347); // the lit key
                hLine(img, 1, 198, 1, 0x6E7276);
            } else {
                hLine(img, 1, 198, 1, 0xFFFFFF);
                hLine(img, 1, 198, 18, 0x7C8084);
            }
            outline(img, 0, 0, 199, 19, state == 1 ? 0x58D6B8 : 0x1A1C1E);
            corners(img, 199, 19);
            return img;
        }
    }

    // ---------------------------------------------------------------- walnut variations

    /** The walnut case of all three variations: wood, bevel, a metal rim around the dial and the list. */
    static BufferedImage walnutCase(int w, int h, int header, int[] list, int wood, double grain, int rim, int rimDark) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        wood(img, 0, 0, w, h, 7, false, wood, grain);
        bevelFrame(img, 0, 0, w, h, 0x160C05, mix(wood, 0xFFFFFF, 0.25), mix(wood, 0x000000, 0.45));
        rect(img, 4, 3, w - 5, header, rimDark);
        outline(img, 4, 3, w - 5, header, rim);
        int lx = list[0], ly = list[1], lw = list[2], lh = list[3];
        outline(img, lx - 2, ly - 2, lx + lw + 1, ly + lh + 1, rim);
        hLine(img, lx - 2, lx + lw + 1, ly + lh + 1, rimDark);
        vLine(img, lx + lw + 1, ly - 2, ly + lh + 1, rimDark);
        outline(img, lx - 1, ly - 1, lx + lw, ly + lh, 0x0E0804);
        return img;
    }

    /** Scale strokes along the bottom of the dial (every 4 px, long ones every 20 px) and a short red pointer. */
    static void scale(BufferedImage img, int x0, int x1, int bottom, int small, int large, int pointer, int skipFrom) {
        for (int x = x0 + 4; x <= x1 - 4; x += 4) {
            if (x >= skipFrom) break;
            boolean big = (x - x0 - 4) % 20 == 0;
            vLine(img, x, bottom - (big ? 3 : 1), bottom - 1, big ? large : small);
        }
        int needle = x0 + (int) ((x1 - x0) * 0.62);
        vLine(img, needle, bottom - 5, bottom - 1, pointer);
    }

    /** Bakelite / piano key / brass key: a vertical gradient with a light top line, dark bottom lines and an outline. */
    static BufferedImage key(int state, int top, int bottom, int light, int shade, int line, int hoverLine, int pressedTop,
                             int pressedBottom) {
        BufferedImage img = new BufferedImage(200, 20, BufferedImage.TYPE_INT_ARGB);
        int t = state == 2 ? pressedTop : state == 1 ? mix(top, 0xFFFFFF, 0.15) : top;
        int b = state == 2 ? pressedBottom : state == 1 ? mix(bottom, 0xFFFFFF, 0.15) : bottom;
        for (int y = 1; y < 19; y++) hLine(img, 1, 198, y, mix(t, b, (y - 1) / 17.0));
        if (state == 2) {
            hLine(img, 1, 198, 1, mix(pressedTop, 0x000000, 0.3));
            hLine(img, 1, 198, 2, mix(pressedTop, 0x000000, 0.15));
        } else {
            hLine(img, 1, 198, 1, light);
            hLine(img, 1, 198, 17, shade);
            hLine(img, 1, 198, 18, mix(shade, 0x000000, 0.25));
        }
        outline(img, 0, 0, 199, 19, state == 1 ? hoverLine : line);
        corners(img, 199, 19);
        return img;
    }

    static final class Amber implements Theme {
        public String name() {
            return "walnut-amber";
        }

        public Colours colours() {
            return new Colours(0x3A1E08, 0x2E4A10, 0x7A4A1A, 0xF3E3BC, 0xF0E2C0, 0xC8A56A, 0xFFC94A,
                    0x14FFD890, 0x38FFC060, 0x3A2614, 0x7A6A50, false, false, true);
        }

        public BufferedImage panel(int w, int h, int header, int[] list, boolean radio) {
            BufferedImage img = walnutCase(w, h, header, list, 0x4E3019, 11, 0xD4AF37, 0x8A6A1E);
            // the dial glows from behind: brightest in the middle, darker to the edges
            int dx0 = 5, dy0 = 4, dx1 = w - 6, dy1 = header - 1;
            double cx = (dx0 + dx1) / 2.0, cy = (dy0 + dy1) / 2.0;
            for (int y = dy0; y <= dy1; y++) {
                for (int x = dx0; x <= dx1; x++) {
                    double tx = (x - cx) / (dx1 - cx), ty = (y - cy) / (dy1 - cy);
                    double glow = Math.clamp(1 - 0.55 * tx * tx - 0.3 * ty * ty, 0, 1);
                    set(img, x, y, mix(0xB8691C, 0xFFD98A, glow));
                }
            }
            hLine(img, dx0, dx1, dy0, 0xFFF0C0);
            scale(img, dx0, dx1, dy1, 0x7A4A1A, 0x4A2A08, 0x9A1A10, Integer.MAX_VALUE);
            // speaker cloth with a gold thread
            for (int y = list[1]; y < list[1] + list[3]; y++) {
                for (int x = list[0]; x < list[0] + list[2]; x++) {
                    int c = (x + y) % 4 == 0 ? 0x4A3818 : (x % 2 == 0 && y % 2 == 0) ? 0x241709 : 0x2A1C10;
                    set(img, x, y, c);
                }
            }
            return img;
        }

        public BufferedImage button(int state) {
            return key(state, 0xF2E8D0, 0xD8CAA6, 0xFFF8E6, 0xB8A882, 0x3A2A18, 0xD4AF37, 0xC0B28E, 0xD6C9A6);
        }
    }

    static final class Teak implements Theme {
        public String name() {
            return "walnut-teak";
        }

        public Colours colours() {
            return new Colours(0xF1D9A0, 0x9FE08A, 0xB89A62, 0xFFF4DC, 0xF3E9D2, 0xB8A07A, 0xFFD36A,
                    0x12FFFFFF, 0x30FFE0A0, 0x1A1A1A, 0x6A665E, false, false, true);
        }

        public BufferedImage panel(int w, int h, int header, int[] list, boolean radio) {
            BufferedImage img = walnutCase(w, h, header, list, 0x8A5A32, 13, 0xC8A050, 0x6A4A1E);
            // black glass dial with a soft reflection at the top and a gold scale
            int dx0 = 5, dy0 = 4, dx1 = w - 6, dy1 = header - 1;
            for (int y = dy0; y <= dy1; y++) {
                int c = mix(0x2A2826, 0x0C0B0A, (y - dy0) / (double) (dy1 - dy0));
                for (int x = dx0; x <= dx1; x++) set(img, x, y, c);
            }
            hLine(img, dx0, dx1, dy0, 0x4A4744);
            hLine(img, dx0, dx1, dy1 - 5, 0x5A4620);
            scale(img, dx0, dx1, dy1, 0x8A6A30, 0xC8A050, 0xD02A1A, Integer.MAX_VALUE);
            // ribbed 1960s speaker cloth
            for (int y = list[1]; y < list[1] + list[3]; y++) {
                for (int x = list[0]; x < list[0] + list[2]; x++) set(img, x, y, x % 2 == 0 ? 0x2A2019 : 0x1E1712);
            }
            return img;
        }

        public BufferedImage button(int state) {
            return key(state, 0xF7F4EE, 0xE2DDD2, 0xFFFFFF, 0xC9C3B6, 0x2A2A2A, 0xC8A050, 0xD2CCBE, 0xE6E1D6);
        }
    }

    static final class Mahogany implements Theme {
        public String name() {
            return "walnut-mahogany";
        }

        public Colours colours() {
            return new Colours(0x2E1E0E, 0x2F6B2F, 0x6E5638, 0xF0DEB8, 0xF0E2C0, 0xB89C6E, 0xFFC94A,
                    0x18FFE0B0, 0x40FFD890, 0x2A1A08, 0x6A5020, false, false, true);
        }

        public BufferedImage panel(int w, int h, int header, int[] list, boolean radio) {
            BufferedImage img = walnutCase(w, h, header, list, 0x6A2A1A, 12, 0xE0B860, 0x7A5A20);
            int dx0 = 5, dy0 = 4, dx1 = w - 6, dy1 = header - 1;
            for (int y = dy0; y <= dy1; y++) {
                int c = mix(0xEFE4C2, 0xD8C898, (y - dy0) / (double) (dy1 - dy0));
                for (int x = dx0; x <= dx1; x++) set(img, x, y, c);
            }
            hLine(img, dx0, dx1, dy0, 0xFFF6DC);
            int eyeX = w - 18, eyeY = header - 10;
            scale(img, dx0, dx1, dy1, 0x8A7450, 0x5A4630, 0xB03020, radio ? eyeX - 9 : Integer.MAX_VALUE);
            if (radio) magicEye(img, eyeX, eyeY);
            // fretwork in front of the speaker cloth
            for (int y = list[1]; y < list[1] + list[3]; y++) {
                for (int x = list[0]; x < list[0] + list[2]; x++) {
                    boolean lattice = Math.floorMod(x + y, 8) == 0 || Math.floorMod(x - y, 8) == 0;
                    set(img, x, y, lattice ? 0x3A180E : 0x1E0E08);
                }
            }
            // brass corner fittings with a rivet each
            brassCorner(img, 0, 0, 1, 1);
            brassCorner(img, w - 1, 0, -1, 1);
            brassCorner(img, 0, h - 1, 1, -1);
            brassCorner(img, w - 1, h - 1, -1, -1);
            return img;
        }

        public BufferedImage button(int state) {
            return key(state, 0xF0CC70, 0xA07A2C, 0xFFF0B0, 0x7A5A20, 0x3A2A10, 0xFFF0B0, 0x9A7428, 0xC89E48);
        }

        /** An L-shaped brass fitting, 3 px thick and 12 px long, from the corner (x, y) inwards (dx, dy = ±1). */
        static void brassCorner(BufferedImage img, int x, int y, int dx, int dy) {
            for (int i = 0; i < 12; i++) {
                for (int t = 0; t < 3; t++) {
                    int shade = t == 0 ? 0xF4D688 : t == 1 ? 0xD8B058 : 0x8A6A28;
                    set(img, x + dx * i, y + dy * t, shade);
                    set(img, x + dx * t, y + dy * i, shade);
                }
            }
            set(img, x + dx * 7, y + dy, 0x5A4010);
            set(img, x + dx, y + dy * 7, 0x5A4010);
            img.setRGB(x, y, 0);
        }

        /** A green magic-eye tuning tube: black bezel, dark green glass, a bright fan opening upwards. */
        static void magicEye(BufferedImage img, int cx, int cy) {
            for (int y = cy - 7; y <= cy + 7; y++) {
                for (int x = cx - 7; x <= cx + 7; x++) {
                    double d = Math.hypot(x - cx, y - cy);
                    if (d > 7.2) continue;
                    int c;
                    if (d > 5.8) c = 0x0A0A0A;
                    else if (d < 1.6) c = 0x061408;
                    else {
                        double angle = Math.toDegrees(Math.atan2(x - cx, cy - y));
                        c = Math.abs(angle) < 62 ? (d > 4.5 ? 0x3CD860 : 0x7CFF96) : 0x0E3A18;
                    }
                    set(img, x, y, c);
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    /** Walnut grain; vertical = streaks run up and down (the hi-fi cheeks). */
    static void wood(BufferedImage img, int x0, int y0, int w, int h, int seed, boolean vertical) {
        wood(img, x0, y0, w, h, seed, vertical, 0x5B3A21, 10);
    }

    static void wood(BufferedImage img, int x0, int y0, int w, int h, int seed, boolean vertical, int base, double depth) {
        Random rnd = new Random(seed);
        double phase = rnd.nextDouble() * 10;
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                double a = vertical ? x : y, b = vertical ? y : x;
                double grain = Math.sin(a * 0.9 + 2.2 * Math.sin(b * 0.035 + phase) + Math.sin(b * 0.11) * 0.6);
                double v = grain * depth + rnd.nextGaussian() * 2.5;
                int r = (int) Math.clamp(((base >> 16) & 255) + v, 0, 255), g = (int) Math.clamp(((base >> 8) & 255) + v * 0.7, 0, 255),
                        bl = (int) Math.clamp((base & 255) + v * 0.45, 0, 255);
                set(img, x, y, (r << 16) | (g << 8) | bl);
            }
        }
    }

    static void screw(BufferedImage img, int x, int y) {
        int[][] px = {{0, 1, 1, 0}, {1, 2, 2, 1}, {1, 2, 2, 1}, {0, 1, 1, 0}};
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                if (px[j][i] == 0) continue;
                set(img, x + i, y + j, px[j][i] == 2 ? 0x5A5E62 : 0xA8ACB0);
            }
        }
        set(img, x + 1, y + 1, 0x2A2D30);
        set(img, x + 2, y + 2, 0x2A2D30);
    }

    static void bevelFrame(BufferedImage img, int x0, int y0, int w, int h, int line, int light, int dark) {
        outline(img, x0, y0, x0 + w - 1, y0 + h - 1, line);
        hLine(img, x0 + 1, x0 + w - 2, y0 + 1, light);
        vLine(img, x0 + 1, y0 + 1, y0 + h - 2, light);
        hLine(img, x0 + 1, x0 + w - 2, y0 + h - 2, dark);
        vLine(img, x0 + w - 2, y0 + 1, y0 + h - 2, dark);
        corners(img, x0 + w - 1, y0 + h - 1);
    }

    /** Clears the four corner pixels so the frame looks rounded. */
    static void corners(BufferedImage img, int x1, int y1) {
        img.setRGB(0, 0, 0);
        img.setRGB(x1, 0, 0);
        img.setRGB(0, y1, 0);
        img.setRGB(x1, y1, 0);
    }

    static void nineSlice(BufferedImage g, BufferedImage s, int x, int y, int w, int h, int b) {
        int sw = s.getWidth(), sh = s.getHeight();
        for (int j = 0; j < h; j++) {
            int sy = j < b ? j : j >= h - b ? sh - (h - j) : b + (j - b) % (sh - 2 * b);
            for (int i = 0; i < w; i++) {
                int sx = i < b ? i : i >= w - b ? sw - (w - i) : b + (i - b) % (sw - 2 * b);
                int p = s.getRGB(sx, sy);
                if ((p >>> 24) != 0) g.setRGB(x + i, y + j, blend(g.getRGB(x + i, y + j), p));
            }
        }
    }

    static void blendRect(BufferedImage g, int x, int y, int w, int h, int argb) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) g.setRGB(i, j, blend(g.getRGB(i, j), argb));
    }

    static int blend(int dst, int src) {
        int a = src >>> 24;
        if (a == 255) return src;
        int r = (((src >> 16) & 255) * a + ((dst >> 16) & 255) * (255 - a)) / 255;
        int gg = (((src >> 8) & 255) * a + ((dst >> 8) & 255) * (255 - a)) / 255;
        int bb = ((src & 255) * a + (dst & 255) * (255 - a)) / 255;
        int da = Math.max(a, dst >>> 24);
        return (da << 24) | (r << 16) | (gg << 8) | bb;
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static void rect(BufferedImage img, int x0, int y0, int x1, int y1, int rgb) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) set(img, x, y, rgb);
    }

    static void outline(BufferedImage img, int x0, int y0, int x1, int y1, int rgb) {
        hLine(img, x0, x1, y0, rgb);
        hLine(img, x0, x1, y1, rgb);
        vLine(img, x0, y0, y1, rgb);
        vLine(img, x1, y0, y1, rgb);
    }

    static void hLine(BufferedImage img, int x0, int x1, int y, int rgb) {
        for (int x = x0; x <= x1; x++) set(img, x, y, rgb);
    }

    static void vLine(BufferedImage img, int x, int y0, int y1, int rgb) {
        for (int y = y0; y <= y1; y++) set(img, x, y, rgb);
    }

    static void set(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, 0xFF000000 | rgb);
    }

    static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    static void write(BufferedImage img, Path path) throws IOException {
        ImageIO.write(img, "png", path.toFile());
    }

    static BufferedImage readJar(String entry) throws IOException {
        try (ZipFile zip = new ZipFile(CLIENT_JAR); InputStream in = zip.getInputStream(zip.getEntry(entry))) {
            return ImageIO.read(in);
        }
    }

    // ---------------------------------------------------------------- the Minecraft font (bitmap providers only)

    record Glyph(BufferedImage image, int x, int y, int w, int h, int width, int up) {
    }

    static final class Font {
        final Map<Integer, Glyph> glyphs = new HashMap<>();

        static Font load() throws IOException {
            Font font = new Font();
            String json;
            try (ZipFile zip = new ZipFile(CLIENT_JAR);
                 InputStream in = zip.getInputStream(zip.getEntry("assets/minecraft/font/include/default.json"))) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            // providers in order: later ones must not override earlier ones (vanilla takes the first match)
            int at = 0;
            while ((at = json.indexOf("\"file\"", at)) >= 0) {
                int fs = json.indexOf('"', json.indexOf(':', at) + 1) + 1;
                String file = json.substring(fs, json.indexOf('"', fs));
                int provEnd = json.indexOf('}', at);
                int ascent = intField(json, "\"ascent\"", at, provEnd, 7);
                int height = intField(json, "\"height\"", at, provEnd, 8);
                int cs = json.indexOf('[', json.indexOf("\"chars\"", at));
                int ce = json.indexOf(']', cs);
                List<int[]> rows = new ArrayList<>();
                int p = cs;
                while ((p = json.indexOf('"', p + 1)) >= 0 && p < ce) {
                    StringBuilder sb = new StringBuilder();
                    int q = p + 1;
                    while (json.charAt(q) != '"') {
                        if (json.charAt(q) == '\\') {
                            char e = json.charAt(q + 1);
                            if (e == 'u') {
                                sb.append((char) Integer.parseInt(json.substring(q + 2, q + 6), 16));
                                q += 6;
                            } else {
                                sb.append(e);
                                q += 2;
                            }
                        } else {
                            sb.append(json.charAt(q++));
                        }
                    }
                    rows.add(sb.toString().codePoints().toArray());
                    p = q;
                }
                String path = "assets/minecraft/textures/" + file.substring(file.indexOf(':') + 1);
                BufferedImage img = readJar(path);
                int cellW = img.getWidth() / rows.get(0).length, cellH = img.getHeight() / rows.size();
                double scale = height / (double) cellH;
                for (int r = 0; r < rows.size(); r++) {
                    for (int c = 0; c < rows.get(r).length; c++) {
                        int cp = rows.get(r)[c];
                        if (cp == 0 || font.glyphs.containsKey(cp)) continue;
                        int gx = c * cellW, gy = r * cellH, width = 0;
                        for (int x = cellW - 1; x >= 0 && width == 0; x--) {
                            for (int y = 0; y < cellH; y++) {
                                if ((img.getRGB(gx + x, gy + y) >>> 24) != 0) {
                                    width = x + 1;
                                    break;
                                }
                            }
                        }
                        font.glyphs.put(cp, new Glyph(img, gx, gy, cellW, cellH, (int) (0.5 + width * scale) + 1, 7 - ascent));
                    }
                }
                at = ce;
            }
            return font;
        }

        static int intField(String json, String key, int from, int to, int fallback) {
            int k = json.indexOf(key, from);
            if (k < 0 || k > to) return fallback;
            int s = json.indexOf(':', k) + 1, e = s;
            while (Character.isWhitespace(json.charAt(s))) s++;
            e = s;
            while (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-') e++;
            return Integer.parseInt(json.substring(s, e));
        }

        int advance(int cp) {
            if (cp == ' ') return 4;
            Glyph g = glyphs.get(cp);
            return g == null ? 6 : g.width();
        }

        int width(String s) {
            return s.codePoints().map(this::advance).sum();
        }

        void draw(BufferedImage target, String s, int x, int y, int rgb, boolean shadow) {
            if (shadow) draw0(target, s, x + 1, y + 1, (rgb & 0xFCFCFC) >> 2);
            draw0(target, s, x, y, rgb);
        }

        void draw0(BufferedImage target, String s, int x, int y, int rgb) {
            int cx = x;
            for (int cp : s.codePoints().toArray()) {
                Glyph g = glyphs.get(cp);
                if (g != null && cp != ' ') {
                    for (int j = 0; j < g.h(); j++) {
                        for (int i = 0; i < g.w(); i++) {
                            int a = g.image().getRGB(g.x() + i, g.y() + j) >>> 24;
                            if (a == 0) continue;
                            int tx = cx + i, ty = y + g.up() + j;
                            if (tx < 0 || ty < 0 || tx >= target.getWidth() || ty >= target.getHeight()) continue;
                            target.setRGB(tx, ty, blend(target.getRGB(tx, ty), (a << 24) | (rgb & 0xFFFFFF)));
                        }
                    }
                }
                cx += advance(cp);
            }
        }
    }
}
