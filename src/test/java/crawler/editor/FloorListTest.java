package crawler.editor;

import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.GameProject;
import crawler.core.MapLoader;
import crawler.core.MapObject;
import crawler.core.MapWriter;
import crawler.core.ObjectType;
import crawler.render.AssetSource;
import crawler.render.ObjectLooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The project's list of floors, adding to it, and the guide. */
class FloorListTest {
    @TempDir
    Path folder;

    private GameProject project(String... floors) throws IOException {
        GameProject project = GameProject.create(folder.resolve("p"), "P");
        for (String floor : floors) {
            MapWriter.save(MapEdits.blank(floor, 5, 5), project.mapsFolder().resolve(floor + ".txt"));
        }
        return project;
    }

    @Test
    void floorsAreInNumberOrderUntilTheyAreMoved() throws IOException {
        GameProject project = project("b10f", "b2f", "b1f");
        assertEquals(List.of("maps/b1f.txt", "maps/b2f.txt", "maps/b10f.txt"), project.floors());
        project.setFloors(List.of("maps/b2f.txt", "maps/gone.txt", "maps/b1f.txt"));
        project.save();
        GameProject again = GameProject.open(project.root());
        assertEquals(List.of("maps/b2f.txt", "maps/b1f.txt", "maps/b10f.txt"), again.floors(),
                "missing floors are left out, new ones come last");
        assertEquals("maps/b2f.txt", again.floorBeside("maps/b1f.txt", -1));
        assertEquals("maps/b10f.txt", again.floorBeside("maps/b1f.txt", 1));
        assertNull(again.floorBeside("maps/b2f.txt", -1));
        assertNull(again.floorBeside("maps/b10f.txt", 1));
        assertNull(again.floorBeside("maps/nowhere.txt", 1));
    }

    @Test
    void aFloorCanStartWithAnothersLayout() {
        DungeonMap map = MapEdits.blank("A", 8, 8);
        MapEdits.room(map, 1, 1, 5, 5);
        map.setStart(2, 2, Direction.EAST);
        map.putObject(MapObject.create(ObjectType.CHEST, 3, 3));
        DungeonMap copy = MapEdits.sameLayout(map, "B");
        assertEquals("B", copy.name());
        assertTrue(copy.objects().isEmpty());
        assertTrue(copy.cell(5, 5).walkable);
        assertEquals(1, map.objects().size(), "the other floor is untouched");
    }

    @Test
    void newFilesNeverReplaceOldOnes() throws IOException {
        assertEquals("b2f.txt", FloorsDialog.freeFile(folder, "b2f.txt").getFileName().toString());
        Files.writeString(folder.resolve("b2f.txt"), "");
        assertEquals("b2f-2.txt", FloorsDialog.freeFile(folder, "b2f.txt").getFileName().toString());
        Files.writeString(folder.resolve("b2f-2.txt"), "");
        assertEquals("b2f-3.txt", FloorsDialog.freeFile(folder, "b2f.txt").getFileName().toString());
    }

    @Test
    void theGuideIsDrawnOnlyWhenItsOnAndNeverChangesTheMap() {
        DungeonMap map = MapEdits.blank("A", 6, 6);
        MapDocument doc = new MapDocument(map, null, new AssetStore(folder));
        MapCanvas canvas = new MapCanvas(doc, new ObjectLooks(AssetSource.NONE));
        canvas.setSize(300, 300);
        int[] plain = pixels(canvas);
        DungeonMap bigger = MapLoader.parse("t", List.of("+---+---+---+", "| >         |", "+---+---+---+", "",
                "[stairs 1,0]", "to = maps/a.txt", "arrive = 5,5,north", "", "[stairs 2,0]", "to = maps/a.txt", "arrive = 99,99,north"))
                .resized(9, 9, 1, 1);
        canvas.setGuide(new MapCanvas.Guide(bigger, "maps/a.txt"));
        assertFalse(Arrays.equals(plain, pixels(canvas)));
        canvas.setGuide(null);
        assertTrue(Arrays.equals(plain, pixels(canvas)));
        assertFalse(doc.isDirty());
    }

    private static int[] pixels(MapCanvas canvas) {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        canvas.paint(g);
        g.dispose();
        return image.getRGB(0, 0, 300, 300, null, 0, 300);
    }
}
