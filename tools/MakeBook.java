import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * The art of the guide book: the screen's panel (the tuner's teak case around a sheet of paper, 232x230), the item
 * texture (the vanilla book in teak, with a dot sending two waves on the cover) and the four build pictures of the
 * pages (96x96, made of the mod's own block textures, no lettering): tower, channels, relay, speakers.
 * Everything goes into the mod's resources; art/book/preview.png shows it together: a mock page at GUI scale 3, the
 * pictures on paper and the item texture enlarged.
 * Run: java tools/MakeBook.java (from the project folder; the drawing helpers are those of tools/MakeGuiTextures.java).
 */
public class MakeBook extends MakeGuiTextures {
    static final Path TEXTURES = Path.of("src/main/resources/assets/worldradio/textures");
    static final Path ART = Path.of("art/book");
    /** The screen's measures, copied from GuideBookScreen. */
    static final int W = 232, H = 230, MARGIN = 8, FOOTER = 26, TAB_W = 26, TAB_H = 24, TAB_GAP = 3;
    static final int WOOD = 0x8A5A32, GOLD = 0xC8A050, GOLD_LIGHT = 0xF1D9A0, PAPER = 0xF1E6CC, PAPER_EDGE = 0xD9C9A2;
    static final int TITLE = 0x4A2A12, HEADING = 0xB45A14, BODY = 0x2A2018, SOFT = 0x7A6A52;
    static final int ORANGE = 0xFB923C, BLUE = 0x60A5FA, RING = 0x9A7A3A;

    public static void main(String[] args) throws IOException {
        Files.createDirectories(ART);
        Files.createDirectories(TEXTURES.resolve("gui/book"));
        BufferedImage panel = panel();
        write(panel, TEXTURES.resolve("gui/book.png"));
        String[] names = {"tower", "channels", "relay", "speakers"};
        BufferedImage[] pictures = {tower(), channels(), relay(), speakers()};
        for (int i = 0; i < names.length; i++) write(pictures[i], TEXTURES.resolve("gui/book/" + names[i] + ".png"));
        BufferedImage item = item();
        write(item, TEXTURES.resolve("item/guide_book.png"));
        write(preview(panel, pictures, names, item), ART.resolve("preview.png"));
    }

    // ---------------------------------------------------------------- panel

    static BufferedImage panel() {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        wood(img, 0, 0, W, H, 7, false, WOOD, 13);
        bevelFrame(img, 0, 0, W, H, 0x160C05, mix(WOOD, 0xFFFFFF, 0.25), mix(WOOD, 0x000000, 0.45));
        int x0 = MARGIN, y0 = MARGIN, x1 = W - MARGIN - 1, y1 = H - FOOTER - 1;
        outline(img, x0 - 2, y0 - 2, x1 + 2, y1 + 2, GOLD);
        hLine(img, x0 - 2, x1 + 2, y1 + 2, 0x6A4A1E);
        vLine(img, x1 + 2, y0 - 2, y1 + 2, 0x6A4A1E);
        outline(img, x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0x0E0804);
        Random rnd = new Random(11);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int v = (int) Math.round(rnd.nextGaussian() * 1.5);
                set(img, x, y, shade(PAPER, v));
            }
        }
        outline(img, x0, y0, x1, y1, PAPER_EDGE);
        for (int sx : new int[]{2, W - 6}) {
            screw(img, sx, 2);
            screw(img, sx, H - 6);
        }
        return img;
    }

    static int shade(int rgb, int by) {
        return (Math.clamp(((rgb >> 16) & 255) + by, 0, 255) << 16) | (Math.clamp(((rgb >> 8) & 255) + by, 0, 255) << 8)
                | Math.clamp((rgb & 255) + by, 0, 255);
    }

    // ---------------------------------------------------------------- item

    /** The vanilla book with its leather in teak and the logo on the cover: a dot sending two waves, in gold. */
    static BufferedImage item() throws IOException {
        int cover = 0x9A6234;
        BufferedImage img = copy(readJar("assets/minecraft/textures/item/book.png"));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int p = img.getRGB(x, y);
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                if ((p >>> 24) == 0 || Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 16) continue; // the pages are grey
                double f = r / (double) 0x65; // 0x65: the red of the vanilla cover's main brown
                set(img, x, y, ((int) Math.min(255, ((cover >> 16) & 255) * f) << 16)
                        | ((int) Math.min(255, ((cover >> 8) & 255) * f) << 8) | (int) Math.min(255, (cover & 255) * f));
            }
        }
        for (int[] p : new int[][]{{5, 5}, {5, 6}, {7, 4}, {8, 5}, {8, 6}, {7, 7}}) set(img, p[0], p[1], GOLD_LIGHT);
        for (int[] p : new int[][]{{10, 3}, {11, 4}, {11, 5}, {11, 6}, {10, 7}}) set(img, p[0], p[1], GOLD);
        return img;
    }

    // ---------------------------------------------------------------- pictures

    /** Three transmitters with no, two and four antenna blocks: the taller the mast, the further the waves. */
    static BufferedImage tower() throws IOException {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB), rod = rod();
        int[] antennas = {0, 2, 4};
        for (int t = 0; t < 3; t++) {
            int x = 8 + t * 32, n = antennas[t];
            put(img, face("radio_front_" + (n == 0 ? 1 : n < 4 ? 2 : 3)), x, 80);
            for (int i = 1; i <= n; i++) put(img, rod, x, 80 - 16 * i);
            waves(img, x + 8, 78 - 16 * n, t + 1, ORANGE);
        }
        return img;
    }

    /**
     * A transmitter with its mast and six channels in every way they connect: beside it, below it, stacked, and on
     * from channel to channel. Only the top of the transmitter stays free, for the mast.
     */
    static BufferedImage channels() throws IOException {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB), rod = rod(), channel = face("channel_front_3");
        put(img, face("radio_front_2"), 40, 64);
        for (int[] at : new int[][]{{24, 64}, {24, 80}, {40, 80}, {56, 64}, {56, 48}, {56, 32}}) put(img, channel, at[0], at[1]);
        put(img, rod, 40, 48);
        put(img, rod, 40, 32);
        waves(img, 48, 30, 3, ORANGE);
        return img;
    }

    /** The transmitter's range ends before the radio; the amplifier inside it carries the signal on. */
    static BufferedImage relay() throws IOException {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        arc(img, 16, 48, 34, 0, 360, ORANGE, true);
        arc(img, 48, 48, 34, 0, 360, BLUE, true);
        put(img, face("radio_front_2"), 8, 40);
        put(img, face("amplifier_front_2"), 40, 40);
        put(img, face("receiver_front_3"), 72, 40);
        return img;
    }

    /** A radio with speakers on both sides and on top, one more in a chain, and how far it is heard. */
    static BufferedImage speakers() throws IOException {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB), speaker = face("speaker_front_on");
        arc(img, 48, 64, 44, 0, 180, RING, true);
        put(img, face("receiver_front_3"), 40, 56);
        put(img, speaker, 24, 56);
        put(img, speaker, 56, 56);
        put(img, speaker, 40, 40);
        put(img, speaker, 8, 56);
        return img;
    }

    /** The first frame of a block texture. */
    static BufferedImage face(String name) throws IOException {
        return ImageIO.read(TEXTURES.resolve("block/" + name + ".png").toFile()).getSubimage(0, 0, 16, 16);
    }

    /** A lightning rod from the side, put together from the head and the pole of the vanilla texture. */
    static BufferedImage rod() throws IOException {
        BufferedImage atlas = readJar("assets/minecraft/textures/block/lightning_rod.png");
        BufferedImage rod = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        put(rod, atlas.getSubimage(0, 0, 4, 4), 6, 0);
        put(rod, atlas.getSubimage(0, 4, 2, 12), 7, 4);
        return rod;
    }

    static void put(BufferedImage img, BufferedImage tile, int x, int y) {
        for (int j = 0; j < tile.getHeight(); j++) {
            for (int i = 0; i < tile.getWidth(); i++) {
                int p = tile.getRGB(i, j);
                if ((p >>> 24) != 0 && x + i >= 0 && y + j >= 0 && x + i < img.getWidth() && y + j < img.getHeight()) {
                    img.setRGB(x + i, y + j, blend(img.getRGB(x + i, y + j), p));
                }
            }
        }
    }

    /** Radio waves to both sides of a point: count arcs, each wider and fainter than the one before. */
    static void waves(BufferedImage img, int cx, int cy, int count, int rgb) {
        for (int i = 0; i < count; i++) {
            int faded = mix(rgb, PAPER, i * 0.22);
            arc(img, cx, cy, 6 + 4 * i, 20, 70, faded, false);
            arc(img, cx, cy, 6 + 4 * i, 110, 160, faded, false);
        }
    }

    /** A circle's arc in degrees, counter-clockwise from the right; dashed = three pixels on, three off. */
    static void arc(BufferedImage img, double cx, double cy, double r, double from, double to, int rgb, boolean dashed) {
        int steps = (int) (r * Math.toRadians(to - from) * 2) + 1;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(from + (to - from) * i / steps);
            if (dashed && (int) (r * a / 3) % 2 == 1) continue;
            set(img, (int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy - r * Math.sin(a)), rgb);
        }
    }

    // ---------------------------------------------------------------- preview

    static BufferedImage preview(BufferedImage panel, BufferedImage[] pictures, String[] names, BufferedImage item)
            throws IOException {
        Font f = Font.load();
        Teak theme = new Teak();
        BufferedImage[] keys = {theme.button(0), theme.button(1), theme.button(2)};
        BufferedImage g = new BufferedImage(600, 250, BufferedImage.TYPE_INT_ARGB);
        rect(g, 0, 0, g.getWidth() - 1, g.getHeight() - 1, 0x6E7F6E);
        int left = 10 + TAB_W - 3, top = 10;
        put(g, panel, left, top);
        BufferedImage[] blocks = {face("radio_front_3"), face("channel_front_3"), face("amplifier_front_3"),
                face("receiver_front_3"), face("speaker_front_on"), ImageIO.read(TEXTURES.resolve("item/portable_radio.png").toFile())};
        BufferedImage[] topics = {item, rod(), readJar("assets/minecraft/textures/item/spyglass.png"),
                readJar("assets/minecraft/textures/block/note_block.png")};
        for (int i = 0; i < blocks.length; i++) tab(g, keys[0], blocks[i], left - TAB_W + 3, top + 8 + i * (TAB_H + TAB_GAP));
        for (int i = 0; i < topics.length; i++) tab(g, keys[i == 1 ? 2 : 0], topics[i], left + W - 3, top + 8 + i * (TAB_H + TAB_GAP));

        int x = left + MARGIN + 6, y = top + MARGIN + 3, width = W - 2 * MARGIN - 12;
        put(g, rod(), x, y);
        f.draw(g, "Range & Antennas", x + 20, y + 4, TITLE, false);
        hLine(g, x, x + width - 1, y + 16, PAPER_EDGE);
        y = top + MARGIN + 22;
        y = paragraph(g, f, "A transmitter reaches 32 blocks. Every antenna block stacked on top of it adds 32 more,"
                + " up to 32 blocks high.", x, y, width, BODY);
        y = paragraph(g, f, "\"The higher the mast, the further the music.\"", x, y, width, SOFT);
        f.draw(g, "Antenna blocks", x, y + 3, HEADING, false);
        put(g, pictures[0], x + (width - 96) / 2, y + 15);
        button(g, keys[0], left + MARGIN, top + H - FOOTER + 4, 22, 18, "<", theme.colours(), f, false);
        button(g, keys[1], left + W - MARGIN - 22, top + H - FOOTER + 4, 22, 18, ">", theme.colours(), f, false);
        String number = "8 / 14";
        f.draw(g, number, left + (W - f.width(number)) / 2, top + H - FOOTER + 9, 0xFFF4DC, true);

        int px = left + W + TAB_W + 12;
        for (int i = 0; i < pictures.length; i++) {
            int tx = px + i % 2 * 106, ty = 10 + i / 2 * 116;
            f.draw(g, names[i], tx, ty, 0xFFFFFF, true);
            rect(g, tx, ty + 11, tx + 99, ty + 110, PAPER);
            outline(g, tx, ty + 11, tx + 99, ty + 110, PAPER_EDGE);
            put(g, pictures[i], tx + 2, ty + 13);
        }
        int ix = px + 2 * 106 + 6;
        f.draw(g, "item", ix, 10, 0xFFFFFF, true);
        put(g, item, ix, 21);
        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                int p = item.getRGB(k, j);
                if ((p >>> 24) != 0) rect(g, ix + k * 4, 41 + j * 4, ix + k * 4 + 3, 44 + j * 4, p & 0xFFFFFF);
            }
        }
        BufferedImage sheet = new BufferedImage(g.getWidth() * SCALE, g.getHeight() * SCALE, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < sheet.getHeight(); j++) {
            for (int i = 0; i < sheet.getWidth(); i++) sheet.setRGB(i, j, g.getRGB(i / SCALE, j / SCALE));
        }
        return sheet;
    }

    static void tab(BufferedImage g, BufferedImage key, BufferedImage icon, int x, int y) {
        nineSlice(g, key, x, y, TAB_W, TAB_H, 3);
        put(g, icon, x + 5, y + 4);
    }

    /** Draws the text wrapped to the width, nine pixels a line; returns where the next section starts. */
    static int paragraph(BufferedImage g, Font f, String text, int x, int y, int width, int rgb) {
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && f.width(line + word) > width) {
                f.draw(g, line.toString().trim(), x, y, rgb, false);
                y += 9;
                line.setLength(0);
            }
            line.append(word).append(' ');
        }
        f.draw(g, line.toString().trim(), x, y, rgb, false);
        return y + 9 + 5;
    }
}
