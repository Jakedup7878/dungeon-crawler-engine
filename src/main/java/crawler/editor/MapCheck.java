package crawler.editor;

import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.core.Reachability;
import crawler.render.AssetSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Things worth knowing about a map before you play it: floor count, which cells the party
 * can't reach, and anything that looks like a mistake.
 *
 * @param floorCells  floor cells the party could stand on (not counting those with a chest or NPC)
 * @param unreachable floor cells the party can't walk to from the start, indexed {@code y * width + x}
 * @param playable    whether the game can start on this map (there is floor, and the start is on it)
 */
record MapCheck(int floorCells, int reachableCells, int doors, int events, int chests, int npcs,
                boolean[] unreachable, List<String> problems, boolean playable) {
    private static final int MAX_LISTED = 3;

    static MapCheck of(DungeonMap map, AssetSource assets) {
        int width = map.width();
        int height = map.height();
        List<String> problems = new ArrayList<>();
        boolean[] reached = Reachability.from(map, map.startX(), map.startY());
        boolean[] unreachable = new boolean[width * height];

        int floor = 0;
        int reachable = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (map.cell(x, y).walkable && !map.isBlocked(x, y)) {
                    floor++;
                    if (reached[y * width + x]) {
                        reachable++;
                    } else {
                        unreachable[y * width + x] = true;
                    }
                }
            }
        }

        boolean startOnFloor = map.cell(map.startX(), map.startY()).walkable && !map.isBlocked(map.startX(), map.startY());
        if (floor == 0) {
            problems.add("There is no floor yet. Carve a room or dig a corridor.");
        } else if (!startOnFloor) {
            problems.add("The start is inside solid rock. Move it with the Start tool.");
        } else if (reachable < floor) {
            int missing = floor - reachable;
            problems.add(missing + (missing == 1 ? " floor cell" : " floor cells")
                    + " can't be reached from the start (shown in red).");
        }

        int doors = 0;
        List<String> badDoors = new ArrayList<>();
        for (int line = 0; line <= height; line++) {
            for (int x = 0; x < width; x++) {
                doors += checkDoor(map, new EdgeRef(true, x, line), badDoors);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int line = 0; line <= width; line++) {
                doors += checkDoor(map, new EdgeRef(false, line, y), badDoors);
            }
        }
        addSome(problems, badDoors, "doors like that");

        int[] counts = new int[ObjectType.values().length];
        List<String> stuck = new ArrayList<>();
        for (MapObject object : map.objects()) {
            counts[object.type().ordinal()]++;
            String where = " at " + object.x() + ", " + object.y();
            if (object.solid() && startOnFloor && !canReach(map, object, reached)) {
                stuck.add("The " + object.type().lowerName() + where + " (" + object.displayName()
                        + ") can't be reached: no open side faces a reachable cell.");
            }
            if (object.type() == ObjectType.EVENT && object.text().isEmpty()) {
                stuck.add("The event" + where + " has no message, so nothing will happen there.");
            }
        }
        addSome(problems, stuck, "objects like that");

        List<String> missing = new ArrayList<>();
        for (String reference : map.assetReferences()) {
            if (assets.image(reference) == null) {
                missing.add("Can't find the image " + reference + ", so the built-in look is used.");
            }
        }
        addSome(problems, missing, "missing images");

        return new MapCheck(floor, reachable, doors, counts[ObjectType.EVENT.ordinal()],
                counts[ObjectType.CHEST.ordinal()], counts[ObjectType.NPC.ordinal()],
                unreachable, List.copyOf(problems), floor > 0 && startOnFloor);
    }

    /** Whether the party can walk up to a chest or NPC from some side. */
    private static boolean canReach(DungeonMap map, MapObject object, boolean[] reached) {
        for (Direction side : Direction.values()) {
            int nx = object.x() + side.dx;
            int ny = object.y() + side.dy;
            if (map.inBounds(nx, ny) && reached[ny * map.width() + nx]
                    && map.edge(object.x(), object.y(), side).passable) {
                return true;
            }
        }
        return false;
    }

    private static void addSome(List<String> problems, List<String> found, String more) {
        for (int i = 0; i < Math.min(MAX_LISTED, found.size()); i++) {
            problems.add(found.get(i));
        }
        if (found.size() > MAX_LISTED) {
            problems.add("... and " + (found.size() - MAX_LISTED) + " more " + more + ".");
        }
    }

    private static int checkDoor(DungeonMap map, EdgeRef edge, List<String> badDoors) {
        if (edge.get(map) != EdgeType.DOOR) {
            return 0;
        }
        boolean before = map.cell(edge.beforeX(), edge.beforeY()).walkable;
        boolean after = map.cell(edge.afterX(), edge.afterY()).walkable;
        if (!before || !after) {
            badDoors.add("The door on the " + edge.describe(map) + " leads into rock.");
        }
        return 1;
    }
}
