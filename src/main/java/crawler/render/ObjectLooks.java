package crawler.render;

import crawler.core.DungeonMap;
import crawler.core.MapObject;
import crawler.core.ObjectType;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * How each object looks in the dungeon: the map's own image where it has one, otherwise
 * the built-in look (a spinning blue top for NPCs, a wooden chest, and nothing for events).
 */
public final class ObjectLooks {
    private static Sprite defaultNpc;
    private static Sprite defaultChest;
    private static Sprite defaultOpenChest;

    private final AssetSource assets;
    private final Map<String, Optional<Sprite>> custom = new HashMap<>();

    public ObjectLooks(AssetSource assets) {
        this.assets = assets;
    }

    /** How tall each kind of object stands, in cells (walls are 1 tall). */
    public static double height(ObjectType type) {
        return switch (type) {
            case NPC -> 0.8;
            case CHEST -> 0.42;
            case EVENT -> 0.7;
        };
    }

    public static synchronized Sprite defaultNpc() {
        if (defaultNpc == null) {
            defaultNpc = DefaultSprites.spinningTop();
        }
        return defaultNpc;
    }

    public static synchronized Sprite defaultChest(boolean open) {
        if (defaultChest == null) {
            defaultChest = DefaultSprites.chest(false);
            defaultOpenChest = DefaultSprites.chest(true);
        }
        return open ? defaultOpenChest : defaultChest;
    }

    /**
     * The sprite for an object, or null for an event with no image (events are invisible
     * unless the map gives them one). A chest with its own closed image but no opened image
     * keeps its closed image when opened.
     */
    public Sprite look(MapObject object, boolean opened) {
        boolean open = opened && object.type() == ObjectType.CHEST;
        Sprite sprite = custom(open ? object.openSprite() : object.sprite());
        if (sprite == null && open) {
            sprite = custom(object.sprite());
        }
        if (sprite != null) {
            return sprite;
        }
        return switch (object.type()) {
            case EVENT -> null;
            case CHEST -> defaultChest(open);
            case NPC -> defaultNpc();
        };
    }

    /** The sprite for an image reference, or null if there is none or it can't be read. */
    public Sprite custom(String reference) {
        if (reference == null) {
            return null;
        }
        return custom.computeIfAbsent(reference, r -> {
            BufferedImage image = assets.image(r);
            return image == null ? Optional.empty() : Optional.of(Sprite.of(image));
        }).orElse(null);
    }

    /** Billboards for every visible object on the map. */
    public List<Billboard> billboards(DungeonMap map, Predicate<MapObject> opened) {
        List<Billboard> billboards = new ArrayList<>();
        for (MapObject object : map.objects()) {
            Sprite sprite = look(object, opened.test(object));
            if (sprite != null) {
                billboards.add(new Billboard(object.x() + 0.5, object.y() + 0.5, sprite, height(object.type())));
            }
        }
        return billboards;
    }

    /** Forgets images read so far (after the asset folder changes). */
    public void clear() {
        custom.clear();
    }
}
