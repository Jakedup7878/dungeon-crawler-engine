package crawler.battle;

import java.util.List;

/**
 * Something that happened in a battle round, in the order it happened. The battle screen
 * plays these one at a time: an animation, a sound, a line of text.
 */
public sealed interface BattleEvent {

    /** A party member braces for the round: attacks on them do half damage. */
    record Defend(Fighter actor) implements BattleEvent {
    }

    /**
     * One attack.
     *
     * @param damage HP taken (0 for a miss)
     * @param killed whether the target fell
     */
    record Attack(Fighter attacker, Fighter target, int damage, boolean missed, boolean killed) implements BattleEvent {
    }

    /** The party tried to run and couldn't. */
    record FleeFailed(Fighter actor) implements BattleEvent {
    }

    /** This battle can't be run from (a fixed battle, such as a boss). */
    record CantFlee(Fighter actor) implements BattleEvent {
    }

    /** The party got away. The battle is over. */
    record Fled(Fighter actor) implements BattleEvent {
    }

    /** A party member reached a new level. */
    record LevelUp(Fighter hero, int level) {
    }

    /**
     * Every enemy is beaten. The battle is over.
     *
     * @param expEach experience each surviving party member earned
     */
    record Victory(int expEach, int gold, List<LevelUp> levelUps) implements BattleEvent {
    }

    /** Every party member has fallen. The battle, and the game, is over. */
    record Defeat() implements BattleEvent {
    }
}
