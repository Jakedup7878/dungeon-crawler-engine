package crawler.battle;

import crawler.core.CharacterClass;
import crawler.core.EnemyType;
import crawler.core.PartyMember;
import crawler.core.Stats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleTest {
    static final CharacterClass KNIGHT = new CharacterClass("knight", "Knight", new Stats(30, 0, 10, 6, 5),
            new Stats(10, 2, 3, 2, 1), null, null);

    static Fighter hero(String name, Stats stats) {
        return Fighter.hero(new PartyMember(name, "Knight", 1, stats, null), KNIGHT);
    }

    static Fighter enemy(String name, Stats stats, int exp, int gold) {
        return Fighter.enemy(new EnemyType(name.toLowerCase(), name, stats, exp, gold, null), name);
    }

    /** Plays rounds where everyone attacks, until the battle ends; returns every event. */
    static List<BattleEvent> playOut(Battle battle) {
        List<BattleEvent> all = new ArrayList<>();
        for (int i = 0; i < 200 && battle.outcome() == Battle.Outcome.ONGOING; i++) {
            all.addAll(battle.playRound(List.of()));
        }
        return all;
    }

    @Test
    void damageIsAttackTimesTheRollMinusHalfTheDefense() {
        Fighter attacker = hero("A", new Stats(10, 0, 10, 0, 0));
        Fighter target = enemy("T", new Stats(50, 0, 0, 4, 0), 0, 0);
        assertEquals(8, Battle.damage(attacker, target, 100));
        assertEquals(9, Battle.damage(attacker, target, 115));
        target.setDefending(true);
        assertEquals(4, Battle.damage(attacker, target, 100), "defending halves it");
        Fighter weak = hero("W", new Stats(10, 0, 1, 0, 0));
        assertEquals(1, Battle.damage(weak, enemy("Wall", new Stats(9, 0, 0, 50, 0), 0, 0), 85), "always at least 1");
    }

    @Test
    void winningSharesExperienceAndGold() {
        Fighter a = hero("A", new Stats(30, 0, 40, 5, 5));
        Fighter b = hero("B", new Stats(30, 0, 40, 5, 5));
        Battle battle = new Battle(List.of(a, b),
                List.of(enemy("Slime", new Stats(10, 0, 1, 0, 1), 30, 7), enemy("Rat", new Stats(10, 0, 1, 0, 1), 15, 3)),
                true, new Random(1));
        List<BattleEvent> events = playOut(battle);
        assertEquals(Battle.Outcome.VICTORY, battle.outcome());
        BattleEvent.Victory victory = assertInstanceOf(BattleEvent.Victory.class, events.get(events.size() - 1));
        assertEquals(10, battle.goldWon());
        assertEquals(23, victory.expEach(), "45 experience shared by two, rounded up");
        assertEquals(2, victory.levelUps().size(), "20 experience reaches level 2");
        assertEquals(2, a.level());
        assertEquals(new Stats(40, 2, 43, 7, 6), a.stats(), "the class's growth was added");
        assertThrows(IllegalStateException.class, () -> battle.playRound(List.of()));
    }

    @Test
    void losingEndsInDefeat() {
        Fighter frail = hero("Frail", new Stats(3, 0, 1, 0, 1));
        Battle battle = new Battle(List.of(frail), List.of(enemy("Ogre", new Stats(99, 0, 50, 50, 9), 0, 0)),
                true, new Random(2));
        List<BattleEvent> events = playOut(battle);
        assertEquals(Battle.Outcome.DEFEAT, battle.outcome());
        assertInstanceOf(BattleEvent.Defeat.class, events.get(events.size() - 1));
        assertFalse(frail.alive());
    }

    @Test
    void theFastestActsFirst() {
        Fighter slow = hero("Slow", new Stats(50, 0, 1, 0, 1));
        Fighter fast = enemy("Fast", new Stats(50, 0, 1, 0, 200), 0, 0);
        Battle battle = new Battle(List.of(slow), List.of(fast), true, new Random(3));
        List<BattleEvent> events = battle.playRound(List.of(new Battle.Order(slow, Battle.Command.ATTACK, fast)));
        BattleEvent.Attack first = assertInstanceOf(BattleEvent.Attack.class, events.get(0));
        assertSame(fast, first.attacker());
    }

    @Test
    void defendingHappensBeforeAnyoneActs() {
        Fighter guard = hero("Guard", new Stats(50, 0, 1, 0, 1));
        Fighter brute = enemy("Brute", new Stats(50, 0, 20, 0, 200), 0, 0);
        Battle battle = new Battle(List.of(guard), List.of(brute), true, new Random(4));
        List<BattleEvent> events = battle.playRound(List.of(new Battle.Order(guard, Battle.Command.DEFEND, null)));
        assertInstanceOf(BattleEvent.Defend.class, events.get(0));
        assertTrue(guard.defending());
    }

    @Test
    void fixedBattlesCantBeFled() {
        Fighter hero = hero("H", new Stats(50, 0, 1, 0, 999));
        Battle battle = new Battle(List.of(hero), List.of(enemy("Boss", new Stats(99, 0, 1, 99, 1), 0, 0)),
                false, new Random(5));
        List<BattleEvent> events = battle.playRound(List.of(new Battle.Order(hero, Battle.Command.FLEE, null)));
        assertTrue(events.stream().anyMatch(BattleEvent.CantFlee.class::isInstance));
        assertEquals(Battle.Outcome.ONGOING, battle.outcome());
    }

    @Test
    void aFastPartyUsuallyGetsAway() {
        Fighter hero = hero("H", new Stats(50, 0, 1, 0, 50));
        Battle battle = new Battle(List.of(hero), List.of(enemy("Snail", new Stats(99, 0, 1, 99, 1), 0, 0)),
                true, new Random(6));
        assertEquals(90, battle.fleeChance(), "capped at 90%");
        for (int i = 0; i < 20 && battle.outcome() == Battle.Outcome.ONGOING; i++) {
            battle.playRound(List.of(new Battle.Order(hero, Battle.Command.FLEE, null)));
        }
        assertEquals(Battle.Outcome.FLED, battle.outcome());
    }

    @Test
    void anAttackOnAFallenEnemyHitsAnotherOne() {
        Fighter hero = hero("H", new Stats(50, 0, 99, 0, 1));
        Fighter gone = enemy("Gone", new Stats(1, 0, 1, 0, 1), 0, 0);
        gone.takeDamage(5);
        Fighter other = enemy("Other", new Stats(500, 0, 1, 0, 0), 0, 0);
        Battle battle = new Battle(List.of(hero), List.of(gone, other), true, new Random(7));
        List<BattleEvent> events = battle.playRound(List.of(new Battle.Order(hero, Battle.Command.ATTACK, gone)));
        BattleEvent.Attack attack = (BattleEvent.Attack) events.stream()
                .filter(e -> e instanceof BattleEvent.Attack a && a.attacker() == hero).findFirst().orElseThrow();
        assertSame(other, attack.target());
    }

    @Test
    void theSameSeedGivesTheSameBattle() {
        List<String> first = describe(new Random(42));
        List<String> second = describe(new Random(42));
        assertEquals(first, second);
    }

    private static List<String> describe(Random random) {
        Battle battle = new Battle(List.of(hero("A", new Stats(30, 0, 8, 4, 5)), hero("B", new Stats(25, 0, 6, 3, 7))),
                List.of(enemy("Slime", new Stats(20, 0, 5, 2, 4), 5, 1), enemy("Rat", new Stats(14, 0, 6, 1, 8), 4, 1)),
                true, random);
        return playOut(battle).stream().map(Object::toString).toList();
    }

    @Test
    void levelsNeedMoreExperienceEachTime() {
        Fighter hero = hero("H", new Stats(10, 0, 1, 1, 1));
        assertEquals(20, hero.expForNextLevel());
        assertEquals(3, hero.gainExp(120), "20, 60 and 120 experience: levels 2, 3 and 4");
        assertEquals(4, hero.level());
        assertEquals(200, hero.expForNextLevel());
    }
}
