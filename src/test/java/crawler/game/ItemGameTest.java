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

/** Items outside battle: files, chests, the inventory and equipment. */
class ItemGameTest {
    static final Item POTION = Item.create("potion", "Potion");
    static final Item SWORD = weapon("sword", 5, "fighter");
    static final Item AXE = weapon("axe", 8, "Fighter");
    static final Item WAND = weapon("wand", 1, "mage");

    static Item weapon(String id, int attack, String classes) {
        return new Item(id, id, ItemKind.WEAPON, "", new Stats(0, 0, attack, 0, 0), ItemTarget.SELF, false,
                List.of(classes), null, "flash", "");
    }

    static Game game(DungeonMap map) {
        return new Game(map, new GameContent(new ProjectSettings(null, null, null, null, null, false, 4, 0,
                ItemStack.parse("potion x2, axe, wand, nonsense")),
                List.of(new PartyMember("Hero", "Fighter", 1, new Stats(40, 0, 10, 5, 9), null, List.of("sword", "wand", "gone"))),
                List.of(), List.of(GameTest.SLIME), List.of(), List.of(POTION, SWORD, AXE, WAND)), new Random(1));
    }

    @Test
    void theGameStartsWithItsItemsAndGear() {
        Game game = game(GameTest.corridor());
        assertEquals(2, game.inventory().count(POTION));
        Fighter hero = game.heroes().get(0);
        assertEquals(SWORD, hero.equipped(ItemKind.WEAPON), "the wand is only for mages");
        assertEquals(15, hero.stats().attack());
        assertEquals("flash", hero.attackAnimation());
    }

    @Test
    void equippingSwapsWithTheInventory() {
        Game game = game(GameTest.corridor());
        Fighter hero = game.heroes().get(0);
        assertFalse(game.equip(hero, WAND));
        assertTrue(game.equip(hero, AXE));
        assertEquals(18, hero.stats().attack());
        assertEquals(0, game.inventory().count(AXE));
        assertEquals(1, game.inventory().count(SWORD));
        assertTrue(game.unequip(hero, ItemKind.WEAPON));
        assertEquals(10, hero.stats().attack());
        assertNull(hero.attackAnimation());
        assertFalse(game.unequip(hero, ItemKind.WEAPON));
        assertFalse(game.equip(hero, Item.create("potion", "Potion")), "a potion can't be worn");
    }

    @Test
    void potionsWorkOutsideBattleAndArentWasted() {
        Game game = game(GameTest.corridor());
        Fighter hero = game.heroes().get(0);
        assertEquals("It would have no effect.", game.useItem(POTION, hero));
        assertEquals(2, game.inventory().count(POTION));
        game.startBattle(List.of(GameTest.SLIME), true);
        assertEquals("You can't use that now.", game.useItem(POTION, hero));
    }

    @Test
    void harmOutsideBattleNeverKills() {
        Item poisonous = new Item("bad", "Bad", ItemKind.CONSUMABLE, "", Stats.ZERO, ItemTarget.ALLY, true, List.of(),
                null, null, "on use\n damage target 999\n gold 3");
        Game game = game(GameTest.corridor());
        game.inventory().add(poisonous, 1);
        Fighter hero = game.heroes().get(0);
        assertEquals("Hero loses 39 HP. You gain 3 gold.", game.useItem(poisonous, hero));
        assertEquals(1, hero.hp());
        assertEquals(3, game.gold());
        assertEquals("Hero recovers 20 HP.", game.useItem(POTION, hero));
        assertEquals(1, game.inventory().count(POTION));
    }

    @Test
    void chestsGiveTheirItemsOnce() {
        DungeonMap map = GameTest.corridor();
        map.putObject(new MapObject(ObjectType.CHEST, 1, 0, "Chest", "", null, null, true, List.of(),
                ItemStack.parse("potion x3")));
        Game game = game(map);
        game.interact();
        assertEquals("Inside you find:\nPotion x3", game.message().text());
        game.dismissMessage();
        game.interact();
        assertEquals(5, game.inventory().count(POTION));
    }

    @Test
    void theInventoryHoldsNinetyNineOfEach() {
        Inventory bag = new Inventory();
        assertEquals(99, bag.add(POTION, 500));
        assertEquals(0, bag.add(POTION, 1));
    }

    @Test
    void stacksAreReadAndWritten() {
        assertEquals("potion x3, sword", ItemStack.text(ItemStack.parse(" potion  X3 ,sword x1")));
        for (String wrong : List.of("potion x0", "potion three", "potion x100", "a b c")) {
            assertThrows(IllegalArgumentException.class, () -> ItemStack.parse(wrong), wrong);
        }
    }

    @Test
    void chestItemsSurviveTheMapFile() {
        DungeonMap map = GameTest.corridor();
        map.putObject(new MapObject(ObjectType.CHEST, 1, 0, "Chest", "", null, null, true, List.of(),
                ItemStack.parse("potion x3, sword")));
        DungeonMap back = MapLoader.parse("t", MapWriter.write(map).lines().toList());
        assertEquals(map.object(1, 0), back.object(1, 0));
    }

    @Test
    void itemFilesRoundTrip(@TempDir Path folder) throws IOException {
        Item item = new Item("blade", "Blade", ItemKind.WEAPON, "Sharp.", new Stats(1, 0, 4, -2, 0), ItemTarget.SELF, false,
                List.of("fighter", "thief"), new SpriteRef("assets/b.png", 2, 5), "slash",
                "# comment\non hit\n  say \"a \\ b\"\n  heal user 1");
        GameData.saveItems(folder, List.of(item));
        Item back = GameData.loadItems(folder).get(0);
        assertEquals(item.scriptText(), back.scriptText());
        assertEquals(item.bonus(), back.bonus());
        assertEquals(item.classes(), back.classes());
        assertEquals(item.icon(), back.icon());
        assertEquals("slash", back.animation());
        assertFalse(back.consumed());
        assertTrue(back.script().has("hit"));
    }

    @Test
    void gearSurvivesThePartyAndClassFiles(@TempDir Path folder) throws IOException {
        List<PartyMember> party = List.of(new PartyMember("A", "B", 1, PartyMember.DEFAULT_STATS, null, List.of("x", "y")));
        GameData.saveParty(folder, party);
        assertEquals(party, GameData.loadParty(folder));
        List<CharacterClass> classes = List.of(new CharacterClass("a", "A", CharacterClass.DEFAULT_BASE,
                CharacterClass.DEFAULT_GROWTH, null, null, List.of("x")));
        GameData.saveClasses(folder, classes);
        assertEquals(classes, GameData.loadClasses(folder));
        GameProject project = GameProject.create(folder.resolve("p"), "P");
        project.setSettings(new ProjectSettings(null, null, null, null, null, true, 4, 0, ItemStack.parse("x x2")));
        project.save();
        assertEquals(project.settings(), GameProject.open(folder.resolve("p")).settings());
    }

    @Test
    void mistakesInItemFilesNameTheItem(@TempDir Path folder) throws IOException {
        for (String[] c : new String[][]{{"script = on use\\n  fly", "the script of 'a', line 2: unknown command 'fly'"},
                {"kind = hat", "unknown kind"}, {"target = moon", "unknown target"}, {"consumed = maybe", "yes or no"},
                {"attack = 9999", "attack should be"}}) {
            Files.writeString(folder.resolve("items.txt"), "[item a]\n" + c[0] + "\n");
            String said = assertThrows(IllegalArgumentException.class, () -> GameData.loadItems(folder)).getMessage();
            assertTrue(said.contains(c[1]) && said.startsWith("items.txt, line 2"), said);
        }
    }
}
