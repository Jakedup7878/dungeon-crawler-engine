package crawler.core;

/** What fills a grid cell. */
public enum CellType {
    /** Ordinary walkable floor. */
    FLOOR(true),
    /** Solid rock: never entered. Used to fill unused parts of the map. */
    ROCK(false);

    public final boolean walkable;

    CellType(boolean walkable) {
        this.walkable = walkable;
    }
}
