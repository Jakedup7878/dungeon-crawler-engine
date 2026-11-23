package crawler.editor;

import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.Surface;
import crawler.render.AssetSource;
import crawler.render.DungeonRenderer;
import crawler.render.ObjectLooks;
import crawler.render.Textures;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The panel beside the map: a live first-person preview drawn by the game's own
 * renderer (with the map's textures and objects), help for the current tool, map
 * statistics and problems.
 */
final class SidePanel extends JComponent {
    static final int PANEL_WIDTH = 352;
    private static final int PREVIEW_W = 320;
    private static final int PREVIEW_H = 240;
    private static final int PAD = 16;

    private static final Color BACKGROUND = new Color(18, 19, 26);
    private static final Color FRAME = new Color(70, 80, 105);
    private static final Color HEADER = new Color(128, 140, 170);
    private static final Color TEXT = new Color(222, 226, 236);
    private static final Color DIM = new Color(140, 146, 164);
    private static final Color HIGHLIGHT = new Color(255, 214, 92);
    private static final Color GOOD = new Color(120, 210, 130);
    private static final Color WARN = new Color(255, 150, 90);
    private static final Font HEADER_FONT = new Font(Font.DIALOG, Font.BOLD, 11);
    private static final Font TEXT_FONT = new Font(Font.DIALOG, Font.PLAIN, 13);
    private static final Font BOLD_FONT = new Font(Font.DIALOG, Font.BOLD, 13);

    private final MapDocument doc;
    private final AssetSource assets;
    private final ObjectLooks looks;
    private final DungeonRenderer renderer = new DungeonRenderer(PREVIEW_W, PREVIEW_H, Textures.defaults());
    /** Redraws the preview so the spinning tops spin. */
    private final Timer animation = new Timer(50, e -> repaint(PAD, 0, PREVIEW_W, PREVIEW_H + 40));
    private final long startNanos = System.nanoTime();
    /** The texture references the renderer's textures were made from; null forces a rebuild. */
    private Map<Surface, String> texturesMadeFrom;
    private Tool tool = Tool.ROOM;
    private int previewX;
    private int previewY;
    private Direction previewFacing = Direction.NORTH;

    SidePanel(MapDocument doc, AssetSource assets, ObjectLooks looks) {
        this.doc = doc;
        this.assets = assets;
        this.looks = looks;
        setOpaque(true);
        setPreferredSize(new Dimension(PANEL_WIDTH, 640));
        doc.addListener(this::repaint);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        animation.start();
    }

    @Override
    public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }

    /** The images behind the textures may have changed (another map was opened). */
    void assetsChanged() {
        texturesMadeFrom = null;
        repaint();
    }

    void setTool(Tool tool) {
        this.tool = tool;
        repaint();
    }

    void setPreview(int x, int y, Direction facing) {
        previewX = x;
        previewY = y;
        previewFacing = facing;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(FRAME);
            g.drawLine(0, 0, 0, getHeight());

            DungeonMap map = doc.map();
            MapCheck check = doc.check();
            int x = PAD;
            int y = PAD;
            int textWidth = getWidth() - 2 * PAD;

            y = header(g, "3D PREVIEW", x, y);
            if (!map.textures().equals(texturesMadeFrom)) {
                renderer.setTextures(Textures.forMap(map, assets));
                texturesMadeFrom = new EnumMap<>(Surface.class);
                texturesMadeFrom.putAll(map.textures());
            }
            double seconds = (System.nanoTime() - startNanos) / 1e9;
            renderer.render(map, previewX + 0.5, previewY + 0.5, previewFacing.angle(),
                    looks.billboards(map, object -> false), seconds);
            g.drawImage(renderer.frame(), x, y, null);
            g.setColor(FRAME);
            g.drawRect(x - 1, y - 1, PREVIEW_W + 1, PREVIEW_H + 1);
            y += PREVIEW_H + 20;
            y = line(g, "From " + previewX + ", " + previewY + ", facing " + previewFacing.displayName(), TEXT_FONT, TEXT, x, y);
            y = line(g, "Point at a cell to look from it. Q / E turn.", TEXT_FONT, DIM, x, y);
            y += 12;

            y = header(g, "TOOL", x, y);
            y = line(g, tool.label + "   (" + tool.shortcut() + ")", BOLD_FONT, HIGHLIGHT, x, y);
            y = paragraph(g, tool.primaryHint, TEXT_FONT, TEXT, x, y, textWidth);
            y = paragraph(g, tool.secondaryHint, TEXT_FONT, DIM, x, y, textWidth);
            y += 12;

            y = header(g, "MAP", x, y);
            y = line(g, fit(g, map.name(), textWidth), BOLD_FONT, HIGHLIGHT, x, y);
            y = line(g, map.width() + " x " + map.height() + " cells, " + check.doors()
                    + (check.doors() == 1 ? " door" : " doors"), TEXT_FONT, TEXT, x, y);
            y = line(g, check.floorCells() + " floor cells, " + check.reachableCells() + " reachable",
                    TEXT_FONT, TEXT, x, y);
            y = line(g, count(check.events(), "event") + ", " + count(check.chests(), "chest") + ", "
                    + count(check.npcs(), "NPC"), TEXT_FONT, TEXT, x, y);
            List<String> custom = new ArrayList<>();
            for (Surface surface : map.textures().keySet()) {
                custom.add(surface.displayName.toLowerCase());
            }
            y = line(g, custom.isEmpty() ? "Built-in textures" : "Own textures: " + String.join(", ", custom),
                    TEXT_FONT, TEXT, x, y);
            String music = map.music() == null ? "no music" : "music";
            String battles = map.encounters().active()
                    ? "battles " + map.encounters().rate() + "% a step, " + map.encounters().groupText() + " enemies"
                    : "no random battles";
            y = line(g, fit(g, music + ", " + battles, textWidth), TEXT_FONT, TEXT, x, y);
            y += 12;

            y = header(g, "CHECKS", x, y);
            if (check.problems().isEmpty()) {
                line(g, "Ready to play. Press F5 to try it.", TEXT_FONT, GOOD, x, y);
            } else {
                for (String problem : check.problems()) {
                    y = paragraph(g, problem, TEXT_FONT, WARN, x, y, textWidth) + 4;
                }
            }
        } finally {
            g.dispose();
        }
    }

    private static String count(int n, String thing) {
        return n + " " + thing + (n == 1 ? "" : "s");
    }

    private static int header(Graphics2D g, String text, int x, int y) {
        g.setFont(HEADER_FONT);
        g.setColor(HEADER);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x, y + fm.getAscent());
        return y + fm.getHeight() + 6;
    }

    private static int line(Graphics2D g, String text, Font font, Color color, int x, int y) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x, y + fm.getAscent());
        return y + fm.getHeight() + 2;
    }

    /** Draws word-wrapped text and returns the y below it. */
    private static int paragraph(Graphics2D g, String text, Font font, Color color, int x, int y, int width) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (fm.stringWidth(candidate) > width && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        lines.add(current.toString());
        for (String l : lines) {
            y = line(g, l, font, color, x, y);
        }
        return y;
    }

    private static String fit(Graphics2D g, String text, int width) {
        g.setFont(BOLD_FONT);
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && fm.stringWidth(cut + "...") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }
}
