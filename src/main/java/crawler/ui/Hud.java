package crawler.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** The game screen's colours, fonts and small drawing helpers, shared by all its parts. */
final class Hud {
    static final Color BACKGROUND = new Color(8, 8, 12);
    static final Color FRAME = new Color(118, 128, 160);
    static final Color FRAME_INNER = new Color(40, 44, 58);
    static final Color PANEL = new Color(6, 8, 16, 232);
    static final Color TEXT = new Color(222, 224, 232);
    static final Color DIM = new Color(128, 134, 152);
    static final Color HIGHLIGHT = new Color(255, 214, 92);
    static final Color GOOD = new Color(110, 210, 120);
    static final Color BAD = new Color(240, 80, 70);
    static final Font FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    static final Font SMALL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);
    static final Font BIG_FONT = new Font(Font.MONOSPACED, Font.BOLD, 28);

    private Hud() {
    }

    /** The double border every box on the screen has. */
    static void drawFrame(Graphics2D g, Rectangle r) {
        g.setStroke(new BasicStroke(2f));
        g.setColor(FRAME);
        g.drawRect(r.x - 4, r.y - 4, r.width + 7, r.height + 7);
        g.setStroke(new BasicStroke(1f));
        g.setColor(FRAME_INNER);
        g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
    }

    /** A dark, see-through box with a border, for text over the 3D view. */
    static void panel(Graphics2D g, Rectangle r) {
        g.setColor(PANEL);
        g.fill(r);
        drawFrame(g, r);
    }

    /** Cuts text down with "..." so it fits a width in the current font. */
    static String fit(Graphics2D g, String text, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && fm.stringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    /** Word-wraps text to a pixel width; line breaks in the text are kept. */
    static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(candidate) > width && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** Text centred on a point, in the current font. */
    static void centered(Graphics2D g, String text, int centreX, int baseline) {
        g.drawString(text, centreX - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }

    /**
     * An image fitted inside a box, keeping its shape, standing on the box's bottom edge.
     * Small pixel art is enlarged with sharp pixels; big pictures are smoothed.
     *
     * @return where it was drawn
     */
    static Rectangle drawFitted(Graphics2D graphics, BufferedImage image, Rectangle box, double maxScale) {
        double scale = Math.min(maxScale,
                Math.min(box.width / (double) image.getWidth(), box.height / (double) image.getHeight()));
        int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
        Rectangle at = new Rectangle(box.x + (box.width - w) / 2, box.y + box.height - h, w, h);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, at.x, at.y, at.width, at.height, null);
        g.dispose();
        return at;
    }

    /** A health bar: green when healthy, yellow, then red when low. */
    static void bar(Graphics2D g, int x, int y, int width, int height, int value, int max) {
        double part = max <= 0 ? 0 : Math.max(0, Math.min(1, value / (double) max));
        g.setColor(new Color(30, 32, 40));
        g.fillRect(x, y, width, height);
        g.setColor(part > 0.5 ? GOOD : part > 0.25 ? HIGHLIGHT : BAD);
        g.fillRect(x, y, (int) Math.round(width * part), height);
        g.setColor(FRAME_INNER);
        g.drawRect(x, y, width - 1, height - 1);
    }
}
