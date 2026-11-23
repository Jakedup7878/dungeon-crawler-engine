package crawler.core;

/**
 * What sits on the boundary between two neighbouring cells.
 *
 * <p>Walls live on the edges between cells (Wizardry-style "thin walls") rather than
 * filling whole cells, so a single corridor cell can have a wall on one side and a
 * door on the other. New kinds of edge (locked doors, secret doors, one-way walls)
 * only need a new constant here plus a texture in the renderer.
 */
public enum EdgeType {
    /** Nothing there: the party can walk and see through it. */
    OPEN(true, false),
    /** Solid wall. */
    WALL(false, true),
    /** A door: the party walks through it, but cannot see through it. */
    DOOR(true, true);

    /** Whether the party can step across this edge. */
    public final boolean passable;
    /** Whether this edge stops line of sight (and is therefore drawn). */
    public final boolean blocksSight;

    EdgeType(boolean passable, boolean blocksSight) {
        this.passable = passable;
        this.blocksSight = blocksSight;
    }
}
