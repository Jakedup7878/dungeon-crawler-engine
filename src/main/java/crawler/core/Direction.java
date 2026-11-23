package crawler.core;

/**
 * The four cardinal directions.
 *
 * <p>Map coordinates are screen-style: x grows to the east, y grows to the south,
 * so (0, 0) is the north-west corner of the map.
 */
public enum Direction {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private static final Direction[] VALUES = values();

    /** Step on the x axis when moving one cell in this direction. */
    public final int dx;
    /** Step on the y axis when moving one cell in this direction. */
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public Direction turnLeft() {
        return VALUES[(ordinal() + 3) & 3];
    }

    public Direction turnRight() {
        return VALUES[(ordinal() + 1) & 3];
    }

    public Direction opposite() {
        return VALUES[(ordinal() + 2) & 3];
    }

    /** Heading in radians, clockwise from north (0 = north, PI/2 = east). */
    public double angle() {
        return ordinal() * (Math.PI / 2);
    }

    /** "North", "East", ... */
    public String displayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
