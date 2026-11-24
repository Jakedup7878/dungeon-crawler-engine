package crawler.game;

import crawler.battle.Fighter;
import crawler.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Enemy tiers. */
class TierTest {
    static final Tier LOW = new Tier("low", "Low", 0xFFFFFF, 99, 100, 100, 100, 100, 100, 100, true);
    static final Tier HIGH = new Tier("high", "High", 0xFF0000, 1, 200, 150, 50, 100, 300, 0, false);

    static EnemyType enemy(String id, String tier) {
        return new EnemyType(id, id, new Stats(10, 3, 10, 10, 10), 10, 10, null, null, tier);
    }

    static GameContent content(List<Tier> tiers) {
        return new GameContent(ProjectSettings.DEFAULT, List.of(), List.of(),
                List.of(enemy("rat", "low"), enemy("bat", "low"), enemy("ogre", "high"), enemy("imp", null)), List.of(), List.of(), tiers);
    }

    static Game game() {
        return new Game(GameTest.corridor(), content(List.of(LOW, HIGH)), new Random(3));
    }

    @Test
    void aTierChangesItsEnemies() {
        Game game = game();
        game.startBattle(List.of(game.content().enemy("ogre"), game.content().enemy("imp")), true);
        Fighter ogre = game.battle().enemies().get(0);
        assertEquals(new Stats(20, 3, 15, 5, 10), ogre.stats());
        assertEquals(20, ogre.hp());
        assertEquals(30, ogre.expReward());
        assertEquals(0, ogre.goldReward());
        assertEquals(2, ogre.tierRank());
        assertFalse(game.battle().canFlee(), "nobody runs from this tier");
        Fighter imp = game.battle().enemies().get(1);
        assertNull(imp.tier());
        assertEquals(10, imp.hp());
        assertEquals(0, imp.tierRank());
    }

    @Test
    void aWholeTierCanBeNamed() {
        GameContent content = content(List.of(LOW, HIGH));
        assertEquals(List.of("rat", "bat"), content.enemiesFor("tier:low").stream().map(EnemyType::id).toList());
        assertEquals(List.of(), content.enemiesFor("tier:gone"));
        DungeonMap map = GameTest.corridor();
        map.putObject(new MapObject(ObjectType.EVENT, 1, 0, "", "", null, null, true, List.of("tier:high", "tier:low")));
        Game game = new Game(map, content, new Random(1));
        game.movement().perform(MoveAction.FORWARD);
        assertEquals("ogre", game.battle().enemies().get(0).enemyId());
        assertEquals("low", game.battle().enemies().get(1).tier().id());
    }

    @Test
    void lightTiersAreRare() {
        int ogres = 0;
        int others = 0;
        for (int i = 0; i < 300; i++) {
            DungeonMap map = GameTest.corridor();
            map.setEncounters(new Encounters(List.of("tier:low", "ogre", "rat"), 100, 1, 1));
            Game game = new Game(map, content(List.of(LOW, HIGH)), new Random(i));
            game.movement().perform(MoveAction.FORWARD);
            if (game.battle().enemies().get(0).enemyId().equals("ogre")) {
                ogres++;
            } else {
                others++;
            }
        }
        assertTrue(ogres < 15 && others > 285, ogres + " ogres");
    }

    @Test
    void scriptsCanAskForTheTier() {
        Game game = game();
        Item bane = new Item("bane", "Bane", ItemKind.WEAPON, "", Stats.ZERO, ItemTarget.SELF, false, List.of(), null, null,
                "on hit\n if target.tier >= 2\n  say \"tier {target.tier}\"\n end");
        game.inventory().add(bane, 1);
        game.equip(game.heroes().get(0), bane);
        game.startBattle(List.of(game.content().enemy("ogre")), true);
        boolean said = false;
        for (int i = 0; i < 5 && !said && game.battle().outcome() == crawler.battle.Battle.Outcome.ONGOING; i++) {
            said = game.battle().playRound(List.of()).contains(new crawler.battle.BattleEvent.Note("tier 2"));
        }
        assertTrue(said);
    }

    @Test
    void theBuiltInTiersAreUsedWhenThereAreNone() {
        assertEquals(List.of("common", "elite", "boss"), content(List.of()).availableTiers().stream().map(Tier::id).toList());
        assertEquals(3, content(List.of()).tierRank("boss"));
    }

    @Test
    void tiersSurviveTheirFiles(@TempDir Path folder) throws IOException {
        GameData.saveTiers(folder, List.of(LOW, HIGH));
        assertEquals(List.of(LOW, HIGH), GameData.loadTiers(folder));
        List<EnemyType> enemies = List.of(enemy("ogre", "high"), enemy("imp", null));
        GameData.saveEnemies(folder, enemies);
        assertEquals(enemies, GameData.loadEnemies(folder));
        assertTrue(enemies.get(0).equals(enemies.get(0).withSprite(null)));
        GameContent.load(ProjectSettings.DEFAULT, ProjectFiles.folder(folder));
        GameData.saveTiers(folder, List.of(LOW));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GameContent.load(ProjectSettings.DEFAULT,
                ProjectFiles.folder(folder))).getMessage().contains("ogre is in a tier that doesn't exist: high"));
        for (String wrong : List.of("color = red", "flee = maybe", "weight = 0", "hp = 0")) {
            Files.writeString(folder.resolve("tiers.txt"), "[tier a]\n" + wrong);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> GameData.loadTiers(folder)).getMessage()
                    .startsWith("tiers.txt, line 2"));
        }
    }
}
