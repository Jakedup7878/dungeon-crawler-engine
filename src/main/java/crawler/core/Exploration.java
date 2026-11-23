package crawler.core;

import java.util.Objects;

/** Which cells of a map the party has set foot in. Drives the automap. */
public final class Exploration {
    private final DungeonMap map;
    private final boolean[] visited;
    private int visitedCount;

    public Exploration(DungeonMap map) {
        this.map = Objects.requireNonNull(map, "map");
        this.visited = new boolean[map.width() * map.height()];
    }

    public DungeonMap map() {
        return map;
    }

    public void visit(int x, int y) {
        if (map.inBounds(x, y) && !visited[y * map.width() + x]) {
            visited[y * map.width() + x] = true;
            visitedCount++;
        }
    }

    public boolean isVisited(int x, int y) {
        return map.inBounds(x, y) && visited[y * map.width() + x];
    }

    public int visitedCount() {
        return visitedCount;
    }
}
