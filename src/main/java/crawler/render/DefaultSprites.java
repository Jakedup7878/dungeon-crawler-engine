package crawler.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;

/** The built-in looks for objects, drawn in code: a spinning blue top for NPCs, and a chest. */
final class DefaultSprites {
    private static final int TOP_SIZE = 64;
    private static final int TOP_FRAMES = 8;
    /** Stripes around the top; the animation moves them by one stripe per loop. */
    private static final int TOP_STRIPES = 6;

    private DefaultSprites() {
    }

    /** A blue spinning top with spiral stripes, wobbling slightly as it spins. */
    static Sprite spinningTop() {
        List<BufferedImage> frames = new ArrayList<>();
        for (int f = 0; f < TOP_FRAMES; f++) {
            double t = f / (double) TOP_FRAMES;
            frames.add(topFrame(t, Math.sin(2 * Math.PI * t)));
        }
        return Sprite.animated(frames, 14);
    }

    /** Radius of the top's silhouette at pixel row y, or 0 where there is none. */
    private static double topRadius(int y) {
        if (y < 2) {
            return 0;
        }
        if (y < 5) {
            return 4.5; // knob
        }
        if (y < 13) {
            return 2.6; // handle
        }
        double t = (y - 13) / 48.0;
        if (t > 1) {
            return 0;
        }
        if (t < 0.2) {
            return 8 + 20 * Math.sin(t / 0.2 * Math.PI / 2); // rounded shoulder
        }
        return Math.max(0.8, 28 * Math.pow(1 - (t - 0.2) / 0.8, 1.2)); // taper to the point
    }

    private static BufferedImage topFrame(double phase, double wobble) {
        int size = TOP_SIZE;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int[] px = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        // Soft shadow on the floor under the point.
        for (int y = 56; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5 - 32) / 15.0;
                double dy = (y + 0.5 - 61) / 2.6;
                double d = dx * dx + dy * dy;
                if (d < 1) {
                    px[y * size + x] = (int) (110 * (1 - d)) << 24;
                }
            }
        }

        for (int y = 0; y < 62; y++) {
            double r = topRadius(y);
            if (r <= 0) {
                continue;
            }
            boolean topRow = topRadius(y - 1) <= 0 || (y == 13);
            // Leaning: the higher up, the further the wobble pushes it sideways.
            double centreX = 32 + wobble * 2.2 * (61 - y) / 60.0;
            for (int x = 0; x < size; x++) {
                double u = (x + 0.5 - centreX) / r;
                if (Math.abs(u) > 1) {
                    continue;
                }
                double angle = Math.asin(u); // where on the round surface this pixel is
                double light = 0.3 + 0.7 * Math.max(0, Math.cos(angle + 0.45));
                double red;
                double green;
                double blue;
                if (y < 13) {
                    red = 70;
                    green = 92;
                    blue = 160;
                } else if (y >= 21 && y <= 23) {
                    red = 236; // gold band round the widest part
                    green = 190;
                    blue = 72;
                } else {
                    double band = angle / (2 * Math.PI) * TOP_STRIPES + phase + (y - 13) * 0.035;
                    boolean stripe = band - Math.floor(band) < 0.45;
                    red = stripe ? 135 : 30;
                    green = stripe ? 195 : 82;
                    blue = stripe ? 255 : 214;
                }
                double shine = Math.pow(Math.max(0, Math.cos(angle + 0.6)), 24) * 200;
                double edge = Math.abs(u) > 1 - 1.2 / r || topRow ? 0.45 : 1.0;
                px[y * size + x] = 0xFF000000
                        | channel((red * light + shine) * edge) << 16
                        | channel((green * light + shine) * edge) << 8
                        | channel((blue * light + shine) * edge);
            }
        }
        return image;
    }

    /** A wooden chest with iron bands, closed (with a lock) or open (with gold inside). */
    static Sprite chest(boolean open) {
        int width = 64;
        int height = 48;
        Color wood = new Color(128, 78, 36);
        Color woodDark = new Color(84, 48, 20);
        Color woodLight = new Color(158, 100, 48);
        Color iron = new Color(92, 96, 108);
        Color ironLight = new Color(156, 162, 176);
        Color gold = new Color(238, 192, 70);
        Color outline = new Color(40, 24, 10);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 90));
            g.fillOval(3, 41, 58, 7);

            int bx = 6;
            int by = 22;
            int bw = 52;
            int bh = 22;
            if (open) {
                // The lid has swung back, so we see its inside above the box.
                Polygon lid = new Polygon(new int[]{7, 57, 53, 11}, new int[]{23, 23, 3, 3}, 4);
                g.setColor(woodDark);
                g.fill(lid);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(lid);
                g.setColor(iron);
                g.fillRect(14, 4, 4, 19);
                g.fillRect(46, 4, 4, 19);
            }

            g.setColor(wood);
            g.fillRect(bx, by, bw, bh);
            g.setColor(woodDark);
            for (int y = by + 7; y < by + bh; y += 7) {
                g.drawLine(bx, y, bx + bw - 1, y);
            }

            if (open) {
                g.setColor(new Color(28, 16, 6));
                g.fillRect(bx + 2, by, bw - 4, 5);
                for (int i = 0; i < 6; i++) {
                    int cx = 12 + i * 7;
                    int cy = by - 3 + (i % 2) * 2;
                    g.setColor(gold);
                    g.fillOval(cx, cy, 9, 6);
                    g.setColor(new Color(255, 236, 150));
                    g.fillOval(cx + 2, cy + 1, 3, 2);
                }
            } else {
                RoundRectangle2D lid = new RoundRectangle2D.Double(4, 6, 56, 19, 20, 16);
                g.setColor(woodLight);
                g.fill(lid);
                g.setColor(new Color(190, 130, 70));
                g.drawLine(10, 9, 54, 9);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(lid);
            }

            // Iron straps.
            int strapTop = open ? by : 6;
            for (int sx : new int[]{13, 46}) {
                g.setColor(iron);
                g.fillRect(sx, strapTop, 5, by + bh - strapTop);
                g.setColor(ironLight);
                g.drawLine(sx + 1, strapTop + 1, sx + 1, by + bh - 1);
            }

            if (!open) {
                g.setColor(gold);
                g.fillRect(28, 17, 8, 10);
                g.setColor(outline);
                g.setStroke(new BasicStroke(1f));
                g.drawRect(28, 17, 8, 10);
                g.fillRect(31, 20, 2, 4);
            }

            g.setColor(outline);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRect(bx, by, bw, bh);
        } finally {
            g.dispose();
        }
        return Sprite.of(image);
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, value));
    }
}
