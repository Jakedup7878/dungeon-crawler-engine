package crawler.render;

import crawler.core.AttackAnimation;
import crawler.core.DungeonMap;
import crawler.core.EnemyType;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.core.ProjectSettings;
import crawler.core.SpriteRef;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * How everything that isn't a wall looks: objects on the map, enemies and attack animations.
 *
 * <p>The order is always: the thing's own picture; else the project's default (Project
 * Settings in the editor); else the built-in look: a spinning blue top for NPCs, a wooden
 * chest, nothing for events, a coloured blob for enemies, a slash or claw for attacks.
 */
public final class ObjectLooks {
    private static Sprite builtInNpc;
    private static Sprite builtInChest;
    private static Sprite builtInOpenChest;
    private static Sprite builtInSlash;
    private static Sprite builtInClaw;
    private static final Map<Integer, Sprite> BLOBS = new HashMap<>();

    private final AssetSource assets;
    private final Map<SpriteRef, Optional<Sprite>> custom = new HashMap<>();
    private SpriteRef npcDefault;
    private SpriteRef chestDefault;
    private SpriteRef chestOpenDefault;

    public ObjectLooks(AssetSource assets) {
        this.assets = assets;
    }

    /** Uses the project's own default looks for NPCs and chests (null fields mean built-in). */
    public void setDefaults(ProjectSettings settings) {
        npcDefault = settings.npcSprite();
        chestDefault = settings.chestSprite();
        chestOpenDefault = settings.chestOpenSprite();
    }

    /** How tall each kind of object stands, in cells (walls are 1 tall). */
    public static double height(ObjectType type) {
        return switch (type) {
            case NPC -> 0.8;
            case CHEST -> 0.42;
            case EVENT -> 0.7;
        };
    }

    // ------------------------------------------------------------ built-in looks

    /** The built-in NPC: a spinning blue top. */
    public static synchronized Sprite defaultNpc() {
        if (builtInNpc == null) {
            builtInNpc = DefaultSprites.spinningTop();
        }
        return builtInNpc;
    }

    /** The built-in chest, closed or open. */
    public static synchronized Sprite defaultChest(boolean open) {
        if (builtInChest == null) {
            builtInChest = DefaultSprites.chest(false);
            builtInOpenChest = DefaultSprites.chest(true);
        }
        return open ? builtInOpenChest : builtInChest;
    }

    /** The built-in attack animations: a slash when the party hits, a claw when enemies do. */
    public static synchronized Sprite defaultAttack(boolean byEnemy) {
        if (builtInSlash == null) {
            builtInSlash = DefaultSprites.slash();
            builtInClaw = DefaultSprites.claw();
        }
        return byEnemy ? builtInClaw : builtInSlash;
    }

    /** The built-in enemy: a blob, coloured by the enemy's id so different kinds look different. */
    public static synchronized Sprite defaultEnemy(String id) {
        int hue = Math.floorMod(id == null ? 0 : id.hashCode(), 360);
        return BLOBS.computeIfAbsent(hue, h -> DefaultSprites.blob(h / 360f));
    }

    // ------------------------------------------------------------ choosing a look

    /**
     * The sprite for an object, or null for an event with no picture (events are invisible
     * unless given one). A chest with its own closed picture but no open one keeps its
     * closed picture when opened.
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
            case CHEST -> chest(open);
            case NPC -> npc();
        };
    }

    /** How NPCs without their own picture look in this game. */
    public Sprite npc() {
        Sprite sprite = custom(npcDefault);
        return sprite != null ? sprite : defaultNpc();
    }

    /** How chests without their own pictures look in this game. */
    public Sprite chest(boolean open) {
        // With only a closed picture, open chests use it too.
        Sprite sprite = custom(open && chestOpenDefault != null ? chestOpenDefault : chestDefault);
        return sprite != null ? sprite : defaultChest(open);
    }

    public Sprite enemy(EnemyType type) {
        return enemy(type.id(), type.sprite());
    }

    public Sprite enemy(String id, SpriteRef picture) {
        Sprite sprite = custom(picture);
        return sprite != null ? sprite : defaultEnemy(id);
    }

    /** An attack animation's strip, or the built-in slash or claw. */
    public Sprite attack(AttackAnimation animation, boolean byEnemy) {
        Sprite sprite = animation == null ? null : custom(animation.image());
        return sprite != null ? sprite : defaultAttack(byEnemy);
    }

    /** The sprite for a picture reference, or null if there is none or it can't be read. */
    public Sprite custom(SpriteRef reference) {
        if (reference == null) {
            return null;
        }
        return custom.computeIfAbsent(reference, r -> {
            BufferedImage image = assets.image(r.image());
            return image == null ? Optional.empty() : Optional.of(Sprite.strip(image, r.frames(), r.fps()));
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

    /** Forgets pictures read so far (after the asset folder changes). */
    public void clear() {
        custom.clear();
    }
}
