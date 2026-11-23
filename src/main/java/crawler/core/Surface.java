package crawler.core;

/** The surfaces a map can give its own texture. */
public enum Surface {
    WALL("wall", "Walls"),
    DOOR("door", "Doors"),
    FLOOR("floor", "Floor"),
    CEILING("ceiling", "Ceiling");

    /** Name used in map files. */
    public final String key;
    public final String displayName;

    Surface(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    /** The surface with this map-file key, or null. */
    public static Surface byKey(String key) {
        for (Surface surface : values()) {
            if (surface.key.equalsIgnoreCase(key)) {
                return surface;
            }
        }
        return null;
    }
}
