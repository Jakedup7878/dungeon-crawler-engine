package crawler.battle;

import crawler.core.Inventory;
import crawler.core.Item;
import crawler.core.ItemKind;
import crawler.core.ItemTarget;
import crawler.core.Stats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Items in battle: using them, and what weapons and armor do. */
class ItemBattleTest {
    private final Fighter hero = BattleTest.hero("Hero", new Stats(50, 10, 10, 0, 50));
    private final Fighter friend = BattleTest.hero("Friend", new Stats(50, 0, 1, 0, 49));
    private final Fighter ogre = BattleTest.enemy("Ogre", new Stats(500, 0, 10, 0, 1), 0, 0);
    private final Inventory bag = new Inventory();
    /** Nothing misses: the seed's dice are replaced by middling ones. */
    private final Random middling = new Random() {
        @Override
        public int nextInt(int bound) {
            return bound / 2;
        }
    };
    private final Battle battle = new Battle(List.of(hero, friend), List.of(ogre), true, middling, bag);

    private static Item item(ItemKind kind, ItemTarget target, Stats bonus, String script) {
        return new Item("thing", "Thing", kind, "", bonus, target, true, List.of(), null, null, script);
    }

    private static Item potion(ItemTarget target, String script) {
        return item(ItemKind.CONSUMABLE, target, Stats.ZERO, script);
    }

    private List<BattleEvent> use(Item item, Fighter target) {
        return battle.playRound(List.of(new Battle.Order(hero, Battle.Command.ITEM, target, item),
                new Battle.Order(friend, Battle.Command.DEFEND, null)));
    }

    private List<BattleEvent> attack() {
        return battle.playRound(List.of(new Battle.Order(hero, Battle.Command.ATTACK, ogre),
                new Battle.Order(friend, Battle.Command.DEFEND, null)));
    }

    @Test
    void aPotionHealsAndIsUsedUp() {
        Item potion = potion(ItemTarget.ALLY, "on use\n heal target 20");
        bag.add(potion, 2);
        friend.takeDamage(30);
        List<BattleEvent> events = use(potion, friend);
        assertTrue(events.contains(new BattleEvent.Heal(friend, 20, false)));
        assertEquals(1, bag.count(potion));
    }

    @Test
    void healingStopsAtTheMaximum() {
        Item potion = potion(ItemTarget.ALLY, "on use\n heal target 20");
        bag.add(potion, 1);
        friend.takeDamage(5);
        assertTrue(use(potion, friend).contains(new BattleEvent.Heal(friend, 5, false)));
    }

    @Test
    void onlyRevivingWorksOnTheFallen() {
        Item potion = potion(ItemTarget.ALLY, "on use\n heal target 20");
        bag.add(potion, 1);
        friend.takeDamage(999);
        use(potion, friend);
        assertFalse(friend.alive());
        assertEquals(1, bag.count(potion), "it had no effect, so it isn't used up");
        Item feather = new Item("feather", "Feather", ItemKind.CONSUMABLE, "", Stats.ZERO, ItemTarget.FALLEN, true,
                List.of(), null, null, "on use\n revive target target.maxhp / 2");
        bag.add(feather, 1);
        assertTrue(use(feather, friend).contains(new BattleEvent.Revived(friend, 25)));
    }

    @Test
    void anItemThatIsntThereCantBeUsed() {
        Item potion = potion(ItemTarget.ALLY, "on use\n heal target 20");
        friend.takeDamage(30);
        use(potion, friend);
        assertFalse(friend.hp() > 20);
    }

    @Test
    void aBombCanWinTheBattle() {
        Item bomb = potion(ItemTarget.ENEMIES, "on use\n damage enemies 9999\n gold 5");
        bag.add(bomb, 1);
        use(bomb, null);
        assertEquals(Battle.Outcome.VICTORY, battle.outcome());
        assertEquals(5, battle.goldFromItems());
    }

    @Test
    void equipmentChangesStatsAndNeverEndsBelowItsLimits() {
        hero.equip(item(ItemKind.WEAPON, ItemTarget.SELF, new Stats(10, 0, 5, -99, 1), ""));
        assertEquals(new Stats(60, 10, 15, 0, 51), hero.stats());
        hero.heal(99);
        assertEquals(60, hero.hp());
        hero.unequip(ItemKind.WEAPON);
        assertEquals(50, hero.hp(), "HP above the new maximum is lost");
    }

    @Test
    void weaponsActWhenTheyHit() {
        hero.takeDamage(20);
        hero.equip(item(ItemKind.WEAPON, ItemTarget.SELF, Stats.ZERO,
                "on hit\n damage target 7\n heal user damage\n poison target 3 for 2"));
        List<BattleEvent> events = attack();
        assertTrue(events.contains(new BattleEvent.Hurt(ogre, 7, "", false)));
        assertTrue(events.contains(new BattleEvent.Heal(hero, 10, false)));
        assertTrue(events.contains(new BattleEvent.Hurt(ogre, 3, "poison", false)), "poison at the end of the round");
    }

    @Test
    void poisonRunsOut() {
        ogre.poison(3, 2);
        assertEquals(3, ogre.tickPoison());
        assertEquals(3, ogre.tickPoison());
        assertEquals(0, ogre.tickPoison());
    }

    @Test
    void armorActsWhenItsWearerIsHit() {
        for (Fighter f : List.of(hero, friend)) {
            f.equip(item(ItemKind.BODY, ItemTarget.SELF, Stats.ZERO, "on hurt\n damage target 2"));
        }
        assertTrue(attack().contains(new BattleEvent.Hurt(ogre, 2, "", false)));
    }

    @Test
    void stunnedFightersLoseTheirTurn() {
        Item bolt = potion(ItemTarget.ENEMY, "on use\n stun target 1");
        bag.add(bolt, 1);
        List<BattleEvent> events = use(bolt, ogre);
        assertTrue(events.stream().noneMatch(e -> e instanceof BattleEvent.Attack a && a.attacker() == ogre));
        assertTrue(attack().stream().anyMatch(e -> e instanceof BattleEvent.Attack a && a.attacker() == ogre));
    }

    @Test
    void boostsWearOffAndBlocksRunAtTheirMoments() {
        hero.equip(item(ItemKind.HEAD, ItemTarget.SELF, Stats.ZERO,
                "on battle\n boost user attack 4 for 2\non round\n say \"round {round}\""));
        assertTrue(attack().contains(new BattleEvent.Note("round 1")));
        assertEquals(14, hero.stats().attack());
        assertTrue(attack().contains(new BattleEvent.Note("round 2")));
        assertEquals(10, hero.stats().attack());
    }

    @Test
    void conditionsEndWithTheBattle() {
        hero.boost("attack", 5, 99);
        hero.poison(1, 99);
        ogre.takeDamage(495);
        attack();
        assertEquals(Battle.Outcome.VICTORY, battle.outcome());
        assertFalse(hero.poisoned());
        assertEquals(10, hero.stats().attack());
    }

    @Test
    void poisonCanEndTheBattleInDefeat() {
        Fighter last = BattleTest.hero("Last", new Stats(5, 0, 1, 0, 1));
        Battle b = new Battle(List.of(last), List.of(ogre), true, middling);
        last.poison(99, 1);
        ogre.stun(1);
        b.playRound(List.of());
        assertEquals(Battle.Outcome.DEFEAT, b.outcome());
    }
}
