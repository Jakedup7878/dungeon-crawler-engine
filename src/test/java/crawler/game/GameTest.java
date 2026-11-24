package crawler.game;

import crawler.battle.Battle;
import crawler.battle.Fighter;
import crawler.core.DungeonMap;
import crawler.core.Encounters;
import crawler.core.EnemyType;
import crawler.core.GameContent;
import crawler.core.MapLoader;
import crawler.core.MapObject;
import crawler.core.MoveAction;
import crawler.core.MoveResult;
import crawler.core.ObjectType;
import crawler.core.PartyMember;
import crawler.core.ProjectSettings;
import crawler.core.Stats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Chests, NPCs, events and battles as the game runs them. */
class GameTest {
    static final EnemyType SLIME = new EnemyType("slime", "Blue Slime", new Stats(1, 0, 1, 0, 1), 5, 4, null);

    /** A corridor four cells long; the party starts at the west end, facing east. */
    static DungeonMap corridor() {
        return MapLoader.parse("t.txt", List.of(
                "+---+---+---+---+",
                "| >             |",
                "+---+---+---+---+"));
    }

    static GameContent content(int startingGold) {
        return new GameContent(new ProjectSettings(null, null, null, null, null, false, 4, startingGold),
                List.of(new PartyMember("Hero", "Fighter", 1, new Stats(40, 0, 50, 5, 9), null)),
                List.of(), List.of(SLIME), List.of());
    }

    @Test
    void walkingIntoAChestOpensIt() {
        DungeonMap map = corridor();
        map.putObject(new MapObject(ObjectType.CHEST, 1, 0, "Chest", "A key", null, null, true));
        Game game = new Game(map, content(0), new Random(1));
        MoveResult result = game.movement().perform(MoveAction.FORWARD);
        assertEquals(MoveResult.Outcome.BLOCKED, result.outcome());
        assertTrue(game.message().text().contains("A key"));
        assertTrue(game.isOpened(map.object(1, 0)));
        game.dismissMessage();
        game.interact();
        assertTrue(game.message().text().contains("empty now"));
    }

    @Test
    void npcsTalkAndEventsHappenOnce() {
        DungeonMap map = corridor();
        map.putObject(new MapObject(ObjectType.EVENT, 1, 0, "Trap", "Click!", null, null, true));
        map.putObject(new MapObject(ObjectType.NPC, 3, 0, "Sage", "Hello.", null, null, true));
        Game game = new Game(map, content(0), new Random(1));
        game.movement().perform(MoveAction.FORWARD);
        assertEquals("Click!", game.message().text());
        game.dismissMessage();
        game.movement().perform(MoveAction.BACKWARD);
        game.movement().perform(MoveAction.FORWARD);
        assertNull(game.message(), "a one-time event doesn't happen again");
        game.movement().perform(MoveAction.FORWARD);
        game.movement().perform(MoveAction.FORWARD);
        game.interact();
        assertEquals("Sage", game.message().title());
    }

    @Test
    void randomBattlesStartAsYouWalk() {
        DungeonMap map = corridor();
        map.setEncounters(new Encounters(List.of("slime"), 100, 2, 3));
        Game game = new Game(map, content(0), new Random(1));
        assertNull(game.battle());
        game.movement().perform(MoveAction.FORWARD);
        Battle battle = game.battle();
        assertNotNull(battle, "a 100% chance always starts a battle");
        assertTrue(battle.enemies().size() >= 2 && battle.enemies().size() <= 3);
        assertTrue(battle.canFlee());
        assertEquals("Blue Slime A", battle.enemies().get(0).name(), "several of a kind get letters");
    }

    @Test
    void unknownEnemiesDontStartABattle() {
        DungeonMap map = corridor();
        map.setEncounters(new Encounters(List.of("dragon"), 100, 1, 1));
        Game game = new Game(map, content(0), new Random(1));
        game.movement().perform(MoveAction.FORWARD);
        assertNull(game.battle());
    }

    @Test
    void eventBattlesCantBeFledAndPayOut() {
        DungeonMap map = corridor();
        map.putObject(new MapObject(ObjectType.EVENT, 1, 0, "Boss", "Grr!", null, null, true, List.of("slime")));
        Game game = new Game(map, content(10), new Random(1));
        game.movement().perform(MoveAction.FORWARD);
        assertEquals("Grr!", game.message().text(), "the message comes first");
        Battle battle = game.battle();
        assertFalse(battle.canFlee());
        while (battle.outcome() == Battle.Outcome.ONGOING) {
            battle.playRound(List.of());
        }
        assertEquals(Battle.Outcome.VICTORY, battle.outcome());
        game.endBattle();
        assertNull(game.battle());
        assertEquals(14, game.gold(), "10 to start with, plus 4 from the slime");
    }

    @Test
    void losingEndsTheGame() {
        Game game = new Game(corridor(), new GameContent(ProjectSettings.DEFAULT,
                List.of(new PartyMember("Frail", "Fighter", 1, new Stats(1, 0, 1, 0, 1), null)), List.of(),
                List.of(new EnemyType("ogre", "Ogre", new Stats(99, 0, 99, 99, 99), 0, 0, null)), List.of()),
                new Random(1));
        game.startBattle(List.of(game.content().enemy("ogre")), true);
        Battle battle = game.battle();
        while (battle.outcome() == Battle.Outcome.ONGOING) {
            battle.playRound(List.of());
        }
        game.endBattle();
        assertTrue(game.isGameOver());
    }

    @Test
    void aCreatedPartyReplacesTheReadyMadeOne() {
        Game game = new Game(corridor(), content(0), new Random(1));
        game.replaceParty(List.of(GameContent.newMember("Kira", game.content().availableClasses().get(3))));
        List<Fighter> heroes = game.heroes();
        assertEquals(1, heroes.size());
        assertEquals("Kira", heroes.get(0).name());
        assertEquals("Thief", heroes.get(0).characterClass());
    }
}
