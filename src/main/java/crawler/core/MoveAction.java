package crawler.core;

/** Everything the party can do to change its position or facing. */
public enum MoveAction {
    FORWARD,
    BACKWARD,
    STRAFE_LEFT,
    STRAFE_RIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_AROUND;

    public boolean isTurn() {
        return this == TURN_LEFT || this == TURN_RIGHT || this == TURN_AROUND;
    }

    /**
     * For a step, the world direction the party travels in.
     * For a turn, the facing the party ends up with.
     */
    public Direction apply(Direction facing) {
        return switch (this) {
            case FORWARD -> facing;
            case BACKWARD, TURN_AROUND -> facing.opposite();
            case STRAFE_LEFT, TURN_LEFT -> facing.turnLeft();
            case STRAFE_RIGHT, TURN_RIGHT -> facing.turnRight();
        };
    }
}
