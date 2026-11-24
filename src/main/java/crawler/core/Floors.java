package crawler.core;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A game's floors, by their files inside the project: the first one and all that stairs lead to. */
public final class Floors {
    private final String start;
    private final Map<String, DungeonMap> maps;

    private Floors(String start, Map<String, DungeonMap> maps) {
        this.start = start;
        this.maps = Map.copyOf(maps);
    }

    /** One floor by itself. */
    public static Floors single(DungeonMap map) {
        return new Floors("", Map.of("", map));
    }

    /**
     * Loads every floor that can be reached by stairs, so a missing or broken one is found
     * now rather than halfway through a game.
     *
     * @param start the first floor's file inside the project ("" if it isn't a file yet)
     * @param first the first floor itself, already loaded
     */
    public static Floors load(ProjectFiles files, String start, DungeonMap first) throws IOException {
        Map<String, DungeonMap> maps = new LinkedHashMap<>();
        maps.put(start, first);
        Deque<String> waiting = new ArrayDeque<>(List.of(start));
        while (!waiting.isEmpty()) {
            String path = waiting.poll();
            for (MapObject stairs : maps.get(path).objects()) {
                Destination to = stairs.destination();
                if (stairs.type() != ObjectType.STAIRS || !to.isSet()) {
                    continue;
                }
                String where = "The stairs at " + stairs.x() + ", " + stairs.y() + " on "
                        + (path.isEmpty() ? first.name() : path);
                if (!maps.containsKey(to.map())) {
                    List<String> lines = files.lines(to.map());
                    if (lines == null) {
                        throw new IOException(where + " lead to " + to.map() + ", which is missing.");
                    }
                    maps.put(to.map(), MapLoader.parse(to.map().substring(to.map().lastIndexOf('/') + 1), lines));
                    waiting.add(to.map());
                }
                String problem = arrivalProblem(maps.get(to.map()), to);
                if (problem != null) {
                    throw new IOException(where + " " + problem);
                }
            }
        }
        return new Floors(start, maps);
    }

    /** Where the party ends up, and which way it faces. */
    public record Arrival(int x, int y, Direction facing) {
    }

    /**
     * Where the party arrives, or null if it can't. Arriving on stairs in the middle of the floor
     * means stepping off them: the way it's to face if possible, or any way that's free.
     */
    public static Arrival arrival(DungeonMap map, Destination to) {
        int x = to.atStart() ? map.startX() : to.x();
        int y = to.atStart() ? map.startY() : to.y();
        Direction facing = to.atStart() ? map.startFacing() : to.facing();
        if (!map.inBounds(x, y) || !map.cell(x, y).walkable) {
            return null;
        }
        if (!map.isBlocked(x, y)) {
            return new Arrival(x, y, facing);
        }
        if (map.object(x, y).type() == ObjectType.STAIRS) {
            for (int i = 0; i < 4; i++) {
                if (map.canStep(x, y, facing)) {
                    return new Arrival(x + facing.dx, y + facing.dy, facing);
                }
                facing = facing.turnRight();
            }
        }
        return null;
    }

    /** Why the party can't arrive there ("lead to ..."), or null if it can. */
    public static String arrivalProblem(DungeonMap map, Destination to) {
        int x = to.atStart() ? map.startX() : to.x();
        int y = to.atStart() ? map.startY() : to.y();
        if (!map.inBounds(x, y)) {
            return "lead to cell " + x + ", " + y + " of " + to.map() + ", which is outside that floor.";
        }
        if (arrival(map, to) == null) {
            return "lead to cell " + x + ", " + y + " of " + to.map() + ", where the party can't stand.";
        }
        return null;
    }

    /** The first floor's file. */
    public String start() {
        return start;
    }

    public DungeonMap first() {
        return maps.get(start);
    }

    /** A floor by its file, or null. */
    public DungeonMap get(String path) {
        return maps.get(path);
    }

    public int size() {
        return maps.size();
    }
}
