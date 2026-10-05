import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Block textures (16x16) and models, the portable radio's among them. Receiver (the "Radio") = wooden case,
 * amplifier = steel case, transmitter (block id "radio") = dark steel. The fronts have a speaker
 * grille on the left (x 2..6; transmitter and amplifier a mast there) and, past a one pixel divider, a small level meter at the
 * top right and the reach below it (three bars, lit up to the blockstate level) next to a status LED (red at level 0),
 * all inside a one pixel panel margin. Level 0 (no station / no signal) is one still frame; levels 1 to 3 are
 * animated: the meter bounces and the grille sends rings out from the cone (the mast: waves from its tip). The top of
 * transmitter and the amplifier carries an antenna socket. The channel is the transmitter without mast, bars and
 * socket, with a wide meter and a sine scope instead; the speaker is a wooden box with one big grille. Also writes art/blocks/preview/textures-2.0.png.
 * Run: java tools/MakeTextures.java (from the project folder).
 */
public class MakeTextures {
    static final File TEXTURES = new File("src/main/resources/assets/worldradio/textures/block");
    static final Path MODELS = Path.of("src/main/resources/assets/worldradio/models/block");
    static final int FRAMES = 8;
    static final int FRAMETIME = 2;
    static final int MAX_LEVEL = 3;

    /**
     * Colours of one block: case rim, case fill and grain, front panel, lit parts, dim lit parts, and the grille's
     * holes, cloth and the bright and soft ring.
     */
    record Palette(int rim, int fill, int light, int panel, int lit, int litDim,
                   int hole, int cloth, int ringHi, int ringLo) {
    }

    static final Palette RECEIVER = new Palette(0x5A3D24, 0x6B4A2E, 0x7A5636, 0x2B2B2B, 0x4ADE80, 0x1F6B3A,
            0x151515, 0x303030, 0x7A7A7A, 0x4E4E4E);
    /** The transmitter (block id "radio"): {@code hole} is the sky behind the mast, {@code cloth} the mast. */
    static final Palette RADIO = new Palette(0x34383E, 0x4A4F57, 0x5E646D, 0x1C1C20, 0xFB923C, 0x8A4A14,
            0x0E0E12, 0x9AA0A8, 0xFB923C, 0x8A4A14);
    static final Palette AMPLIFIER = new Palette(0x6E737A, 0x8A8F96, 0xA3A8AE, 0x1E2A44, 0x60A5FA, 0x24466E,
            0x0E1422, 0x2C3A58, 0x8A9BB8, 0x4D5E80);
    static final int OFF_BAR = 0x3A3A3A;
    static final int METER_BG = 0x151515;
    static final int LED_OFF = 0xB91C1C;
    static final int SLOT = 0x222222;
    static final int HOLE = 0x0B0B0B;

    /** Grille area and the centre of the ring animation (the cone). */
    static final int GRILLE_X0 = 2, GRILLE_X1 = 6, GRILLE_Y0 = 2, GRILLE_Y1 = 13;
    static final double CONE_X = 4.0, CONE_Y = 7.5;
    /** Meter window (x 8..13, y 2..5), its three columns, the LED and the reach bars (x, height; bottom at y 13). */
    static final int METER_X0 = 8, METER_X1 = 13, METER_Y0 = 2, METER_Y1 = 5;
    static final int[] METER_X = {9, 11, 13};
    static final int LED_X = 8, LED_Y = 8;
    static final int[][] BARS = {{8, 2}, {10, 4}, {12, 6}};
    static final int BAR_BOTTOM = 13;

    /** Meter heights (1..4) per frame for the three meter columns: a loose bounce that loops. */
    static final int[][] METER = {
            {2, 4, 3}, {3, 3, 4}, {4, 2, 3}, {3, 1, 2}, {2, 3, 1}, {1, 4, 2}, {2, 3, 4}, {3, 2, 3}};

    public static void main(String[] args) throws IOException {
        TEXTURES.mkdirs();
        Files.createDirectories(MODELS);
        for (String stale : new String[]{"radio_bottom.png", "amplifier_bottom.png"}) new File(TEXTURES, stale).delete();
        write("radio_side", caseTexture(RADIO));
        write("radio_top", topTexture(RADIO));
        write("channel_side", caseTexture(RADIO));
        write("channel_top", caseTexture(RADIO));
        write("receiver_side", caseTexture(RECEIVER));
        write("receiver_top", caseTexture(RECEIVER));
        write("speaker_front", speakerFront(false, 0));
        BufferedImage speakerOn = new BufferedImage(16, 16 * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) speakerOn.getGraphics().drawImage(speakerFront(true, f), 0, 16 * f, null);
        write("speaker_front_on", speakerOn);
        Files.writeString(new File(TEXTURES, "speaker_front_on.png.mcmeta").toPath(),
                "{\n  \"animation\": {\n    \"frametime\": " + FRAMETIME + "\n  }\n}\n");
        write("speaker_side", caseTexture(RECEIVER));
        for (String model : new String[]{"speaker", "speaker_on"}) {
            Files.writeString(MODELS.resolve(model + ".json"), """
                    {
                      "parent": "minecraft:block/orientable",
                      "textures": {
                        "top": "worldradio:block/speaker_side",
                        "front": "worldradio:block/%s",
                        "side": "worldradio:block/speaker_side"
                      }
                    }
                    """.formatted(model.replace("speaker", "speaker_front")));
        }
        write("amplifier_side", caseTexture(AMPLIFIER));
        write("amplifier_top", topTexture(AMPLIFIER));
        for (int level = 0; level <= MAX_LEVEL; level++) {
            front("radio", RADIO, level);
            front("channel", RADIO, level);
            front("receiver", RECEIVER, level);
            front("amplifier", AMPLIFIER, level);
        }
        write("portable_radio", portableRadio(false));
        write("portable_radio_on", portableRadio(true));
        portableModel(false);
        portableModel(true);
        // the flat item sprite of 2.0: the item now shows the model
        new File(TEXTURES.getParentFile(), "item/portable_radio.png").delete();
        new File(MODELS.toFile().getParentFile(), "item/portable_radio.json").delete();
        preview();
    }

    /**
     * The portable radio's one texture, in the Radio block's colours and patterns ({@link #RECEIVER}), laid out for its
     * model (see {@link #portableModel}): everything is the block's grained wood, and on it lie the front panel 10x5
     * at the top left (the dark panel with a 3x3 grille, a small meter, the status light and two knobs; switched on,
     * meter and light are green), the framed back panel below it, the antenna at the top right and the dark metal of
     * the handle and the knobs right of the back panel.
     */
    static BufferedImage portableRadio(boolean on) {
        Palette p = RECEIVER;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) { // the grain of caseTexture, without its rim
                set(img, x, y, (x * 5 + y * 3) % 17 == 0 ? p.rim() : (x * 7 + y * 13) % 11 == 0 ? p.light() : p.fill());
            }
        }
        java.util.Map<Character, Integer> colours = java.util.Map.ofEntries(
                java.util.Map.entry('P', p.panel()), java.util.Map.entry('H', p.hole()), java.util.Map.entry('D', p.cloth()),
                java.util.Map.entry('G', p.lit()), java.util.Map.entry('g', p.litDim()), java.util.Map.entry('O', OFF_BAR),
                java.util.Map.entry('R', LED_OFF), java.util.Map.entry('K', p.ringHi()), java.util.Map.entry('k', p.ringLo()),
                java.util.Map.entry('s', 0x4E4E4E), java.util.Map.entry('t', 0x3A3A3A), java.util.Map.entry('v', 0x2B2B2B),
                java.util.Map.entry('a', 0xB8BEC6), java.util.Map.entry('n', 0x9AA0A8));
        String[] front = {
                "PPPPPPPPPP",
                "PHDHP" + (on ? "HGHH" : "HHHH") + "P",
                "PDHDP" + (on ? "GgGG" : "OHOH") + "P",
                "PHDHP" + (on ? "G" : "R") + "KPKP",
                "PPPPPPPPPP"};
        String[] metal = {"sssttv", "sttttv", "ssssst", "tttttv", "KKk"};
        String[] antenna = {"a", "n", "n", "n", "n", "n"};
        letters(img, 0, 0, front, colours);
        letters(img, 10, 5, metal, colours);
        letters(img, 14, 0, antenna, colours);
        for (int x = 0; x < 10; x++) { // the back panel: framed like a face of the block
            for (int y = 5; y < 10; y++) if (x == 0 || x == 9 || y == 5 || y == 9) set(img, x, y, p.rim());
        }
        return img;
    }

    static void letters(BufferedImage img, int x0, int y0, String[] rows, java.util.Map<Character, Integer> colours) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) set(img, x0 + x, y0 + y, colours.get(rows[y].charAt(x)));
        }
    }

    /** One model element; faces as "north 0 0 10 5" (uv in texture pixels), separated by commas. */
    static String element(String from, String to, String faces) {
        StringBuilder json = new StringBuilder("    { \"from\": [" + from + "], \"to\": [" + to + "], \"faces\": {");
        String[] list = faces.split(", ");
        for (int i = 0; i < list.length; i++) {
            String[] f = list[i].split(" ");
            json.append(i == 0 ? " " : ", ").append('"').append(f[0]).append("\": { \"uv\": [").append(f[1]).append(", ")
                    .append(f[2]).append(", ").append(f[3]).append(", ").append(f[4]).append("], \"texture\": \"#all\" }");
        }
        return json.append(" } }").toString();
    }

    /** The same uv on the four sides. */
    static String around(String uv) {
        return "north " + uv + ", south " + uv + ", west " + uv + ", east " + uv;
    }

    /**
     * The portable radio's model, front to the north: a case 12 wide, 7 high and 6 deep standing on the ground, its
     * edges rounded by building it from three boxes that each stand back one pixel in two directions (the long one
     * carries the side panels, the tall one top and bottom, the deep one the front and back panels). A chrome handle
     * over it, an antenna at the back and two knobs on the front. While the radio is silent the antenna is pushed
     * in, one pixel of it shows; the "on" model has it pulled out and the lit texture. In the inventory and in the
     * hand it is shown larger than a block would be, it is that much smaller; the inventory picture is centred on the
     * case with its handle (y 0..10, hence the shift up by 3 pixels as seen at 30 degrees), the antenna may stick out.
     */
    static void portableModel(boolean on) throws IOException {
        String elements = String.join(",\n",
                element("2, 1, 6", "14, 6, 10", "west 10 0 14 5, east 10 0 14 5, north 2 11 14 16, south 2 11 14 16, "
                        + "up 2 10 14 14, down 2 12 14 16"),
                element("3, 0, 6", "13, 7, 10", "up 0 10 10 14, down 3 12 13 16, north 3 10 13 16, south 3 10 13 16, "
                        + "west 6 10 10 16, east 6 10 10 16"),
                element("3, 1, 5", "13, 6, 11", "north 0 0 10 5, south 0 5 10 10, up 3 10 13 16, down 3 10 13 16, "
                        + "west 5 11 11 16, east 5 11 11 16"),
                element("4, 7, 7.5", "5, 9, 8.5", around("11 5 12 7")),
                element("11, 7, 7.5", "12, 9, 8.5", around("11 5 12 7")),
                element("4, 9, 7.5", "12, 10, 8.5", "north 10 7 16 8, south 10 7 16 8, up 10 7 16 8, down 10 8 16 9, "
                        + "west 15 8 16 9, east 15 8 16 9"),
                on ? element("3, 7, 9", "4, 13, 10", around("14 0 15 6") + ", up 14 0 15 1")
                        : element("3, 7, 9", "4, 8, 10", around("14 0 15 1") + ", up 14 0 15 1"),
                element("6, 2, 4.5", "7, 3, 5", around("10 9 11 10") + ", up 10 9 11 10, down 12 9 13 10"),
                element("4, 2, 4.5", "5, 3, 5", around("10 9 11 10") + ", up 10 9 11 10, down 12 9 13 10"));
        String name = on ? "portable_radio_on" : "portable_radio";
        Files.writeString(MODELS.resolve(name + ".json"), """
                {
                  "parent": "minecraft:block/block",
                  "textures": {
                    "all": "worldradio:block/%1$s",
                    "particle": "worldradio:block/%1$s"
                  },
                  "elements": [
                %2$s
                  ],
                  "display": {
                    "gui": { "rotation": [30, 225, 0], "translation": [0, 2.6, 0], "scale": [1, 1, 1] },
                    "ground": { "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] },
                    "fixed": { "translation": [0, 2.5, 0], "scale": [1, 1, 1] },
                    "thirdperson_righthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.6, 0.6, 0.6] },
                    "firstperson_righthand": { "rotation": [0, 225, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45] },
                    "firstperson_lefthand": { "rotation": [0, 45, 0], "translation": [0, 3, 0], "scale": [0.45, 0.45, 0.45] }
                  }
                }
                """.formatted(name, elements));
    }

    /**
     * The speaker's front: the radio's wooden frame around one big square grille with a dust cap in the middle and a
     * ring on the cone. While its radio plays ({@code on}), the ring runs outwards as a wave, one per loop.
     */
    static BufferedImage speakerFront(boolean on, int frame) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Palette p = RECEIVER;
        fill(img, 0, 0, 15, 15, p.rim());
        fill(img, 1, 1, 14, 14, p.panel());
        for (int y = 2; y <= 13; y++) {
            for (int x = 2; x <= 13; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                boolean hole = (x + y) % 2 == 0;
                int c = hole ? p.hole() : p.cloth();
                // from the dust cap out past the corners (8.5 pixels) in one loop
                double ring = 1.5 + frame * 7.0 / FRAMES;
                if (d < 1.5) c = p.ringLo();
                else if (!on && Math.abs(d - 4.0) < 0.6 && !hole) c = p.ringLo();
                else if (on && !hole && Math.abs(d - ring) < 0.8) c = p.ringHi();
                else if (on && !hole && Math.abs(d - ring) < 1.6) c = p.ringLo();
                set(img, x, y, c);
            }
        }
        return img;
    }

    /** The new textures side by side, 12x, for a look before they go into screenshots: art/blocks/preview/. */
    static void preview() throws IOException {
        BufferedImage[][] rows = {
                {frontFrame("radio", RADIO, 0, 0), frontFrame("radio", RADIO, 1, 0), frontFrame("radio", RADIO, 3, 2),
                        frontFrame("radio", RADIO, 3, 5), caseTexture(RADIO), topTexture(RADIO)},
                {frontFrame("channel", RADIO, 0, 0), frontFrame("channel", RADIO, 1, 0), frontFrame("channel", RADIO, 1, 2),
                        frontFrame("channel", RADIO, 1, 5), caseTexture(RADIO), caseTexture(RADIO)},
                {frontFrame("receiver", RECEIVER, 0, 0), frontFrame("receiver", RECEIVER, 3, 2),
                        frontFrame("receiver", RECEIVER, 3, 5), caseTexture(RECEIVER), portableRadio(false), portableRadio(true)},
                {speakerFront(false, 0), speakerFront(true, 0), speakerFront(true, 2), speakerFront(true, 4),
                        speakerFront(true, 6), caseTexture(RECEIVER)},
                {frontFrame("amplifier", AMPLIFIER, 0, 0), frontFrame("amplifier", AMPLIFIER, 1, 0),
                        frontFrame("amplifier", AMPLIFIER, 3, 2), frontFrame("amplifier", AMPLIFIER, 3, 5),
                        caseTexture(AMPLIFIER), topTexture(AMPLIFIER)}};
        int scale = 12, gap = 12, cell = 16 * scale + gap;
        BufferedImage sheet = new BufferedImage(gap + 6 * cell, gap + rows.length * cell, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < sheet.getHeight(); y++) {
            for (int x = 0; x < sheet.getWidth(); x++) sheet.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? 0x8FA88F : 0x86A086);
        }
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                sheet.getGraphics().drawImage(rows[r][c].getScaledInstance(16 * scale, 16 * scale, java.awt.Image.SCALE_REPLICATE),
                        gap + c * cell, gap + r * cell, null);
            }
        }
        File dir = new File("art/blocks/preview");
        dir.mkdirs();
        ImageIO.write(sheet, "png", new File(dir, "textures-2.0.png"));
    }

    /** Frame colour on the rim and a slightly grained fill. */
    static BufferedImage caseTexture(Palette p) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = p.fill();
                if (((x * 7 + y * 13) % 11) == 0) c = p.light();
                if (((x * 5 + y * 3) % 17) == 0) c = p.rim();
                if (x == 0 || y == 0 || x == 15 || y == 15) c = p.rim();
                img.setRGB(x, y, 0xFF000000 | c);
            }
        }
        return img;
    }

    /**
     * The case with an antenna socket in the centre: a 6x6 plate in the light tone, a 4x4 dark ring and a 2x2 hole, and
     * two short vent slits left and right of it.
     */
    static BufferedImage topTexture(Palette p) {
        BufferedImage img = caseTexture(p);
        fill(img, 5, 5, 10, 10, p.light());
        fill(img, 6, 6, 9, 9, SLOT);
        fill(img, 7, 7, 8, 8, HOLE);
        for (int y : new int[]{6, 8}) {
            fill(img, 2, y, 3, y, SLOT);
            fill(img, 12, y, 13, y, SLOT);
        }
        return img;
    }

    /** Writes the front texture (a strip of frames when on), its .mcmeta and the block model for this level. */
    static void front(String block, Palette p, int level) throws IOException {
        String name = block + "_front_" + level;
        int frames = level == 0 ? 1 : FRAMES;
        BufferedImage strip = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) {
            BufferedImage frame = frontFrame(block, p, level, f);
            strip.getGraphics().drawImage(frame, 0, 16 * f, null);
        }
        write(name, strip);
        File meta = new File(TEXTURES, name + ".png.mcmeta");
        if (level == 0) {
            meta.delete();
        } else {
            Files.writeString(meta.toPath(), "{\n  \"animation\": {\n    \"frametime\": " + FRAMETIME + "\n  }\n}\n");
        }
        Files.writeString(MODELS.resolve(block + "_" + level + ".json"), """
                {
                  "parent": "minecraft:block/orientable",
                  "textures": {
                    "top": "worldradio:block/%1$s_top",
                    "front": "worldradio:block/%2$s",
                    "side": "worldradio:block/%1$s_side"
                  }
                }
                """.formatted(block, name));
    }

    /**
     * The channel's front: the transmitter's panel without mast and reach bars. The level meter runs across the whole
     * panel (six columns), the status LED sits under it, and a scope at the bottom shows a sine wave that runs to the
     * left while the channel sends (a flat line while it does not).
     */
    static BufferedImage channelFrame(Palette p, boolean on, int frame) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, 15, 15, p.rim());
        fill(img, 1, 1, 14, 14, p.panel());
        fill(img, 2, METER_Y0, 13, METER_Y1, METER_BG);
        for (int col = 0; col < 6; col++) {
            // the right three columns play the same bounce three frames later
            int height = on ? METER[(frame + (col / 3) * 3) % FRAMES][col % 3] : 1;
            for (int i = 0; i < height; i++) {
                set(img, 3 + 2 * col, METER_Y1 - i, !on ? OFF_BAR : i == height - 1 ? p.lit() : p.litDim());
            }
        }
        set(img, LED_X, 7, !on ? LED_OFF : frame == FRAMES - 1 ? p.litDim() : p.lit());
        // scope: five rows; one period fills its twelve pixels (so the crests are round, not pointed) and the wave
        // moves a period per loop of the strip
        fill(img, 2, 9, 13, 13, METER_BG);
        for (int x = 2; x <= 13; x++) {
            int y = on ? (int) Math.round(11 - 2 * Math.sin(2 * Math.PI * (x / 12.0 + (double) frame / FRAMES))) : 11;
            set(img, x, y, on ? p.lit() : OFF_BAR);
        }
        return img;
    }

    static BufferedImage frontFrame(String block, Palette p, int level, int frame) {
        boolean on = level > 0;
        if (block.equals("channel")) return channelFrame(p, on, frame);
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        fill(img, 0, 0, 15, 15, p.rim());
        fill(img, 1, 1, 14, 14, p.panel());
        if (block.equals("radio")) mast(img, p, p.cloth(), on, frame);
        else if (block.equals("amplifier")) mast(img, p, p.light(), on, frame);
        else grille(img, p, on, frame);

        // level meter at the top right: three thin columns in a dark window, bouncing while on
        fill(img, METER_X0, METER_Y0, METER_X1, METER_Y1, METER_BG);
        for (int col = 0; col < METER_X.length; col++) {
            int height = on ? METER[frame % FRAMES][col] : 1;
            for (int i = 0; i < height; i++) {
                int colour = !on ? OFF_BAR : i == height - 1 ? p.lit() : p.litDim();
                set(img, METER_X[col], METER_Y1 - i, colour);
            }
        }

        // reach below: three bars, lit up to the level
        for (int bar = 0; bar < BARS.length; bar++) {
            int x0 = BARS[bar][0];
            boolean lit = bar < level;
            for (int y = BAR_BOTTOM - BARS[bar][1] + 1; y <= BAR_BOTTOM; y++) {
                set(img, x0, y, lit ? p.lit() : OFF_BAR);
                set(img, x0 + 1, y, lit ? p.litDim() : OFF_BAR);
            }
        }

        // status LED: red at level 0, lit (with a short blink per loop) otherwise
        set(img, LED_X, LED_Y, !on ? LED_OFF : frame == FRAMES - 1 ? p.litDim() : p.lit());
        return img;
    }

    /** Speaker grille: a checker of holes; while on, a ring of lighter holes runs out from the cone. */
    static void grille(BufferedImage img, Palette p, boolean on, int frame) {
        double ring = frame * 6.0 / FRAMES; // 0 .. 5.25 pixels out from the cone
        for (int y = GRILLE_Y0; y <= GRILLE_Y1; y++) {
            for (int x = GRILLE_X0; x <= GRILLE_X1; x++) {
                int c;
                if ((x + y) % 2 == 0) {
                    c = p.hole();
                } else {
                    c = p.cloth();
                    if (on) {
                        double d = Math.hypot(x - CONE_X, y - CONE_Y);
                        if (Math.abs(d - ring) < 0.8) c = p.ringHi();
                        else if (Math.abs(d - ring) < 1.6) c = p.ringLo();
                    }
                }
                set(img, x, y, c);
            }
        }
    }

    /**
     * The left field of transmitter and amplifier: a small lattice mast in {@code steel} on a dark sky; while on, waves
     * in the block's lit colour run out from its tip.
     */
    static void mast(BufferedImage img, Palette p, int steel, boolean on, int frame) {
        int tipX = 4, tipY = 4;
        double ring = 1.0 + frame * 4.5 / FRAMES;
        for (int y = GRILLE_Y0; y <= GRILLE_Y1; y++) {
            for (int x = GRILLE_X0; x <= GRILLE_X1; x++) {
                int c = p.hole();
                if (on) {
                    double d = Math.hypot(x - tipX, y - tipY);
                    if (Math.abs(d - ring) < 0.55) c = p.lit();
                    else if (Math.abs(d - ring) < 1.1) c = p.litDim();
                }
                set(img, x, y, c);
            }
        }
        for (int y = tipY + 1; y <= GRILLE_Y1; y++) set(img, tipX, y, steel);
        for (int y : new int[]{8, 11}) {
            set(img, tipX - 1, y, steel);
            set(img, tipX + 1, y, steel);
        }
        fill(img, GRILLE_X0, GRILLE_Y1, GRILLE_X1, GRILLE_Y1, steel);
        set(img, tipX, tipY, on ? p.lit() : LED_OFF);
    }

    static void fill(BufferedImage img, int x0, int y0, int x1, int y1, int rgb) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) set(img, x, y, rgb);
        }
    }

    static void set(BufferedImage img, int x, int y, int rgb) {
        img.setRGB(x, y, 0xFF000000 | rgb);
    }

    static void write(String name, BufferedImage img) throws IOException {
        ImageIO.write(img, "png", new File(TEXTURES, name + ".png"));
    }
}
