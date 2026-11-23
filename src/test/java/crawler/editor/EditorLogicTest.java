package crawler.editor;

import crawler.audio.AudioFilesTest;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Encounters;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.core.SpriteRef;
import crawler.render.AssetSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's rules, without any windows. */
class EditorLogicTest {
    @TempDir
    Path folder;

    @Test
    void roomsCorridorsAndRock() {
        DungeonMap map = MapEdits.blank("T", 12, 12);
        MapEdits.room(map, 2, 2, 4, 3);
        map.sealEdges();
        assertTrue(map.cell(3, 3).walkable);
        assertEquals(EdgeType.OPEN, map.edge(2, 2, Direction.EAST));
        assertEquals(EdgeType.WALL, map.edge(2, 2, Direction.NORTH));
        MapEdits.dig(map, 4, 3, 5, 3);
        map.sealEdges();
        assertEquals(EdgeType.OPEN, map.edge(4, 3, Direction.EAST), "digging out of a room breaks its wall");
        assertEquals(EdgeType.WALL, map.edge(5, 3, Direction.NORTH));
        map.putObject(MapObject.create(ObjectType.CHEST, 5, 3));
        MapEdits.fillRock(map, 5, 3, 5, 3);
        assertNull(map.object(5, 3), "rock buries objects");
    }

    @Test
    void objectsGoOnFreeFloorAwayFromTheStart() {
        DungeonMap map = MapEdits.blank("T", 10, 10);
        MapEdits.room(map, 2, 2, 6, 6);
        map.setStart(4, 4, Direction.NORTH);
        assertNotNull(MapEdits.objectProblem(map, ObjectType.CHEST, 0, 0), "rock");
        assertNotNull(MapEdits.objectProblem(map, ObjectType.NPC, 4, 4), "the start");
        assertNull(MapEdits.objectProblem(map, ObjectType.EVENT, 4, 4), "events may sit on the start");
        map.putObject(MapObject.create(ObjectType.NPC, 3, 3));
        assertNotNull(MapEdits.objectProblem(map, ObjectType.CHEST, 3, 3), "taken");
    }

    @Test
    void checksSpotUnknownEnemiesAndBattlesWithoutEnemies() {
        DungeonMap map = MapEdits.blank("T", 8, 8);
        MapEdits.room(map, 1, 1, 6, 6);
        map.sealEdges();
        map.setEncounters(new Encounters(List.of("slime", "dragn"), 10, 1, 2));
        map.putObject(new MapObject(ObjectType.EVENT, 2, 2, "", "Boss!", null, null, true, List.of("king")));
        MapCheck check = MapCheck.of(map, AssetSource.NONE, Set.of("slime"));
        assertTrue(check.problems().stream().anyMatch(p -> p.contains("dragn")), check.problems().toString());
        assertTrue(check.problems().stream().anyMatch(p -> p.contains("king")), check.problems().toString());

        map.setEncounters(new Encounters(List.of(), 10, 1, 2));
        assertTrue(MapCheck.of(map, AssetSource.NONE, null).problems().stream()
                .anyMatch(p -> p.contains("no enemies are chosen")));
    }

    @Test
    void checksSpotMissingFilesIncludingMusic() {
        DungeonMap map = MapEdits.blank("T", 4, 4);
        map.setMusic("assets/gone.wav");
        assertTrue(MapCheck.of(map, AssetSource.NONE).problems().stream().anyMatch(p -> p.contains("assets/gone.wav")));
    }

    @Test
    void importedPicturesAndSoundsAreSavedIntoTheAssetsFolder() throws IOException {
        Path art = Files.createDirectories(folder.resolve("art"));
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", art.resolve("Hero Face.png").toFile());
        Files.write(art.resolve("Theme.wav"), AudioFilesTest.wav());
        Files.writeString(art.resolve("song.mp3"), "ID3 not really");

        AssetStore store = new AssetStore(null);
        String picture = store.importImage(art.resolve("Hero Face.png"));
        String sound = store.importSound(art.resolve("Theme.wav"));
        assertEquals("assets/hero-face.png", picture);
        assertEquals("assets/theme.wav", sound);
        assertNotNull(store.data(sound));
        IOException e = assertThrows(IOException.class, () -> store.importSound(art.resolve("song.mp3")));
        assertTrue(e.getMessage().contains("WAV"), e.getMessage());

        Path project = Files.createDirectories(folder.resolve("project"));
        Map<String, String> renamed = store.saveImages(List.of(picture, sound), project);
        assertTrue(renamed.isEmpty());
        assertTrue(Files.isRegularFile(project.resolve("assets/hero-face.png")));
        assertTrue(Files.isRegularFile(project.resolve("assets/theme.wav")));
    }

    @Test
    void savingAMapRenamesClashingFilesEverywhere() throws IOException {
        Path art = Files.createDirectories(folder.resolve("art"));
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", art.resolve("a.png").toFile());
        AssetStore store = new AssetStore(null);
        String picture = store.importImage(art.resolve("a.png"));

        Path target = Files.createDirectories(folder.resolve("target/assets")).getParent();
        Files.writeString(target.resolve("assets/a.png"), "something else entirely");
        DungeonMap map = MapEdits.blank("T", 4, 4);
        map.putObject(new MapObject(ObjectType.NPC, 0, 0, "", "", new SpriteRef(picture, 2, 4), null, true));
        Map<String, String> renamed = store.saveImages(map, target);
        assertEquals("assets/a-2.png", renamed.get("assets/a.png"));
        DungeonMap fixed = map.withRenamedAssets(renamed);
        assertEquals(new SpriteRef("assets/a-2.png", 2, 4), fixed.object(0, 0).sprite());
        assertFalse(fixed.contentEquals(map));
    }
}
