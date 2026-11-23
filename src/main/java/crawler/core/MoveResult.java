package crawler.core;

/**
 * What happened when the party attempted a {@link MoveAction}.
 *
 * @param outcome       moved, turned, or blocked
 * @param action        the action that was attempted
 * @param fromX         cell before the action
 * @param fromY         cell before the action
 * @param toX           cell after the action (same as from unless the party moved)
 * @param toY           cell after the action
 * @param facingBefore  facing before the action
 * @param facingAfter   facing after the action
 * @param travel        world direction of the attempted step, or {@code null} for turns
 * @param edge          the edge crossed or bumped into, or {@code null} for turns
 */
public record MoveResult(
        Outcome outcome,
        MoveAction action,
        int fromX, int fromY,
        int toX, int toY,
        Direction facingBefore,
        Direction facingAfter,
        Direction travel,
        EdgeType edge) {

    public enum Outcome {
        /** The party stepped into a new cell. */
        MOVED,
        /** The party turned in place. */
        TURNED,
        /** The step was refused (wall, rock, edge of the map). */
        BLOCKED
    }
}
