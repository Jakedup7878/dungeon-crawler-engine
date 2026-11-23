package crawler.core;

/**
 * Notified after every movement attempt, successful or not.
 *
 * <p>This is the hook for everything that reacts to walking around: random
 * encounters, step counters, traps, spinners, teleporters, map events, sound.
 */
@FunctionalInterface
public interface MovementListener {
    void onMovement(MoveResult result);
}
