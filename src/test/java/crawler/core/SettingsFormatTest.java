package crawler.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Music, random battles, animated sprites and event battles in map files. */
class SettingsFormatTest {
    private static final List<String> GRID = List.of(
            "+---+---+---+",
            "| >         |",
            "+---+---+---+");

    static DungeonMap parse(String sections) {
        List<String> lines = new java.util.ArrayList<>(GRID);
        lines.add("");
        lines.addAll(sections.lines().toList());
        return MapLoader.parse("t.txt", lines);
    }

    @Test
    void musicAndEncountersAreReadAndWritten() {
        DungeonMap map = parse("""
                [settings]
                music = assets/cave.mid
                encounters = slime, giant-rat
                encounter_rate = 8
                group = 2-4
                """);
        assertEquals("assets/cave.mid", map.music());
        assertEquals(List.of("slime", "giant-rat"), map.encounters().enemies());
        assertEquals(8, map.encounters().rate());
        assertEquals(2, map.encounters().minGroup());
        assertEquals(4, map.encounters().maxGroup());
        assertTrue(map.encounters().active());

        DungeonMap again = MapLoader.parse("again.txt", MapWriter.write(map).lines().toList());
        assertTrue(again.contentEquals(map));
        assertTrue(map.assetReferences().contains("assets/cave.mid"));
    }

    @Test
    void aMapWithoutSettingsHasNoMusicOrBattles() {
        DungeonMap map = parse("");
        assertNull(map.music());
        assertEquals(Encounters.NONE, map.encounters());
        assertFalse(map.encounters().active());
        assertFalse(MapWriter.write(map).contains("[settings]"));
    }

    @Test
    void animatedSpritesAndEventBattlesSurviveSaving() {
        DungeonMap map = parse("""
                [npc 1,0]
                name = Spinner
                sprite = assets/spin.png, 8 frames, 12 fps

                [event 2,0]
                text = Ambush!
                battle = slime, slime
                """);
        MapObject npc = map.object(1, 0);
        assertEquals(new SpriteRef("assets/spin.png", 8, 12), npc.sprite());
        assertEquals(List.of("slime", "slime"), map.object(2, 0).battle());

        DungeonMap again = MapLoader.parse("again.txt", MapWriter.write(map).lines().toList());
        assertTrue(again.contentEquals(map));
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "encounter_rate = 150; encounter_rate should be 0 to 100",
            "group = 0-3; group should be",
            "group = 4-2; group should be",
            "group = 1-9; group should be",
            "tempo = fast; unknown setting 'tempo'",
    })
    void badSettingsAreReported(String line, String expected) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("[settings]\n" + line));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
        assertTrue(e.getMessage().contains("line 6"), e.getMessage());
    }

    @Test
    void renamingAssetsUpdatesEveryReference() {
        DungeonMap map = parse("""
                [settings]
                music = assets/a.wav

                [textures]
                wall = assets/a.png

                [npc 1,0]
                sprite = assets/a.png, 4 frames, 8 fps
                """);
        DungeonMap renamed = map.withRenamedAssets(java.util.Map.of("assets/a.png", "assets/a-2.png",
                "assets/a.wav", "assets/a-2.wav"));
        assertEquals("assets/a-2.png", renamed.texture(Surface.WALL));
        assertEquals("assets/a-2.wav", renamed.music());
        assertEquals(new SpriteRef("assets/a-2.png", 4, 8), renamed.object(1, 0).sprite());
        assertEquals("assets/a.png", map.texture(Surface.WALL), "the original is unchanged");
    }
}
