package crawler.render;

import crawler.core.AttackAnimation;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Direction;
import crawler.core.EnemyType;
import crawler.core.MapLoader;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.core.ProjectSettings;
import crawler.core.SpriteRef;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteAndLooksTest {

    /** A strip of frames, each filled with its own colour. */
    static BufferedImage strip(int frames, int size) {
        BufferedImage image = new BufferedImage(frames * size, size, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setRGB(f * size + x, y, Color.HSBtoRGB(f / (float) frames, 1, 1));
                }
            }
        }
        return image;
    }

    @Test
    void aStripIsCutIntoFrames() {
        Sprite sprite = Sprite.strip(strip(4, 16), 4, 8);
        assertEquals(4, sprite.frameCount());
        assertEquals(16, sprite.width);
        assertEquals(16, sprite.height);
        assertEquals(0.5, sprite.duration(), 1e-9);
        assertNotSame(sprite.pixels(0), sprite.pixels(0.13), "each frame has its own pixels");
        assertEquals(sprite.image(0.4).getRGB(0, 0), strip(4, 16).getRGB(48, 0), "frame 4 at 0.4 seconds");
        assertSame(sprite.imageOnce(0), sprite.imageOnce(-1));
        assertSame(sprite.imageOnce(99), sprite.imageOnce(0.45), "once over, it stays on the last frame");
    }

    @Test
    void hugeStripsAreScaledDown() {
        Sprite sprite = Sprite.strip(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_ARGB), 2, 8);
        assertEquals(256, sprite.width);
        assertEquals(256, sprite.height);
    }

    @Test
    void ownPicturesBeatProjectDefaultsWhichBeatBuiltIns() {
        Map<String, BufferedImage> files = Map.of("own.png", strip(1, 8), "npc.png", strip(2, 8));
        ObjectLooks looks = new ObjectLooks(files::get);
        MapObject plain = MapObject.create(ObjectType.NPC, 0, 0);
        assertSame(ObjectLooks.defaultNpc(), looks.look(plain, false), "built-in top");

        looks.setDefaults(new ProjectSettings(new SpriteRef("npc.png", 2, 4), null, null, null, null, true, 4, 0));
        assertEquals(2, looks.look(plain, false).frameCount(), "the project's NPC look");

        MapObject own = plain.withSprites(SpriteRef.still("own.png"), null);
        assertEquals(1, looks.look(own, false).frameCount(), "the NPC's own picture");

        MapObject missing = plain.withSprites(SpriteRef.still("gone.png"), null);
        assertEquals(2, looks.look(missing, false).frameCount(), "a missing picture falls back");
    }

    @Test
    void chestsUseTheirClosedPictureWhenOpenIfThatsAllTheyHave() {
        ObjectLooks looks = new ObjectLooks(Map.of("c.png", strip(1, 8))::get);
        MapObject chest = new MapObject(ObjectType.CHEST, 0, 0, "", "", SpriteRef.still("c.png"), null, true);
        assertSame(looks.look(chest, false), looks.look(chest, true));
        MapObject plain = MapObject.create(ObjectType.CHEST, 0, 0);
        assertSame(ObjectLooks.defaultChest(true), looks.look(plain, true));
    }

    @Test
    void eventsAreInvisibleUnlessGivenAPicture() {
        ObjectLooks looks = new ObjectLooks(AssetSource.NONE);
        assertNull(looks.look(MapObject.create(ObjectType.EVENT, 0, 0), false));
    }

    @Test
    void enemiesAndAttacksHaveBuiltInLooks() {
        ObjectLooks looks = new ObjectLooks(AssetSource.NONE);
        EnemyType slime = EnemyType.create("slime", "Slime");
        assertSame(ObjectLooks.defaultEnemy("slime"), looks.enemy(slime));
        assertTrue(ObjectLooks.defaultEnemy("slime") != ObjectLooks.defaultEnemy("bat") || "slime".hashCode() % 360 == "bat".hashCode() % 360);
        assertSame(ObjectLooks.defaultAttack(false), looks.attack(null, false));
        assertSame(ObjectLooks.defaultAttack(true), looks.attack(AttackAnimation.create("x", "X"), true));
    }

    /** Pixels that change when the map's objects are drawn, looking north from (x, y). */
    static int changedPixels(DungeonMap map, double x, double y) {
        DungeonRenderer renderer = new DungeonRenderer(160, 120, Textures.defaults());
        ObjectLooks looks = new ObjectLooks(AssetSource.NONE);
        renderer.render(map, x, y, 0);
        int[] plain = renderer.frame().getRGB(0, 0, 160, 120, null, 0, 160);
        renderer.render(map, x, y, 0, looks.billboards(map, o -> false), 0);
        int[] with = renderer.frame().getRGB(0, 0, 160, 120, null, 0, 160);
        int changed = 0;
        for (int i = 0; i < plain.length; i++) {
            if (plain[i] != with[i]) {
                changed++;
            }
        }
        return changed;
    }

    @Test
    void spritesShowUnlessAWallIsInTheWay() {
        DungeonMap map = MapLoader.parse("c.txt", List.of(
                "+---+", "|   |", "+   +", "|   |", "+   +", "| ^ |", "+---+"));
        map.putObject(MapObject.create(ObjectType.NPC, 0, 0));
        assertTrue(changedPixels(map, 0.5, 2.5) > 100);
        map.setEdge(0, 1, Direction.NORTH, EdgeType.WALL);
        assertEquals(0, changedPixels(map, 0.5, 2.5));
    }

    @Test
    void stairsOnAWallAreDrawnOnThatWallOnly() {
        crawler.core.DungeonMap map = crawler.core.MapLoader.parse("t", List.of("+---+", "| ^ |", "+---+", "", "[stairs 0,0]", "wall = north"));
        ObjectLooks looks = new ObjectLooks(AssetSource.NONE);
        assertTrue(looks.billboards(map, o -> false).isEmpty(), "no billboard for stairs on a wall");
        DungeonRenderer renderer = new DungeonRenderer(64, 48, Textures.defaults());
        for (crawler.core.Direction facing : crawler.core.Direction.values()) {
            renderer.render(map, 0.5, 0.5, facing.angle());
            int plain = renderer.frame().getRGB(32, 24);
            int[] before = renderer.frame().getRGB(20, 0, 24, 48, null, 0, 24);
            renderer.render(map, 0.5, 0.5, facing.angle(), List.of(), 0, looks::wallPicture);
            int[] after = renderer.frame().getRGB(20, 0, 24, 48, null, 0, 24);
            assertEquals(facing == crawler.core.Direction.NORTH, !java.util.Arrays.equals(before, after), facing + " " + plain);
        }
    }
}
