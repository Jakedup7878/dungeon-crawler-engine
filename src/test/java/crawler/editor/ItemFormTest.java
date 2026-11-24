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
}
