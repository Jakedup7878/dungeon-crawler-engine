package crawler.render;

import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Surface;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The set of textures the dungeon is drawn with.
 *
 * <p>Every texture is generated procedurally, so the game runs with no art files.
 * A map can use its own images ({@link #forMap}); the map editor sets these up.
 * To change the built-in look for every map instead, drop a square power-of-two PNG
 * (64x64 matches the built-in ones) at {@code src/main/resources/textures/<name>.png},
 * where name is one of {@code wall}, {@code door}, {@code floor} or {@code ceiling}.
 */
public final class Textures {
    public static final int SIZE = 64;

    public final Texture wall;
    public final Texture door;
    public final Texture floor;
    public final Texture ceiling;

    public Textures(Texture wall, Texture door, Texture floor, Texture ceiling) {
        this.wall = wall;
        this.door = door;
        this.floor = floor;
        this.ceiling = ceiling;
    }

    /** PNG overrides from the classpath where present, generated textures otherwise. */
    public static Textures load() {
        return new Textures(
                loadOrGenerate("wall", Textures::stoneWall),
                loadOrGenerate("door", Textures::woodenDoor),
                loadOrGenerate("floor", Textures::stoneFloor),
                loadOrGenerate("ceiling", Textures::beamedCeiling));
    }

    private static Textures defaults;

    /** The built-in set ({@link #load()}), made once and shared. */
    public static synchronized Textures defaults() {
        if (defaults == null) {
            defaults = load();
        }
        return defaults;
    }

    /**
     * The textures for one map: its own images where it names them and they can be read,
     * the built-in ones everywhere else.
     */
    public static Textures forMap(DungeonMap map, AssetSource assets) {
        Textures base = defaults();
        return new Textures(
                custom(map, Surface.WALL, assets, base.wall),
                custom(map, Surface.DOOR, assets, base.door),
                custom(map, Surface.FLOOR, assets, base.floor),
                custom(map, Surface.CEILING, assets, base.ceiling));
    }

    public Texture get(Surface surface) {
        return switch (surface) {
            case WALL -> wall;
            case DOOR -> door;
            case FLOOR -> floor;
            case CEILING -> ceiling;
        };
    }

    private static Texture custom(DungeonMap map, Surface surface, AssetSource assets, Texture fallback) {
        String reference = map.texture(surface);
        BufferedImage image = reference == null ? null : assets.image(reference);
        return image == null ? fallback : Texture.fromAnyImage(image);
    }

    public Texture forEdge(EdgeType edge) {
        return switch (edge) {
            case DOOR -> door;
            case WALL, OPEN -> wall;
        };
    }

    private static Texture loadOrGenerate(String name, Supplier<Texture> fallback) {
        String path = "/textures/" + name + ".png";
        try (InputStream in = Textures.class.getResourceAsStream(path)) {
            if (in != null) {
                BufferedImage image = ImageIO.read(in);
                if (image != null) {
                    return Texture.fromImage(image);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Could not load " + path + " (" + e.getMessage() + "); using the built-in texture.");
        }
        return fallback.get();
    }

    // ------------------------------------------------------------ generators

    /** Grey-blue ashlar blocks with a dark outline so wall panels and corners read clearly. */
    public static Texture stoneWall() {
        int[] px = stoneBlocks();
        outline(px, SIZE, 0.45);
        return new Texture(SIZE, px);
    }

    private static int[] stoneBlocks() {
        int n = SIZE;
        int[] px = new int[n * n];
        double[] noise = stoneNoise(n, 11);
        int brickW = 32;
        int brickH = 16;
        for (int y = 0; y < n; y++) {
            int row = y / brickH;
            int ly = y % brickH;
            int shift = (row % 2) * (brickW / 2);
            for (int x = 0; x < n; x++) {
                int sx = (x + shift) % n;
                int lx = sx % brickW;
                double v = noise[y * n + x];
                if (ly == 0 || lx == 0) {
                    px[y * n + x] = rgb(34 + v * 8, 34 + v * 8, 40 + v * 8);
                    continue;
                }
                int brick = row * (n / brickW) + sx / brickW;
                double tint = (hash(brick * 31 + 7) & 0xFF) / 255.0 - 0.5;
                double light = 1 + tint * 0.22 + v * 0.10;
                if (ly == 1 || lx == 1) {
                    light += 0.18;
                } else if (ly == brickH - 1 || lx == brickW - 1) {
                    light -= 0.22;
                }
                px[y * n + x] = rgb(92 * light, 98 * light, 112 * light);
            }
        }
        return px;
    }

    /** A plank door with iron bands and a ring pull, set into the same stonework as the walls. */
    public static Texture woodenDoor() {
        int n = SIZE;
        int[] px = stoneBlocks();
        double[] noise = stoneNoise(n, 21);
        int left = 14;
        int right = 50;
        int top = 12;
        int plankW = 9;
        for (int y = top - 3; y < n; y++) {
            for (int x = left - 3; x < right + 3; x++) {
                double v = noise[y * n + x];
                boolean leaf = x >= left && x < right && y >= top;
                if (!leaf) {
                    // Dark stone door frame, lit on its outer edge.
                    boolean outer = x == left - 3 || x == right + 2 || y == top - 3;
                    double light = (outer ? 1.35 : 1.0) + v * 0.1;
                    px[y * n + x] = rgb(50 * light, 50 * light, 58 * light);
                    continue;
                }
                int plank = (x - left) / plankW;
                int lx = (x - left) % plankW;
                double phase = (hash(plank + 5) & 0xFF) / 40.0;
                double grain = Math.sin(y * 0.45 + phase + Math.sin(x * 0.9 + phase) * 1.6) * 0.07;
                double light = 1 + ((hash(plank + 9) & 0xFF) / 255.0 - 0.5) * 0.2 + grain + v * 0.06;
                double r = 122 * light;
                double g = 80 * light;
                double b = 44 * light;
                if (lx == 0) {
                    r *= 0.45;
                    g *= 0.45;
                    b *= 0.45;
                }
                if ((y >= 20 && y < 24) || (y >= 50 && y < 54)) {
                    boolean rivet = lx == plankW / 2 && (y == 21 || y == 51);
                    double m = rivet ? 1.8 : (y == 20 || y == 50 ? 1.25 : 1.0);
                    r = 66 * m;
                    g = 66 * m;
                    b = 72 * m;
                }
                double ring = Math.hypot(x - 43.5, y - 38.5);
                if (ring >= 2.0 && ring <= 3.6) {
                    r = 208;
                    g = 172;
                    b = 84;
                }
                // Shadow under the lintel.
                double shadow = Math.min(1, 0.55 + (y - top) * 0.09);
                px[y * n + x] = rgb(r * shadow, g * shadow, b * shadow);
            }
        }
        outline(px, n, 0.45);
        return new Texture(n, px);
    }

    /** Four flagstones per cell, with a darker seam on the cell border so the grid is easy to count. */
    public static Texture stoneFloor() {
        int n = SIZE;
        int[] px = new int[n * n];
        double[] noise = stoneNoise(n, 31);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (x == 0 || y == 0 || x == n - 1 || y == n - 1) {
                    px[y * n + x] = rgb(24, 22, 20);
                    continue;
                }
                int slab = (y / 32) * 2 + x / 32;
                double light = 1 + noise[y * n + x] * 0.12 + ((hash(slab + 3) & 0xFF) / 255.0 - 0.5) * 0.14;
                if (x % 32 == 0 || y % 32 == 0) {
                    light *= 0.6;
                }
                px[y * n + x] = rgb(80 * light, 74 * light, 66 * light);
            }
        }
        return new Texture(n, px);
    }

    /** Dark plaster with timber beams along the cell borders. */
    public static Texture beamedCeiling() {
        int n = SIZE;
        int[] px = new int[n * n];
        double[] noise = stoneNoise(n, 41);
        int beam = 4;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double v = noise[y * n + x];
                int edge = Math.min(Math.min(x, y), Math.min(n - 1 - x, n - 1 - y));
                if (edge < beam) {
                    double light = (edge == 0 ? 0.55 : edge == beam - 1 ? 0.8 : 1.0) + v * 0.08;
                    px[y * n + x] = rgb(70 * light, 52 * light, 36 * light);
                } else {
                    double light = 1 + v * 0.12;
                    px[y * n + x] = rgb(46 * light, 48 * light, 56 * light);
                }
            }
        }
        return new Texture(n, px);
    }

    // ------------------------------------------------------------ helpers

    /** Darkens the outermost ring of pixels. */
    private static void outline(int[] px, int n, double factor) {
        for (int i = 0; i < n; i++) {
            darken(px, i, factor);                 // top row
            darken(px, (n - 1) * n + i, factor);   // bottom row
            if (i > 0 && i < n - 1) {
                darken(px, i * n, factor);         // left column
                darken(px, i * n + n - 1, factor); // right column
            }
        }
    }

    private static void darken(int[] px, int i, double factor) {
        int c = px[i];
        px[i] = rgb(((c >> 16) & 0xFF) * factor, ((c >> 8) & 0xFF) * factor, (c & 0xFF) * factor);
    }

    /** Mottled stone: two octaves of tileable value noise plus grain, roughly in -1..1. */
    private static double[] stoneNoise(int n, long seed) {
        double[] coarse = valueNoise(n, 16, seed);
        double[] fine = valueNoise(n, 4, seed + 1);
        Random grain = new Random(seed + 2);
        double[] out = new double[n * n];
        for (int i = 0; i < out.length; i++) {
            out[i] = (coarse[i] - 0.5) * 1.2 + (fine[i] - 0.5) * 0.6 + (grain.nextDouble() - 0.5) * 0.35;
        }
        return out;
    }

    /** Tileable value noise in 0..1 with a lattice point every {@code cell} pixels. */
    private static double[] valueNoise(int n, int cell, long seed) {
        int lattice = n / cell;
        Random rnd = new Random(seed);
        double[] points = new double[lattice * lattice];
        for (int i = 0; i < points.length; i++) {
            points[i] = rnd.nextDouble();
        }
        double[] out = new double[n * n];
        for (int y = 0; y < n; y++) {
            int y0 = y / cell;
            int y1 = (y0 + 1) % lattice;
            double fy = smooth((y % cell) / (double) cell);
            for (int x = 0; x < n; x++) {
                int x0 = x / cell;
                int x1 = (x0 + 1) % lattice;
                double fx = smooth((x % cell) / (double) cell);
                double top = lerp(points[y0 * lattice + x0], points[y0 * lattice + x1], fx);
                double bottom = lerp(points[y1 * lattice + x0], points[y1 * lattice + x1], fx);
                out[y * n + x] = lerp(top, bottom, fy);
            }
        }
        return out;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int hash(int v) {
        v ^= v >>> 16;
        v *= 0x7feb352d;
        v ^= v >>> 15;
        v *= 0x846ca68b;
        v ^= v >>> 16;
        return v;
    }

    private static int rgb(double r, double g, double b) {
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(double v) {
        return v <= 0 ? 0 : v >= 255 ? 255 : (int) v;
    }
}
