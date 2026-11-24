package crawler.core;

import java.util.Locale;

/**
 * Where stairs lead.
 *
 * @param map    the floor's file inside the project, such as {@code maps/b2f.txt}; "" if not set yet
 * @param x      the cell the party arrives on, or -1 for the floor's own start
 * @param facing which way the party faces on arrival; null for the floor's own start
 * @param up     whether the stairs look like they go up
 */
public record Destination(String map, int x, int y, Direction facing, boolean up) {
    public static final Destination NONE = new Destination("", -1, -1, null, false);

    public Destination {
        map = map == null ? "" : map.strip().replace('\\', '/');
        if (x < 0 || y < 0 || facing == null) {
            x = -1;
            y = -1;
            facing = null;
        }
    }

    public boolean isSet() {
        return !map.isEmpty();
    }

    /** Whether the party arrives on the floor's own start. */
    public boolean atStart() {
        return facing == null;
    }

    /** "3,4,north", or "start". */
    public String arrivalText() {
        return atStart() ? "start" : x + "," + y + "," + facing.name().toLowerCase(Locale.ROOT);
    }

    /** Reads a floor and an {@link #arrivalText()}; IllegalArgumentException if it isn't one. */
    public static Destination parse(String map, String arrival, boolean up) {
        String text = arrival == null ? "" : arrival.strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("start")) {
            return new Destination(map, -1, -1, null, up);
        }
        String[] parts = text.split("\\s*,\\s*");
        if (parts.length == 3 && parts[0].matches("\\d{1,4}") && parts[1].matches("\\d{1,4}")) {
            for (Direction d : Direction.values()) {
                String name = d.name().toLowerCase(Locale.ROOT);
                if (name.equals(parts[2]) || name.substring(0, 1).equals(parts[2])) {
                    return new Destination(map, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), d, up);
                }
            }
        }
        throw new IllegalArgumentException("where the party arrives should be 'start' or a cell and a direction, like 3,4,north");
    }
}
