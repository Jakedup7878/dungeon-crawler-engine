package crawler.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes maps in the ASCII format that {@link MapLoader} reads, so a saved map loads
 * back exactly as it was.
 *
 * <p>Solid rock is drawn as a solid block of {@code #}, the way you would draw it by hand.
 * If the start sits inside rock it is left out, and the loader falls back to the first
 * floor cell. Textures and objects follow the grid as {@code [sections]}.
 */
public final class MapWriter {
    private MapWriter() {
    }

    public static void save(DungeonMap map, Path file) throws IOException {
        Files.writeString(file, write(map), StandardCharsets.UTF_8);
    }

    public static String write(DungeonMap map) {
        int width = map.width();
        int height = map.height();
        StringBuilder out = new StringBuilder();
        out.append("name: ").append(map.name().replaceAll("[\\r\\n]+", " ").strip()).append('\n');
        out.append("; ").append(width).append(" x ").append(height).append(" cells.\n");
        out.append(";   ---  or  |   wall          (blank)  open passage\n");
        out.append(";   -D-  or  D   door          ###      solid rock\n");
        out.append(";   ^ > v <      where the party starts, and which way it faces\n");

        for (int line = 0; line <= height; line++) {
            for (int x = 0; x < width; x++) {
                out.append(corner(map, x, line)).append(horizontal(map, x, line));
            }
            out.append(corner(map, width, line)).append('\n');
            if (line == height) {
                break;
            }
            int y = line;
            for (int x = 0; x < width; x++) {
                out.append(vertical(map, x, y)).append(contents(map, x, y));
            }
            out.append(vertical(map, width, y)).append('\n');
        }

        if (!map.textures().isEmpty()) {
            out.append("\n[textures]\n");
            for (Surface surface : Surface.values()) {
                field(out, surface.key, map.texture(surface));
            }
        }
        Encounters encounters = map.encounters();
        if (map.music() != null || !encounters.equals(Encounters.NONE)) {
            out.append("\n[settings]\n");
            field(out, "music", map.music());
            if (!encounters.equals(Encounters.NONE)) {
                field(out, "encounters", String.join(", ", encounters.enemies()));
                field(out, "encounter_rate", Integer.toString(encounters.rate()));
                field(out, "group", encounters.groupText());
            }
        }
        for (MapObject object : map.objects()) {
            out.append("\n[").append(object.type().key).append(' ')
                    .append(object.x()).append(',').append(object.y()).append("]\n");
            field(out, "name", object.name());
            switch (object.type()) {
                case EVENT -> {
                    field(out, "text", object.text());
                    field(out, "once", object.once() ? "yes" : "no");
                    field(out, "sprite", object.sprite());
                    field(out, "battle", String.join(", ", object.battle()));
                }
                case CHEST -> {
                    field(out, "contents", object.text());
                    field(out, "sprite", object.sprite());
                    field(out, "opened", object.openSprite());
                }
                case NPC -> {
                    field(out, "text", object.text());
                    field(out, "sprite", object.sprite());
                }
            }
        }
        return out.toString();
    }

    /** Writes {@code key = value}, skipping empty values. Sprites are written in their text form. */
    private static void field(StringBuilder out, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            out.append(key).append(" = ").append(escape(value.toString())).append('\n');
        }
    }

    /** Line breaks become {@code \n} so every value stays on one line; a backslash becomes {@code \\}. */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
    }

    /** Whether (x, y) is a rock cell inside the map (the border is always drawn as wall). */
    private static boolean rockInside(DungeonMap map, int x, int y) {
        return map.inBounds(x, y) && !map.cell(x, y).walkable;
    }

    private static char corner(DungeonMap map, int x, int line) {
        boolean solid = rockInside(map, x - 1, line - 1) && rockInside(map, x, line - 1)
                && rockInside(map, x - 1, line) && rockInside(map, x, line);
        return solid ? '#' : '+';
    }

    /** Segment {@code x} of horizontal line {@code line}. */
    private static String horizontal(DungeonMap map, int x, int line) {
        EdgeType edge = map.horizontalEdge(x, line);
        if (edge == EdgeType.DOOR) {
            return "-D-";
        }
        if (rockInside(map, x, line - 1) && rockInside(map, x, line)) {
            return "###";
        }
        return edge == EdgeType.OPEN ? "   " : "---";
    }

    /** Segment {@code y} of vertical line {@code line}. */
    private static char vertical(DungeonMap map, int line, int y) {
        EdgeType edge = map.verticalEdge(line, y);
        if (edge == EdgeType.DOOR) {
            return 'D';
        }
        if (rockInside(map, line - 1, y) && rockInside(map, line, y)) {
            return '#';
        }
        return edge == EdgeType.OPEN ? ' ' : '|';
    }

    private static String contents(DungeonMap map, int x, int y) {
        if (!map.cell(x, y).walkable) {
            return "###";
        }
        if (x == map.startX() && y == map.startY()) {
            char marker = switch (map.startFacing()) {
                case NORTH -> '^';
                case EAST -> '>';
                case SOUTH -> 'v';
                case WEST -> '<';
            };
            return " " + marker + " ";
        }
        return "   ";
    }
}
