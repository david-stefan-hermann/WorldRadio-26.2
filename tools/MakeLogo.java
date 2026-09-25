import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * The mod logo: the switched-on radio block drawn from its own textures (radio_front_3, radio_side, radio_top) as an
 * orthographic 3D view, 512x512 with a transparent background. Variants: "inventory" (the item's view, front on the
 * right), "front" (front on the left, turned towards the viewer), "antenna" (front plus a copper lightning rod on the
 * socket and signal arcs). Previews on dark and light backgrounds and at launcher sizes go to art/logo/.
 * Run: java tools/MakeLogo.java [variant to install] (from the project folder); without an argument only the previews
 * are written.
 */
public class MakeLogo {
    static final Path TEXTURES = Path.of("src/main/resources/assets/worldradio/textures/block");
    static final Path ICON = Path.of("src/main/resources/assets/worldradio/icon.png");
    static final Path ART = Path.of("art/logo");
    static final Path CLIENT_JAR = Path.of(System.getProperty("user.home"),
            ".gradle/caches/fabric-loom/26.2/minecraft-client.jar");
    static final int SIZE = 512;
    static final int MARGIN = 20;
    static final int SUPERSAMPLE = 4;
    /** Frame of the animated front: the grille ring is halfway out, the meter is up. */
    static final int FRONT_FRAME = 2;
    static final int SIGNAL = 0x4ADE80;

    /** Face order: down, up, north, south, west, east. */
    static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    /** One face's texture: an image and the uv rectangle (in texels; u0 > u1 mirrors) the face maps onto. */
    record FaceTex(BufferedImage image, double u0, double v0, double u1, double v1) {
        int sample(double tu, double tv) {
            int u = (int) Math.floor(u0 + tu * (u1 - u0) - (u1 < u0 ? 1e-9 : -1e-9));
            int v = (int) Math.floor(v0 + tv * (v1 - v0) - (v1 < v0 ? 1e-9 : -1e-9));
            u = Math.clamp(u, 0, image.getWidth() - 1);
            v = Math.clamp(v, 0, image.getHeight() - 1);
            return image.getRGB(u, v);
        }
    }

    /** A model element in block pixels (0..16), faces in the order above (null = no face). */
    record Box(double x0, double y0, double z0, double x1, double y1, double z1, FaceTex[] faces) {
    }

    /** View: yaw around Y, then pitch around X (like an item's display rotation), plus the signal arcs. */
    record View(String name, double yaw, double pitch, List<Box> boxes, boolean arcs) {
    }

    public static void main(String[] args) throws IOException {
        BufferedImage front = read(TEXTURES.resolve("radio_front_3.png")).getSubimage(0, FRONT_FRAME * 16, 16, 16);
        BufferedImage side = read(TEXTURES.resolve("radio_side.png"));
        BufferedImage top = read(TEXTURES.resolve("radio_top.png"));
        BufferedImage rod;
        try (ZipFile jar = new ZipFile(CLIENT_JAR.toFile());
             InputStream in = jar.getInputStream(jar.getEntry("assets/minecraft/textures/block/lightning_rod.png"))) {
            rod = ImageIO.read(in);
        }

        Box radio = new Box(0, 0, 0, 16, 16, 16, new FaceTex[]{
                full(top), full(top), full(front), full(side), full(side), full(side)});
        // The vanilla lightning rod (template_lightning_rod.json), standing on the socket.
        FaceTex head = new FaceTex(rod, 0, 0, 4, 4);
        Box rodHead = new Box(6, 28, 6, 10, 32, 10, new FaceTex[]{
                head, new FaceTex(rod, 4, 4, 0, 0), head, head, head, head});
        FaceTex shaft = new FaceTex(rod, 0, 4, 2, 16);
        Box rodShaft = new Box(7, 16, 7, 9, 28, 9, new FaceTex[]{
                new FaceTex(rod, 0, 4, 2, 6), null, shaft, shaft, shaft, shaft});

        List<View> views = List.of(
                new View("inventory", 225, 30, List.of(radio), false),
                new View("front", 150, 24, List.of(radio), false),
                new View("antenna", 150, 24, List.of(radio, rodShaft, rodHead), true));

        Files.createDirectories(ART);
        List<BufferedImage> logos = new ArrayList<>();
        for (View view : views) {
            BufferedImage logo = render(view);
            ImageIO.write(logo, "png", ART.resolve(view.name() + ".png").toFile());
            logos.add(logo);
        }
        ImageIO.write(sheet(views, logos), "png", ART.resolve("preview.png").toFile());

        if (args.length > 0) {
            int index = views.stream().map(View::name).toList().indexOf(args[0]);
            if (index < 0) throw new IllegalArgumentException("unknown variant " + args[0]);
            ImageIO.write(logos.get(index), "png", ICON.toFile());
            System.out.println("installed " + args[0] + " as " + ICON);
        }
    }

    static BufferedImage read(Path path) throws IOException {
        return ImageIO.read(path.toFile());
    }

    static FaceTex full(BufferedImage image) {
        return new FaceTex(image, 0, 0, 16, 16);
    }

    // ---------------------------------------------------------------- rendering

    /** Rotates a vector from model space (block centred at the origin) into view space: yaw around Y, then pitch. */
    static double[] toView(View view, double x, double y, double z) {
        double a = Math.toRadians(view.yaw()), b = Math.toRadians(view.pitch());
        double x1 = x * Math.cos(a) + z * Math.sin(a), z1 = -x * Math.sin(a) + z * Math.cos(a);
        return new double[]{x1, y * Math.cos(b) - z1 * Math.sin(b), y * Math.sin(b) + z1 * Math.cos(b)};
    }

    static double[] toModel(View view, double x, double y, double z) {
        double a = Math.toRadians(view.yaw()), b = Math.toRadians(view.pitch());
        double y1 = y * Math.cos(b) + z * Math.sin(b), z1 = -y * Math.sin(b) + z * Math.cos(b);
        return new double[]{x * Math.cos(a) - z1 * Math.sin(a), y1, x * Math.sin(a) + z1 * Math.cos(a)};
    }

    static BufferedImage render(View view) {
        // Fit every box corner (and the arcs beside the rod's head) into the square.
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (Box box : view.boxes()) {
            for (int c = 0; c < 8; c++) {
                double[] p = toView(view, ((c & 1) == 0 ? box.x0() : box.x1()) - 8,
                        ((c & 2) == 0 ? box.y0() : box.y1()) - 8, ((c & 4) == 0 ? box.z0() : box.z1()) - 8);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]);
                maxY = Math.max(maxY, p[1]);
            }
        }
        double[] tip = toView(view, 0, 30 - 8, 0);
        if (view.arcs()) {
            minX = Math.min(minX, tip[0] - 9);
            maxX = Math.max(maxX, tip[0] + 9);
            maxY = Math.max(maxY, tip[1] + 5);
        }
        double scale = Math.min((SIZE - 2 * MARGIN) / (maxX - minX), (SIZE - 2 * MARGIN) / (maxY - minY));
        double cx = SIZE / 2.0 - (minX + maxX) / 2 * scale, cy = SIZE / 2.0 + (minY + maxY) / 2 * scale;

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int n = SUPERSAMPLE;
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double r = 0, g = 0, bl = 0, a = 0;
                for (int sy = 0; sy < n; sy++) {
                    for (int sx = 0; sx < n; sx++) {
                        double vx = (px + (sx + 0.5) / n - cx) / scale, vy = (cy - py - (sy + 0.5) / n) / scale;
                        int argb = trace(view, vx, vy);
                        double alpha = (argb >>> 24) / 255.0;
                        r += ((argb >> 16) & 0xFF) * alpha;
                        g += ((argb >> 8) & 0xFF) * alpha;
                        bl += (argb & 0xFF) * alpha;
                        a += alpha;
                    }
                }
                if (a <= 0) continue;
                int alpha = (int) Math.round(a / (n * n) * 255);
                out.setRGB(px, py, alpha << 24 | (int) Math.round(r / a) << 16 | (int) Math.round(g / a) << 8
                        | (int) Math.round(bl / a));
            }
        }
        if (view.arcs()) arcs(out, cx + tip[0] * scale, cy - tip[1] * scale, scale);
        return out;
    }

    /** Colour where the view ray through (vx, vy) first hits a box, shaded by the face's direction; 0 = nothing. */
    static int trace(View view, double vx, double vy) {
        double[] o = toModel(view, vx, vy, 100);
        double[] d = toModel(view, 0, 0, -1);
        double best = Double.MAX_VALUE;
        int color = 0;
        for (Box box : view.boxes()) {
            double[] lo = {box.x0() - 8, box.y0() - 8, box.z0() - 8}, hi = {box.x1() - 8, box.y1() - 8, box.z1() - 8};
            double tMin = -Double.MAX_VALUE, tMax = Double.MAX_VALUE;
            int axis = -1;
            boolean miss = false;
            for (int i = 0; i < 3; i++) {
                if (Math.abs(d[i]) < 1e-12) {
                    if (o[i] < lo[i] || o[i] > hi[i]) miss = true;
                    continue;
                }
                double t0 = (lo[i] - o[i]) / d[i], t1 = (hi[i] - o[i]) / d[i];
                if (t0 > t1) {
                    double t = t0;
                    t0 = t1;
                    t1 = t;
                }
                if (t0 > tMin) {
                    tMin = t0;
                    axis = i;
                }
                tMax = Math.min(tMax, t1);
            }
            if (miss || axis < 0 || tMin > tMax || tMin >= best) continue;
            int face = switch (axis) {
                case 0 -> d[0] > 0 ? WEST : EAST;
                case 1 -> d[1] > 0 ? DOWN : UP;
                default -> d[2] > 0 ? NORTH : SOUTH;
            };
            FaceTex tex = box.faces()[face];
            if (tex == null) continue;
            double x = o[0] + d[0] * tMin + 8, y = o[1] + d[1] * tMin + 8, z = o[2] + d[2] * tMin + 8;
            double w = box.x1() - box.x0(), h = box.y1() - box.y0(), dz = box.z1() - box.z0();
            double tu, tv;
            switch (face) {
                case UP -> {
                    tu = (x - box.x0()) / w;
                    tv = (z - box.z0()) / dz;
                }
                case DOWN -> {
                    tu = (x - box.x0()) / w;
                    tv = (box.z1() - z) / dz;
                }
                case NORTH -> {
                    tu = (box.x1() - x) / w;
                    tv = (box.y1() - y) / h;
                }
                case SOUTH -> {
                    tu = (x - box.x0()) / w;
                    tv = (box.y1() - y) / h;
                }
                case WEST -> {
                    tu = (z - box.z0()) / dz;
                    tv = (box.y1() - y) / h;
                }
                default -> {
                    tu = (box.z1() - z) / dz;
                    tv = (box.y1() - y) / h;
                }
            }
            int argb = tex.sample(Math.clamp(tu, 0, 1), Math.clamp(tv, 0, 1));
            if ((argb >>> 24) < 128) continue;
            best = tMin;
            color = shade(argb, shadeOf(view, face));
        }
        return color;
    }

    /** Light from the top left: top faces full, faces turned left a bit darker, faces turned right darker still. */
    static double shadeOf(View view, int face) {
        double[] n = switch (face) {
            case DOWN -> new double[]{0, -1, 0};
            case UP -> new double[]{0, 1, 0};
            case NORTH -> new double[]{0, 0, -1};
            case SOUTH -> new double[]{0, 0, 1};
            case WEST -> new double[]{-1, 0, 0};
            default -> new double[]{1, 0, 0};
        };
        double[] v = toView(view, n[0], n[1], n[2]);
        if (v[1] > 0.5) return 1.0;
        if (v[1] < -0.5) return 0.5;
        return v[0] < 0 ? 0.86 : 0.66;
    }

    static int shade(int argb, double f) {
        int r = (int) Math.round(((argb >> 16) & 0xFF) * f), g = (int) Math.round(((argb >> 8) & 0xFF) * f);
        int b = (int) Math.round((argb & 0xFF) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Signal arcs left and right of the rod's head, drawn on a grid of one block texel so they look like the textures:
     * three rings, each fainter than the one inside it.
     */
    static void arcs(BufferedImage out, double tipX, double tipY, double texel) {
        int cell = (int) Math.round(texel);
        int[] radius = {3, 5, 7};
        int[] alpha = {255, 200, 140};
        for (int gy = -9; gy <= 9; gy++) {
            for (int gx = -9; gx <= 9; gx++) {
                double dist = Math.hypot(gx, gy);
                double angle = Math.toDegrees(Math.atan2(-gy, Math.abs(gx)));
                if (Math.abs(angle) > 38) continue;
                for (int i = 0; i < radius.length; i++) {
                    if (Math.abs(dist - radius[i]) >= 0.5) continue;
                    int x0 = (int) Math.round(tipX + (gx - 0.5) * cell), y0 = (int) Math.round(tipY + (gy - 0.5) * cell);
                    fillBlend(out, x0, y0, cell, cell, alpha[i] << 24 | SIGNAL);
                }
            }
        }
    }

    static void fillBlend(BufferedImage out, int x0, int y0, int w, int h, int argb) {
        for (int y = Math.max(0, y0); y < Math.min(out.getHeight(), y0 + h); y++) {
            for (int x = Math.max(0, x0); x < Math.min(out.getWidth(), x0 + w); x++) {
                out.setRGB(x, y, over(argb, out.getRGB(x, y)));
            }
        }
    }

    /** Source-over blend of two ARGB colours (not premultiplied). */
    static int over(int src, int dst) {
        double sa = (src >>> 24) / 255.0, da = (dst >>> 24) / 255.0;
        double a = sa + da * (1 - sa);
        if (a <= 0) return 0;
        int[] c = new int[3];
        for (int i = 0; i < 3; i++) {
            int shift = 16 - 8 * i;
            double s = (src >> shift) & 0xFF, d = (dst >> shift) & 0xFF;
            c[i] = (int) Math.round((s * sa + d * da * (1 - sa)) / a);
        }
        return (int) Math.round(a * 255) << 24 | c[0] << 16 | c[1] << 8 | c[2];
    }

    // ---------------------------------------------------------------- preview

    /** Each variant on a dark and a light background, plus at 64 and 32 px as launchers show it. */
    static BufferedImage sheet(List<View> views, List<BufferedImage> logos) {
        int cellW = 2 * 256 + 3 * 16 + 64 + 16 + 32 + 16;
        int cellH = 256 + 2 * 16;
        BufferedImage out = new BufferedImage(cellW, cellH * views.size(), BufferedImage.TYPE_INT_ARGB);
        int[] backgrounds = {0xFF1E1F22, 0xFFEDEDED};
        for (int i = 0; i < views.size(); i++) {
            int y = i * cellH;
            fill(out, 0, y, cellW, cellH, 0xFF3A3A3A);
            for (int b = 0; b < 2; b++) {
                int x = 16 + b * (256 + 16);
                fill(out, x, y + 16, 256, 256, backgrounds[b]);
                blit(out, scaled(logos.get(i), 256), x, y + 16);
            }
            int x = 16 + 2 * (256 + 16);
            fill(out, x, y + 16, 64, 64, backgrounds[0]);
            blit(out, scaled(logos.get(i), 64), x, y + 16);
            fill(out, x, y + 96, 64, 64, backgrounds[1]);
            blit(out, scaled(logos.get(i), 64), x, y + 96);
            fill(out, x + 80, y + 16, 32, 32, backgrounds[0]);
            blit(out, scaled(logos.get(i), 32), x + 80, y + 16);
            fill(out, x + 80, y + 96, 32, 32, backgrounds[1]);
            blit(out, scaled(logos.get(i), 32), x + 80, y + 96);
        }
        return out;
    }

    /** Box-filter downscale by an integer factor. */
    static BufferedImage scaled(BufferedImage image, int size) {
        int f = image.getWidth() / size;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double r = 0, g = 0, b = 0, a = 0;
                for (int j = 0; j < f; j++) {
                    for (int i = 0; i < f; i++) {
                        int c = image.getRGB(x * f + i, y * f + j);
                        double al = (c >>> 24) / 255.0;
                        r += ((c >> 16) & 0xFF) * al;
                        g += ((c >> 8) & 0xFF) * al;
                        b += (c & 0xFF) * al;
                        a += al;
                    }
                }
                if (a <= 0) continue;
                out.setRGB(x, y, (int) Math.round(a / (f * f) * 255) << 24 | (int) Math.round(r / a) << 16
                        | (int) Math.round(g / a) << 8 | (int) Math.round(b / a));
            }
        }
        return out;
    }

    static void fill(BufferedImage out, int x0, int y0, int w, int h, int argb) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) out.setRGB(x, y, argb);
    }

    static void blit(BufferedImage out, BufferedImage image, int x0, int y0) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setRGB(x0 + x, y0 + y, over(image.getRGB(x, y), out.getRGB(x0 + x, y0 + y)));
            }
        }
    }
}
