package crawler.editor;

import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;

/**
 * Names one edge of the grid: segment {@code x} of horizontal line {@code y}, or
 * segment {@code y} of vertical line {@code x}. Same numbering as {@link DungeonMap}.
 */
record EdgeRef(boolean horizontal, int x, int y) {

    /** The edge between two neighbouring cells. */
    static EdgeRef between(int x0, int y0, int x1, int y1) {
        if (x0 == x1 && Math.abs(y0 - y1) == 1) {
            return new EdgeRef(true, x0, Math.max(y0, y1));
        }
        if (y0 == y1 && Math.abs(x0 - x1) == 1) {
            return new EdgeRef(false, Math.max(x0, x1), y0);
        }
        throw new IllegalArgumentException("(" + x0 + ", " + y0 + ") and (" + x1 + ", " + y1 + ") are not neighbours");
    }

    EdgeType get(DungeonMap map) {
        return horizontal ? map.horizontalEdge(x, y) : map.verticalEdge(x, y);
    }

    void set(DungeonMap map, EdgeType type) {
        map.setEdge(x, y, horizontal ? Direction.NORTH : Direction.WEST, type);
    }

    /** Cell on the north (or west) side. */
    int beforeX() {
        return horizontal ? x : x - 1;
    }

    int beforeY() {
        return horizontal ? y - 1 : y;
    }

    /** Cell on the south (or east) side. */
    int afterX() {
        return x;
    }

    int afterY() {
        return y;
    }

    /** Whether there is a cell of the map on both sides (so it is not part of the border). */
    boolean isInterior(DungeonMap map) {
        return map.inBounds(beforeX(), beforeY()) && map.inBounds(afterX(), afterY());
    }

    /** "north side of 3, 4" and so on, naming a cell that is inside the map. */
    String describe(DungeonMap map) {
        if (map.inBounds(afterX(), afterY())) {
            return (horizontal ? "north" : "west") + " side of " + afterX() + ", " + afterY();
        }
        return (horizontal ? "south" : "east") + " side of " + beforeX() + ", " + beforeY();
    }
}
