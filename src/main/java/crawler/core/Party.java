package crawler.core;

import java.util.Objects;

/** The adventuring party's place in the dungeon: one grid cell and a facing. */
public final class Party {
    private int x;
    private int y;
    private Direction facing;

    public Party(int x, int y, Direction facing) {
        place(x, y, facing);
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Direction facing() {
        return facing;
    }

    /** Puts the party somewhere directly. Use for teleporters, stairs, spinners and loading saves. */
    public void place(int x, int y, Direction facing) {
        this.x = x;
        this.y = y;
        this.facing = Objects.requireNonNull(facing, "facing");
    }

    public void setFacing(Direction facing) {
        this.facing = Objects.requireNonNull(facing, "facing");
    }
}
