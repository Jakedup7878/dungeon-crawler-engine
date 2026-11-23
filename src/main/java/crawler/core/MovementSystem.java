package crawler.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The grid movement rules. Every change to the party's position or facing that
 * comes from the player goes through {@link #perform(MoveAction)}.
 *
 * <p>Movement is purely logical and instant: one call moves exactly one cell or
 * turns exactly 90 degrees. Animation is the renderer's business.
 */
public final class MovementSystem {
    private final Party party;
    private final List<MovementListener> listeners = new ArrayList<>();
    private DungeonMap map;

    public MovementSystem(DungeonMap map, Party party) {
        this.map = Objects.requireNonNull(map, "map");
        this.party = Objects.requireNonNull(party, "party");
    }

    public DungeonMap map() {
        return map;
    }

    /** Switches floors (for stairs and pits). Position the party with {@link Party#place} afterwards. */
    public void setMap(DungeonMap map) {
        this.map = Objects.requireNonNull(map, "map");
    }

    public void addListener(MovementListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(MovementListener listener) {
        listeners.remove(listener);
    }

    /** Whether a party standing in (x, y) could step one cell in the given direction. */
    public boolean canStep(int x, int y, Direction direction) {
        return map.canStep(x, y, direction);
    }

    /** Attempts an action, updates the party, notifies listeners, and reports what happened. */
    public MoveResult perform(MoveAction action) {
        Objects.requireNonNull(action, "action");
        int x = party.x();
        int y = party.y();
        Direction facing = party.facing();
        Direction target = action.apply(facing);

        MoveResult result;
        if (action.isTurn()) {
            party.setFacing(target);
            result = new MoveResult(MoveResult.Outcome.TURNED, action,
                    x, y, x, y, facing, target, null, null);
        } else {
            EdgeType edge = map.edge(x, y, target);
            if (canStep(x, y, target)) {
                int nx = x + target.dx;
                int ny = y + target.dy;
                party.place(nx, ny, facing);
                result = new MoveResult(MoveResult.Outcome.MOVED, action,
                        x, y, nx, ny, facing, facing, target, edge);
            } else {
                result = new MoveResult(MoveResult.Outcome.BLOCKED, action,
                        x, y, x, y, facing, facing, target, edge);
            }
        }

        for (MovementListener listener : List.copyOf(listeners)) {
            listener.onMovement(result);
        }
        return result;
    }
}
