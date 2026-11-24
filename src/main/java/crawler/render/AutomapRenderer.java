package crawler.render;

import crawler.core.CellType;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Exploration;
import crawler.core.MapObject;
import crawler.core.ObjectType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** Draws the top-down automap of the cells the party has visited. */
public final class AutomapRenderer {
    private static final Color BACKGROUND = new Color(10, 11, 16);
    private static final Color FLOOR = new Color(34, 46, 66);
    private static final Color ROCK = new Color(22, 22, 28);
    private static final Color GRID = new Color(50, 64, 90);
    private static final Color WALL = new Color(196, 208, 232);
    private static final Color DOOR = new Color(232, 176, 82);
    private static final Color PARTY = new Color(255, 214, 92);
    private static final int MIN_CELL = 8;
    private static final int MAX_CELL = 26;

    /**
     * Draws the automap inside {@code area}. The whole map is shown when it fits;
     * otherwise the view scrolls to keep the party centred.
     *
     * @param cameraX   party marker position in map units (cell centres at +0.5)
     * @param cameraY   party marker position in map units
     * @param angle     party marker heading, radians clockwise from north
     * @param revealAll draw every cell, not just visited ones (debug view)
     */
    public void draw(Graphics2D graphics, Rectangle area, DungeonMap map, Exploration explored,
                     double cameraX, double cameraY, double angle, boolean revealAll) {
        int cell = Math.min(area.width / map.width(), area.height / map.height());
        cell = Math.max(MIN_CELL, Math.min(MAX_CELL, cell));
        int originX = (int) Math.round(origin(area.x, area.width, cell * map.width(), cameraX * cell));
        int originY = (int) Math.round(origin(area.y, area.height, cell * map.height(), cameraY * cell));

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.clip(area);
            g.setColor(BACKGROUND);
            g.fill(area);
            g.translate(originX, originY);

            for (int y = 0; y < map.height(); y++) {
                for (int x = 0; x < map.width(); x++) {
                    MapObject object = map.object(x, y);
                    boolean stairs = object != null && object.type() == ObjectType.STAIRS;
                    // Stairs in the middle of the floor can't be stood on, so seeing them from beside them will do.
                    if (!revealAll && !explored.isVisited(x, y)
                            && !(stairs && object.solid() && seenFromBeside(map, explored, x, y))) {
                        continue;
                    }
                    boolean rock = map.cell(x, y) == CellType.ROCK;
                    g.setColor(rock ? ROCK : FLOOR);
                    g.fillRect(x * cell, y * cell, cell, cell);
                    if (!rock) {
                        g.setColor(GRID);
                        g.drawRect(x * cell, y * cell, cell - 1, cell - 1);
                        if (stairs) {
                            // On a wall, towards that wall; otherwise in the middle.
                            int dx = object.wall() == null ? 0 : object.wall().dx * cell / 4;
                            int dy = object.wall() == null ? 0 : object.wall().dy * cell / 4;
                            drawStairs(g, x * cell + dx, y * cell + dy, cell, object.destination().up());
                        }
                    }
                }
            }

            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            for (int y = 0; y < map.height(); y++) {
                for (int x = 0; x < map.width(); x++) {
                    if (map.cell(x, y) == CellType.ROCK || (!revealAll && !explored.isVisited(x, y))) {
                        continue;
                    }
                    for (Direction side : Direction.values()) {
                        drawEdge(g, map.edge(x, y, side), x, y, side, cell);
                    }
                }
            }

            drawParty(g, cameraX * cell, cameraY * cell, angle, cell);
        } finally {
            g.dispose();
        }
    }

    private static boolean seenFromBeside(DungeonMap map, Exploration explored, int x, int y) {
        for (Direction side : Direction.values()) {
            if (map.edge(x, y, side).passable && explored.isVisited(x + side.dx, y + side.dy)) {
                return true;
            }
        }
        return false;
    }

    /** A triangle pointing the way the stairs go. */
    private static void drawStairs(Graphics2D g, int x, int y, int cell, boolean up) {
        int a = y + cell / 4;
        int b = y + cell - cell / 4;
        g.setColor(DOOR);
        g.fillPolygon(new int[]{x + cell / 4, x + cell - cell / 4, x + cell / 2},
                up ? new int[]{b, b, a} : new int[]{a, a, b}, 3);
    }

    private static void drawEdge(Graphics2D g, EdgeType edge, int x, int y, Direction side, int cell) {
        if (edge == EdgeType.OPEN) {
            return;
        }
        int x0 = x * cell;
        int y0 = y * cell;
        int x1 = x0 + cell;
        int y1 = y0 + cell;
        switch (side) {
            case NORTH -> y1 = y0;
            case SOUTH -> y0 = y1;
            case WEST -> x1 = x0;
            case EAST -> x0 = x1;
        }
        g.setColor(WALL);
        g.drawLine(x0, y0, x1, y1);
        if (edge == EdgeType.DOOR) {
            int mid = cell / 2;
            int len = Math.max(3, cell / 4);
            int thick = Math.max(3, cell / 6);
            g.setColor(DOOR);
            if (y0 == y1) {
                g.fillRect(x0 + mid - len, y0 - thick / 2, len * 2, thick);
            } else {
                g.fillRect(x0 - thick / 2, y0 + mid - len, thick, len * 2);
            }
        }
    }

    private static void drawParty(Graphics2D graphics, double px, double py, double angle, int cell) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(px, py);
            g.rotate(angle); // positive angles turn clockwise on screen, matching map headings
            double s = cell / 2.0;
            Polygon arrow = new Polygon(
                    new int[]{0, (int) Math.round(s * 0.6), 0, (int) Math.round(-s * 0.6)},
                    new int[]{(int) Math.round(-s * 0.75), (int) Math.round(s * 0.6),
                            (int) Math.round(s * 0.3), (int) Math.round(s * 0.6)},
                    4);
            g.setColor(PARTY);
            g.fillPolygon(arrow);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1f));
            g.drawPolygon(arrow);
        } finally {
            g.dispose();
        }
    }

    /** Centres content that fits; otherwise scrolls so {@code focus} sits mid-area, clamped to the map. */
    private static double origin(int start, int available, int content, double focus) {
        if (content <= available) {
            return start + (available - content) / 2.0;
        }
        double o = start + available / 2.0 - focus;
        return Math.max(start + available - content, Math.min(start, o));
    }
}
