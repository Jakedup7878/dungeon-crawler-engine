package crawler.editor;

import crawler.core.CellType;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.ObjectType;

/**
 * The editing operations, as plain functions on a {@link DungeonMap}. They contain no
 * Swing code, so they can be tested directly. Callers seal the map afterwards
 * ({@link DungeonMap#sealEdges()}) so that rock is always walled off.
 */
final class MapEdits {
    private MapEdits() {
    }

    /** A new map of solid rock with a single floor cell in the middle, where the party starts. */
    static DungeonMap blank(String name, int width, int height) {
        DungeonMap map = new DungeonMap(name, width, height);
        fillRock(map, 0, 0, width - 1, height - 1);
        int x = (width - 1) / 2;
        int y = (height - 1) / 2;
        dig(map, x, y);
        map.setStart(x, y, Direction.NORTH);
        map.sealEdges();
        return map;
    }

    /**
     * Carves a room covering the given cells (corners in any order): floor inside with no
     * walls, and walls all the way round. Doors already in the outline are kept.
     */
    static void room(DungeonMap map, int x0, int y0, int x1, int y1) {
        int left = Math.min(x0, x1);
        int right = Math.max(x0, x1);
        int top = Math.min(y0, y1);
        int bottom = Math.max(y0, y1);
        for (int y = top; y <= bottom; y++) {
            for (int x = left; x <= right; x++) {
                map.setCell(x, y, CellType.FLOOR);
                for (Direction side : Direction.values()) {
                    int nx = x + side.dx;
                    int ny = y + side.dy;
                    boolean inside = nx >= left && nx <= right && ny >= top && ny <= bottom;
                    if (inside) {
                        map.setEdge(x, y, side, EdgeType.OPEN);
                    } else if (map.edge(x, y, side) != EdgeType.DOOR) {
                        map.setEdge(x, y, side, EdgeType.WALL);
                    }
                }
            }
        }
    }

    /** Fills the given cells (corners in any order) with solid rock, burying any objects there. */
    static void fillRock(DungeonMap map, int x0, int y0, int x1, int y1) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                map.setCell(x, y, CellType.ROCK);
                map.removeObject(x, y);
                for (Direction side : Direction.values()) {
                    map.setEdge(x, y, side, EdgeType.WALL);
                }
            }
        }
    }

    /** Digs out a single rock cell, leaving it walled in on all sides. Floor is left alone. */
    static void dig(DungeonMap map, int x, int y) {
        if (map.cell(x, y).walkable) {
            return;
        }
        map.setCell(x, y, CellType.FLOOR);
        for (Direction side : Direction.values()) {
            if (map.edge(x, y, side) != EdgeType.DOOR) {
                map.setEdge(x, y, side, EdgeType.WALL);
            }
        }
    }

    /**
     * Why an object can't go on (x, y), or null if it can: it needs a floor cell with nothing
     * on it, and a chest or NPC can't stand where the party starts.
     */
    static String objectProblem(DungeonMap map, ObjectType type, int x, int y) {
        // New NPCs can be walked through, and new stairs go on a wall if there is one.
        return objectProblem(map, type, type.solid && type != ObjectType.NPC
                && !(type == ObjectType.STAIRS && map.inBounds(x, y) && wallBeside(map, x, y) != null), x, y);
    }

    /** The first side of a cell with a plain wall, or null. */
    static Direction wallBeside(DungeonMap map, int x, int y) {
        for (Direction side : Direction.values()) {
            if (map.edge(x, y, side) == EdgeType.WALL) {
                return side;
            }
        }
        return null;
    }

    /** The same for an object that does or doesn't block its cell. */
    static String objectProblem(DungeonMap map, ObjectType type, boolean solid, int x, int y) {
        if (!map.inBounds(x, y) || !map.cell(x, y).walkable) {
            return "Events, chests, NPCs and stairs go on floor cells.";
        }
        if (map.object(x, y) != null) {
            return "There's already " + map.object(x, y).type().withArticle() + " on that cell.";
        }
        if (solid && x == map.startX() && y == map.startY()) {
            return "The party starts on that cell, so " + type.withArticle()
                    + " can't stand there. Move the start first (tool 5).";
        }
        return null;
    }

    /**
     * One step of a corridor: digs out (toX, toY) if it is rock, then opens the wall between
     * it and the neighbouring cell (fromX, fromY). A door between them is kept.
     */
    static void dig(DungeonMap map, int fromX, int fromY, int toX, int toY) {
        dig(map, fromX, fromY);
        dig(map, toX, toY);
        EdgeRef between = EdgeRef.between(fromX, fromY, toX, toY);
        if (between.get(map) == EdgeType.WALL) {
            between.set(map, EdgeType.OPEN);
        }
    }
}
