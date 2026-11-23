package crawler.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** project.txt, party.txt, classes.txt, enemies.txt and animations.txt. */
class ProjectFilesTest {
    @TempDir
    Path folder;

    @Test
    void aNewProjectHasItsFoldersAndDefaultSettings() throws IOException {
        Path root = folder.resolve("Test Quest");
        GameProject project = GameProject.create(root, " Test Quest ");
        assertTrue(Files.isDirectory(root.resolve("maps")));
        assertTrue(Files.isDirectory(root.resolve("assets")));
        GameProject again = GameProject.open(root);
        assertEquals("Test Quest", again.name());
        assertEquals(root.toAbsolutePath().resolve("maps/b1f.txt"), again.startMapFile());
        assertEquals(ProjectSettings.DEFAULT, again.settings());
        assertEquals(root.toAbsolutePath(), GameProject.findRoot(root.resolve("maps/deep/b9f.txt")));
        assertNull(GameProject.findRoot(folder.resolve("elsewhere.txt")));
        assertTrue(project.contains(root.resolve("maps/x.txt")));
    }

    @Test
    void settingsSurviveSaving() throws IOException {
        Path root = folder.resolve("p");
        GameProject project = GameProject.create(root, "P");
        ProjectSettings settings = new ProjectSettings(new SpriteRef("assets/npc.png", 4, 6), SpriteRef.still("assets/c.png"),
                null, "assets/battle.mid", "assets/win.wav", false, 6, 150);
        project.setSettings(settings);
        project.save();
        assertEquals(settings, GameProject.open(root).settings());
        assertEquals(java.util.Set.of("assets/npc.png", "assets/c.png", "assets/battle.mid", "assets/win.wav"),
                settings.assetReferences());
    }

    @Test
    void badProjectFilesAreReported() throws IOException {
        Files.writeString(folder.resolve("project.txt"), "name = X\nstart = maps/a.txt\nparty_size = 9\n");
        IOException e = assertThrows(IOException.class, () -> GameProject.open(folder));
        assertTrue(e.getMessage().contains("line 3") && e.getMessage().contains("party_size"), e.getMessage());

        Files.writeString(folder.resolve("project.txt"), "name = X\n");
        assertTrue(assertThrows(IOException.class, () -> GameProject.open(folder)).getMessage().contains("start"));
    }

    @Test
    void partyClassesEnemiesAndAnimationsSurviveSaving() throws IOException {
        List<PartyMember> party = List.of(
                new PartyMember("Aldric", "Fighter", 3, new Stats(42, 0, 11, 9, 5), "assets/aldric.png"),
                new PartyMember("Mira", "mage", 1, new Stats(18, 24, 3, 3, 7), null));
        List<CharacterClass> classes = List.of(
                new CharacterClass("knight", "Knight", new Stats(40, 0, 10, 10, 3), new Stats(9, 0, 2, 3, 0),
                        "heavy-slash", "assets/knight.png"));
        List<EnemyType> enemies = List.of(
                new EnemyType("slime", "Blue Slime", new Stats(12, 0, 4, 2, 3), 5, 2,
                        new SpriteRef("assets/slime.png", 4, 6), "splash"),
                new EnemyType("giant-rat", "Giant Rat", new Stats(8, 0, 5, 1, 8), 3, 1, null));
        List<AttackAnimation> animations = List.of(
                new AttackAnimation("heavy-slash", "Heavy slash", new SpriteRef("assets/slash.png", 6, 18), "assets/hit.wav", 150),
                AttackAnimation.create("splash", "Splash"));
        GameData.saveParty(folder, party);
        GameData.saveClasses(folder, classes);
        GameData.saveEnemies(folder, enemies);
        GameData.saveAnimations(folder, animations);
        assertEquals(party, GameData.loadParty(folder));
        assertEquals(classes, GameData.loadClasses(folder));
        assertEquals(enemies, GameData.loadEnemies(folder));
        assertEquals(animations, GameData.loadAnimations(folder));
    }

    @Test
    void missingFilesMeanNoneYet() throws IOException {
        assertTrue(GameData.loadParty(folder).isEmpty());
        assertTrue(GameData.loadClasses(folder).isEmpty());
        assertTrue(GameData.loadEnemies(folder).isEmpty());
        assertTrue(GameData.loadAnimations(folder).isEmpty());
    }

    @Test
    void mistakesInDataFilesPointAtTheLine() throws IOException {
        Files.writeString(folder.resolve("enemies.txt"), "[enemy a]\nname = A\n\n[enemy a]\nname = B\n");
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class, () -> GameData.loadEnemies(folder));
        assertTrue(twice.getMessage().contains("line 4") && twice.getMessage().contains("two enemies"), twice.getMessage());

        Files.writeString(folder.resolve("classes.txt"), "[class mage]\ngrowth_luck = 3\n");
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> GameData.loadClasses(folder));
        assertTrue(unknown.getMessage().contains("line 2") && unknown.getMessage().contains("growth_luck"), unknown.getMessage());

        Files.writeString(folder.resolve("animations.txt"), "[animation x]\nsize = 9000\n");
        IllegalArgumentException size = assertThrows(IllegalArgumentException.class, () -> GameData.loadAnimations(folder));
        assertTrue(size.getMessage().contains("size should be"), size.getMessage());

        Files.writeString(folder.resolve("party.txt"), "[member]\nname = x\n".repeat(7));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> GameData.loadParty(folder))
                .getMessage().contains("at most 6"));
    }

    @Test
    void contentFallsBackToBuiltInClassesAndAStarterParty() {
        GameContent content = GameContent.EMPTY;
        assertEquals(4, content.availableClasses().size());
        List<PartyMember> starters = content.startingParty();
        assertEquals(3, starters.size());
        assertEquals("Fighter", starters.get(0).characterClass());
        assertEquals("fighter", content.classOf(starters.get(0)).id());
        assertNull(content.classOf(new PartyMember("X", "Bard", 1, Stats.ZERO, null)), "no class called Bard");
        assertFalse(content.availableClasses().isEmpty());
    }
}
