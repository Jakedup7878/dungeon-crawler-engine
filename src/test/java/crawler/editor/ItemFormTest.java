package crawler.editor;

import crawler.core.Item;
import crawler.core.DungeonMap;
import crawler.core.ItemStack;
import crawler.core.MapLoader;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.render.AssetSource;
import crawler.script.Script;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's item rules. */
class ItemFormTest {
    @Test
    void everyExampleScriptIsRight() {
        for (ItemForm.Example example : ItemForm.EXAMPLES) {
            Script script = Script.parse(example.script());
            assertTrue(Script.TRIGGERS.stream().anyMatch(script::has), example.name());
            if (!example.kind().equippable) {
                assertTrue(script.has("use"), example.name());
            }
        }
    }

    @Test
    void chestsWithUnknownItemsArePointedOut() {
        DungeonMap map = MapLoader.parse("t", List.of("+---+---+", "| >     |", "+---+---+"));
        map.putObject(new MapObject(ObjectType.CHEST, 1, 0, "", "", null, null, true, List.of(), ItemStack.parse("pie")));
        assertTrue(MapCheck.of(map, AssetSource.NONE, null, Set.of("cake")).problems().stream().anyMatch(p -> p.contains("pie")));
        assertTrue(MapCheck.of(map, AssetSource.NONE, null, Set.of("pie")).problems().stream().noneMatch(p -> p.contains("pie")));
    }

    @Test
    void stairsAreCheckedAndOffered() {
        DungeonMap map = MapLoader.parse("t", List.of("+---+---+", "| >     |", "+---+---+", "",
                "[stairs 1,0]", "to = maps/b.txt", "arrive = 1,0,w"));
        java.util.Map<String, DungeonMap> floors = new java.util.HashMap<>();
        assertTrue(stairsProblem(map, floors, "doesn't exist"));
        floors.put("maps/b.txt", null);
        assertTrue(!stairsProblem(map, floors, "stairs"), "a floor that can't be read isn't judged");
        floors.put("maps/b.txt", map);
        assertTrue(!stairsProblem(map, floors, "stairs"));
        floors.put("maps/b.txt", MapLoader.parse("t", List.of("+---+---+", "| > |###|", "+---+---+")));
        assertTrue(stairsProblem(map, floors, "can't stand"));
        assertTrue(ObjectDialog.arrivals(map).equals(List.of("1,0,west")), "facing the way off the stairs");
    }

    private static boolean stairsProblem(DungeonMap map, java.util.Map<String, DungeonMap> floors, String text) {
        return MapCheck.of(map, AssetSource.NONE, null, null, floors).problems().stream().anyMatch(p -> p.contains(text));
    }
}
