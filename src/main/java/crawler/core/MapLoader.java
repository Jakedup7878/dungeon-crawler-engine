package crawler.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads maps drawn as ASCII art, the way you would on graph paper.
 *
 * <pre>
 * name: Example
 * ; comment lines start with a semicolon
 * +---+---+---+
 * | ^     D   |
 * +   +-D-+---+
 * |   |#######|
 * +---+---+---+
 *
 * [textures]
 * wall = assets/mossy-wall.png
 *
 * [chest 2,0]
 * name = Old chest
 * contents = A rusty key
 * </pre>
 *
 * <ul>
 *   <li>Each cell is 3 characters wide; edges sit between cells, corners are {@code +}.</li>
 *   <li>Horizontal edges: {@code ---} wall, blank = open, anything containing {@code D} = door.</li>
 *   <li>Vertical edges: {@code |} wall, blank = open, {@code D} = door.</li>
 *   <li>Cell contents: {@code #} = solid rock; {@code ^ > v <} = party start, facing N/E/S/W.</li>
 *   <li>Any other character is treated as a wall (edges) or ignored (cell contents).</li>
 * </ul>
 *
 * The grid ends at the first blank line. After it come optional sections:
 * <ul>
 *   <li>{@code [textures]} with {@code wall}, {@code door}, {@code floor}, {@code ceiling} = image path.</li>
 *   <li>{@code [event x,y]}: {@code name}, {@code text}, {@code once} (yes/no), {@code sprite}.</li>
 *   <li>{@code [chest x,y]}: {@code name}, {@code contents}, {@code sprite}, {@code opened}.</li>
 *   <li>{@code [npc x,y]}: {@code name}, {@code text}, {@code sprite}.</li>
 * </ul>
 * Image paths are relative to the map file. In text, {@code \n} starts a new line.
 *
 * Open edges on the map border or next to rock are walled off automatically.
 */
public final class MapLoader {
    /** Characters per cell, counting the edge to its west. */
    private static final int CELL_W = 4;
    private static final Pattern SECTION = Pattern.compile("\\[\\s*(\\w+)(?:\\s+(\\d+)\\s*,\\s*(\\d+))?\\s*]");
    private static final Map<ObjectType, Set<String>> OBJECT_KEYS = Map.of(
            ObjectType.EVENT, Set.of("name", "text", "once", "sprite"),
            ObjectType.CHEST, Set.of("name", "contents", "sprite", "opened"),
            ObjectType.NPC, Set.of("name", "text", "sprite"));

    private MapLoader() {
    }

    public static DungeonMap load(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        return parse(file.getFileName().toString(), lines);
    }

    /** Loads a map from the classpath, e.g. {@code "/maps/b1f.txt"}. */
    public static DungeonMap loadResource(String path) throws IOException {
        try (InputStream in = MapLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new FileNotFoundException("No map resource at " + path);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(path.substring(path.lastIndexOf('/') + 1), text.lines().toList());
        }
    }

    /**
     * Parses map text.
     *
     * @param source used in error messages, and as the map name if there is no {@code name:} line
     */
    public static DungeonMap parse(String source, List<String> lines) {
        String name = source.contains(".") ? source.substring(0, source.lastIndexOf('.')) : source;
        List<String> grid = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();

        int i = 0;
        for (; i < lines.size(); i++) {
            String line = line(lines, i);
            if (line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("+")) {
                break;
            }
            if (line.regionMatches(true, 0, "name:", 0, 5)) {
                name = line.substring(5).trim();
            } else if (!line.isBlank()) {
                throw error(source, i + 1, "expected 'name:', a ';' comment, or the map's top border (+---+---+...)");
            }
        }
        for (; i < lines.size(); i++) {
            String line = line(lines, i);
            if (line.startsWith(";")) {
                continue;
            }
            if (line.isBlank() || line.startsWith("[")) {
                break;
            }
            grid.add(line);
            lineNumbers.add(i + 1);
        }
        if (grid.isEmpty()) {
            throw new IllegalArgumentException(source + ": no map grid found (it should start with a line like +---+---+)");
        }

        Grid parsed = parseGrid(source, name, grid, lineNumbers);
        DungeonMap map = parsed.map();
        parseSections(source, lines, i, map, parsed.hasStart());
        if (!parsed.hasStart()) {
            placeDefaultStart(source, map);
        }
        map.sealEdges();
        return map;
    }

    private static String line(List<String> lines, int i) {
        String line = lines.get(i).stripTrailing();
        return i == 0 && line.startsWith("﻿") ? line.substring(1) : line;
    }

    private record Grid(DungeonMap map, boolean hasStart) {
    }

    private static Grid parseGrid(String source, String name, List<String> grid, List<Integer> lineNumbers) {
        String top = grid.get(0);
        if (top.length() < CELL_W + 1 || (top.length() - 1) % CELL_W != 0) {
            throw error(source, lineNumbers.get(0), "the top border must be '+' followed by '---+' once per column");
        }
        if (grid.size() < 3 || grid.size() % 2 == 0) {
            throw error(source, lineNumbers.get(lineNumbers.size() - 1),
                    "the grid must alternate border and cell rows, starting and ending with a border row");
        }
        int width = (top.length() - 1) / CELL_W;
        int height = (grid.size() - 1) / 2;
        for (int r = 0; r < grid.size(); r++) {
            String row = grid.get(r);
            if (row.length() > top.length()) {
                throw error(source, lineNumbers.get(r), "row is longer than the top border");
            }
            grid.set(r, row + " ".repeat(top.length() - row.length()));
        }

        DungeonMap map = new DungeonMap(name, width, height);

        // Border rows hold the horizontal edges.
        for (int line = 0; line <= height; line++) {
            String row = grid.get(line * 2);
            for (int x = 0; x < width; x++) {
                EdgeType edge = horizontalEdge(row.substring(x * CELL_W + 1, x * CELL_W + CELL_W));
                if (line < height) {
                    map.setEdge(x, line, Direction.NORTH, edge);
                } else {
                    map.setEdge(x, line - 1, Direction.SOUTH, edge);
                }
            }
        }

        // Cell rows hold the vertical edges and the cell contents.
        boolean hasStart = false;
        for (int y = 0; y < height; y++) {
            String row = grid.get(y * 2 + 1);
            int lineNumber = lineNumbers.get(y * 2 + 1);
            for (int line = 0; line <= width; line++) {
                EdgeType edge = verticalEdge(row.charAt(line * CELL_W));
                if (line < width) {
                    map.setEdge(line, y, Direction.WEST, edge);
                } else {
                    map.setEdge(line - 1, y, Direction.EAST, edge);
                }
            }
            for (int x = 0; x < width; x++) {
                String contents = row.substring(x * CELL_W + 1, x * CELL_W + CELL_W);
                boolean rock = contents.indexOf('#') >= 0;
                if (rock) {
                    map.setCell(x, y, CellType.ROCK);
                }
                Direction facing = startMarker(contents);
                if (facing != null) {
                    if (hasStart) {
                        throw error(source, lineNumber, "more than one start marker (^ > v <)");
                    }
                    if (rock) {
                        throw error(source, lineNumber, "the start marker is inside solid rock");
                    }
                    map.setStart(x, y, facing);
                    hasStart = true;
                }
            }
        }

        return new Grid(map, hasStart);
    }

    // ------------------------------------------------------------ sections

    /** A [header] and its key = value lines. */
    private record Section(String kind, int x, int y, boolean hasCell, int line,
                           Map<String, String> values, Map<String, Integer> keyLines) {
    }

    private static void parseSections(String source, List<String> lines, int start, DungeonMap map, boolean hasStart) {
        Section section = null;
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[")) {
                if (section != null) {
                    applySection(source, section, map, hasStart);
                }
                Matcher m = SECTION.matcher(line);
                if (!m.matches()) {
                    throw error(source, i + 1, "expected a section header like [textures] or [chest 3,4]");
                }
                boolean hasCell = m.group(2) != null;
                section = new Section(m.group(1).toLowerCase(), hasCell ? Integer.parseInt(m.group(2)) : 0,
                        hasCell ? Integer.parseInt(m.group(3)) : 0, hasCell, i + 1,
                        new LinkedHashMap<>(), new LinkedHashMap<>());
                continue;
            }
            if (section == null) {
                throw error(source, i + 1, "the map grid has ended (it ends at the first blank line); "
                        + "expected a section header like [textures] or [chest 3,4]");
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw error(source, i + 1, "expected 'key = value'");
            }
            String key = line.substring(0, eq).strip().toLowerCase();
            if (section.values().containsKey(key)) {
                throw error(source, i + 1, "'" + key + "' is given twice");
            }
            section.values().put(key, unescape(line.substring(eq + 1).strip()));
            section.keyLines().put(key, i + 1);
        }
        if (section != null) {
            applySection(source, section, map, hasStart);
        }
    }

    private static void applySection(String source, Section section, DungeonMap map, boolean hasStart) {
        if (section.kind().equals("textures")) {
            if (section.hasCell()) {
                throw error(source, section.line(), "[textures] doesn't take a cell position");
            }
            section.values().forEach((key, value) -> {
                Surface surface = Surface.byKey(key);
                if (surface == null) {
                    throw error(source, section.keyLines().get(key), "unknown texture '" + key
                            + "' (use wall, door, floor or ceiling)");
                }
                map.setTexture(surface, value);
            });
            return;
        }

        ObjectType type = ObjectType.byKey(section.kind());
        if (type == null) {
            throw error(source, section.line(), "unknown section [" + section.kind()
                    + "] (use [textures], [event x,y], [chest x,y] or [npc x,y])");
        }
        int x = section.x();
        int y = section.y();
        if (!section.hasCell()) {
            throw error(source, section.line(), "say which cell, like [" + type.key + " 3,4]");
        }
        if (!map.inBounds(x, y)) {
            throw error(source, section.line(), "cell " + x + "," + y + " is outside the map");
        }
        if (!map.cell(x, y).walkable) {
            throw error(source, section.line(), "cell " + x + "," + y + " is solid rock");
        }
        if (map.object(x, y) != null) {
            throw error(source, section.line(), "there is already " + map.object(x, y).type().withArticle()
                    + " on cell " + x + "," + y);
        }
        if (type.solid && hasStart && x == map.startX() && y == map.startY()) {
            throw error(source, section.line(), type.withArticle() + " can't stand on the start cell "
                    + x + "," + y + ": the party would have nowhere to stand");
        }
        Set<String> allowed = OBJECT_KEYS.get(type);
        for (String key : section.values().keySet()) {
            if (!allowed.contains(key)) {
                throw error(source, section.keyLines().get(key), "a " + type.key + " has no '" + key
                        + "' (it takes " + String.join(", ", allowed.stream().sorted().toList()) + ")");
            }
        }
        Map<String, String> v = section.values();
        boolean once = true;
        if (v.containsKey("once")) {
            String value = v.get("once").toLowerCase();
            if (value.equals("yes") || value.equals("true")) {
                once = true;
            } else if (value.equals("no") || value.equals("false")) {
                once = false;
            } else {
                throw error(source, section.keyLines().get("once"), "once should be yes or no");
            }
        }
        String text = type == ObjectType.CHEST ? v.get("contents") : v.get("text");
        map.putObject(new MapObject(type, x, y, v.getOrDefault("name", ""), text,
                v.get("sprite"), v.get("opened"), once));
    }

    /** {@code \n} is a line break and {@code \\} a backslash. */
    static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                if (next == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                }
                if (next == '\\') {
                    out.append('\\');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    // ------------------------------------------------------------ grid helpers

    private static EdgeType horizontalEdge(String segment) {
        if (segment.indexOf('D') >= 0 || segment.indexOf('d') >= 0) {
            return EdgeType.DOOR;
        }
        return segment.isBlank() ? EdgeType.OPEN : EdgeType.WALL;
    }

    private static EdgeType verticalEdge(char c) {
        return switch (c) {
            case ' ' -> EdgeType.OPEN;
            case 'D', 'd' -> EdgeType.DOOR;
            default -> EdgeType.WALL;
        };
    }

    private static Direction startMarker(String contents) {
        for (char c : contents.toCharArray()) {
            switch (c) {
                case '^': return Direction.NORTH;
                case '>': return Direction.EAST;
                case 'v', 'V': return Direction.SOUTH;
                case '<': return Direction.WEST;
                default: break;
            }
        }
        return null;
    }

    /** With no start marker, the party starts on the first free floor cell, facing north. */
    private static void placeDefaultStart(String source, DungeonMap map) {
        for (int y = 0; y < map.height(); y++) {
            for (int x = 0; x < map.width(); x++) {
                if (map.cell(x, y).walkable && !map.isBlocked(x, y)) {
                    map.setStart(x, y, Direction.NORTH);
                    return;
                }
            }
        }
        throw new IllegalArgumentException(source + ": the map has no floor cells");
    }

    private static IllegalArgumentException error(String source, int line, String message) {
        return new IllegalArgumentException(source + ", line " + line + ": " + message);
    }
}
