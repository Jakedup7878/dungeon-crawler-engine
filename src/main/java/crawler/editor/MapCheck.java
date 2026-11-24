package crawler.editor;

import crawler.core.Destination;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Floors;
import crawler.core.ItemStack;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.core.Reachability;
import crawler.render.AssetSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Things worth knowing about a map before you play it: floor count, which cells the party
 * can't reach, and anything that looks like a mistake.
 *
 * @param floorCells  floor cells the party could stand on (not counting those with a chest or NPC)
 * @param unreachable floor cells the party can't walk to from the start, indexed {@code y * width + x}
 * @param playable    whether the game can start on this map (there is floor, and the start is on it)
 */
record MapCheck(int floorCells, int reachableCells, int doors, int events, int chests, int npcs, int stairs,
                boolean[] unreachable, List<String> problems, boolean playable) {
    private static final int MAX_LISTED = 3;

    static MapCheck of(DungeonMap map, AssetSource assets) {
        return of(map, assets, null, null, null);
    }

    static MapCheck of(DungeonMap map, AssetSource assets, Set<String> knownEnemies) {
        return of(map, assets, knownEnemies, null, null);
    }

    static MapCheck of(DungeonMap map, AssetSource assets, Set<String> knownEnemies, Set<String> knownItems) {
        return of(map, assets, knownEnemies, knownItems, null);
    }

    /**
     * @param floors       the project's floors by file (null values can't be read), to check where stairs lead;
     *                     null to skip that
     * @param knownItems   ids of the project's items, to check chests the same way; null to skip that
     * @param knownEnemies ids of the project's enemies, to point out encounters and event battles
     *                     naming enemies that don't exist; null to skip that check
     */
    static MapCheck of(DungeonMap map, AssetSource assets, Set<String> knownEnemies, Set<String> knownItems,
                       Map<String, DungeonMap> floors) {
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
            if (object.type() == ObjectType.STAIRS) {
                Destination to = object.destination();
                if (object.wall() != null && map.edge(object.x(), object.y(), object.wall()) != EdgeType.WALL) {
                    stuck.add("The stairs" + where + " are on the " + object.wall().displayName().toLowerCase()
                            + " side, but there's no wall there.");
                }
                if (!to.isSet()) {
                    stuck.add("The stairs" + where + " don't lead anywhere yet.");
                } else if (floors != null && !floors.containsKey(to.map())) {
                    stuck.add("The stairs" + where + " lead to " + to.map() + ", which doesn't exist.");
                } else if (floors != null && floors.get(to.map()) != null) {
                    String problem = Floors.arrivalProblem(floors.get(to.map()), to);
                    if (problem != null) {
                        stuck.add("The stairs" + where + " " + problem);
                    }
                }
            }
            if (object.type() == ObjectType.EVENT && object.text().isEmpty()) {
                stuck.add("The event" + where + " has no message, so nothing will happen there.");
            }
        }
        addSome(problems, stuck, "objects like that");

        if (knownEnemies != null) {
            List<String> unknown = new ArrayList<>();
            for (String id : map.encounters().enemies()) {
                if (!knownEnemies.contains(id)) {
                    unknown.add("Random battles name an enemy that doesn't exist: " + id + " (see Project > Enemies).");
                }
            }
            for (MapObject object : map.objects()) {
                for (String id : object.battle()) {
                    if (!knownEnemies.contains(id)) {
                        unknown.add("The event at " + object.x() + ", " + object.y() + " has a battle with an enemy that "
                                + "doesn't exist: " + id + ".");
                    }
                }
            }
            addSome(problems, unknown, "unknown enemies");
        }
        if (knownItems != null) {
            List<String> unknown = new ArrayList<>();
            for (MapObject object : map.objects()) {
                for (ItemStack stack : object.items()) {
                    if (!knownItems.contains(stack.id())) {
                        unknown.add("The chest at " + object.x() + ", " + object.y() + " holds an item that doesn't exist: "
                                + stack.id() + " (see Project > Items).");
                    }
                }
            }
            addSome(problems, unknown, "unknown items");
        }
        if (map.encounters().rate() > 0 && map.encounters().enemies().isEmpty()) {
            problems.add("Random battles are switched on, but no enemies are chosen (Edit > Map Settings).");
        }

        List<String> missing = new ArrayList<>();
        for (String reference : map.assetReferences()) {
            if (assets.data(reference) == null && assets.image(reference) == null) {
                missing.add("Can't find the file " + reference + ". Pictures fall back to the built-in look; sounds stay silent.");
            }
        }
        addSome(problems, missing, "missing files");

        return new MapCheck(floor, reachable, doors, counts[ObjectType.EVENT.ordinal()],
                counts[ObjectType.CHEST.ordinal()], counts[ObjectType.NPC.ordinal()],
                counts[ObjectType.STAIRS.ordinal()],
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
