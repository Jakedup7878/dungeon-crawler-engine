package crawler.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading and writing map files. */
class MapFormatTest {

    static DungeonMap sample() throws IOException {
        return MapLoader.loadResource("/sample/maps/b1f.txt");
    }

    @Test
    void sampleFloorLoads() throws IOException {
        DungeonMap map = sample();
        assertEquals("B1F - The Old Cellars", map.name());
        assertEquals(12, map.width());
        assertEquals(10, map.height());
        assertEquals(1, map.startX());
        assertEquals(8, map.startY());
        assertEquals(Direction.NORTH, map.startFacing());
        assertEquals(EdgeType.DOOR, map.edge(1, 2, Direction.SOUTH));
        assertEquals(EdgeType.DOOR, map.edge(1, 3, Direction.NORTH), "both cells see the same door");
        assertEquals(CellType.ROCK, map.cell(1, 1));
        assertEquals(5, map.objects().size());
    }

    @Test
    void writingThenReadingGivesTheSameMap() throws IOException {
        DungeonMap map = sample();
        String text = MapWriter.write(map);
        DungeonMap again = MapLoader.parse("again.txt", text.lines().toList());
        assertTrue(again.contentEquals(map));
        assertEquals(text, MapWriter.write(again), "writing is stable");
    }

    @Test
    void textWithLineBreaksAndBackslashesSurvives() throws IOException {
        DungeonMap map = sample();
        map.putObject(new MapObject(ObjectType.NPC, 4, 3, "Sage", "Line one\nC:\\path", null, null, true));
        DungeonMap again = MapLoader.parse("t.txt", MapWriter.write(map).lines().toList());
        assertEquals("Line one\nC:\\path", again.object(4, 3).text());
    }

    @Test
    void openEdgesOnTheBorderAreWalledOff() {
        DungeonMap map = MapLoader.parse("tiny.txt", List.of(
                "+---+---+",
                "| ^      ",
                "+---+---+"));
        assertEquals(EdgeType.WALL, map.edge(1, 0, Direction.EAST));
    }

    static Stream<Arguments> mistakes() {
        return Stream.of(
                Arguments.of("+---+---+\n| ^ | ^ |\n+---+---+", "more than one start marker"),
                Arguments.of("hello", "expected 'name:'"),
                Arguments.of("+---+--+\n|   |  |\n+---+--+", "top border"),
                Arguments.of("+---+---+\n| ^     |", "alternate border and cell rows"),
                Arguments.of("+---+---+\n| ^     |\n+---+---+\n\n[chests 1,0]", "unknown section"),
                Arguments.of("+---+---+\n| ^     |\n+---+---+\n\n[npc 0,0]", "start cell"));
    }

    @ParameterizedTest
    @MethodSource("mistakes")
    void mistakesAreReportedClearly(String text, String expected) {
        List<String> lines = text.lines().toList();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MapLoader.parse("bad.txt", lines));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    @Test
    void startInsideRockIsLeftOutAndTheLoaderPicksAFloorCell() throws IOException {
        DungeonMap map = sample();
        map.setStart(1, 1, Direction.EAST); // a rock pillar
        DungeonMap again = MapLoader.parse("x.txt", MapWriter.write(map).lines().toList());
        assertTrue(again.cell(again.startX(), again.startY()).walkable);
    }

    @Test
    void copiesAreIndependent() throws IOException {
        DungeonMap map = sample();
        DungeonMap copy = map.copy();
        assertTrue(copy.contentEquals(map));
        copy.removeObject(10, 1);
        assertNotNull(map.object(10, 1));
        assertNull(copy.object(10, 1));
        assertFalse(copy.contentEquals(map));
    }
}
