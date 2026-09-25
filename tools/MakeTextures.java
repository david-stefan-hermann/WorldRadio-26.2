import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Block textures (16x16) and models. Radio = wooden case, amplifier = steel case; both fronts have a speaker grille on
 * the left (x 2..6) and, past a one pixel divider, a small level meter at the top right and the reach below it (three
 * bars, lit up to the blockstate level) next to a status LED (red at level 0), all inside a one pixel panel margin.
 * Level 0 (no station / no signal) is one still frame; levels 1 to 3 are animated: the meter bounces and the grille
 * sends rings out from the cone. The top of both blocks carries an antenna socket.
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

    static final Palette RADIO = new Palette(0x5A3D24, 0x6B4A2E, 0x7A5636, 0x2B2B2B, 0x4ADE80, 0x1F6B3A,
            0x151515, 0x303030, 0x7A7A7A, 0x4E4E4E);
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
        write("amplifier_side", caseTexture(AMPLIFIER));
        write("amplifier_top", topTexture(AMPLIFIER));
        for (int level = 0; level <= MAX_LEVEL; level++) {
            front("radio", RADIO, level);
            front("amplifier", AMPLIFIER, level);
        }
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
            BufferedImage frame = frontFrame(p, level, f);
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

    static BufferedImage frontFrame(Palette p, int level, int frame) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        boolean on = level > 0;
        fill(img, 0, 0, 15, 15, p.rim());
        fill(img, 1, 1, 14, 14, p.panel());
        grille(img, p, on, frame);

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
