package crawler.core;

/** Which cells the party can walk to. */
public final class Reachability {
    private Reachability() {
    }

    /**
     * Every cell the party could walk to from (x, y), as a {@code width * height} array
     * indexed {@code y * width + x}. All false if (x, y) is not a floor cell.
     */
    public static boolean[] from(DungeonMap map, int x, int y) {
        int width = map.width();
        boolean[] reached = new boolean[width * map.height()];
        if (!map.inBounds(x, y) || !map.cell(x, y).walkable) {
            return reached;
        }
        int[] queue = new int[reached.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = y * width + x;
        reached[y * width + x] = true;
        while (head < tail) {
            int index = queue[head++];
            int cx = index % width;
            int cy = index / width;
            for (Direction direction : Direction.values()) {
                if (map.canStep(cx, cy, direction)) {
                    int next = (cy + direction.dy) * width + cx + direction.dx;
                    if (!reached[next]) {
                        reached[next] = true;
                        queue[tail++] = next;
                    }
                }
            }
        }
        return reached;
    }
}
