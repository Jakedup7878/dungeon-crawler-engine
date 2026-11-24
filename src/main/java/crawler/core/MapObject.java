package crawler.core;

import java.util.List;
import java.util.Objects;

/**
 * An event, chest, NPC or staircase standing in cell (x, y). Immutable: edits make a new object.
 *
 * <p>Pictures are {@link SpriteRef}s (an image, optionally an animation strip) relative to
 * the project folder. Null means the built-in look (for events: invisible).
 *
 * @param name       shown in the game when the party meets it; may be empty
 * @param text       the event's message, the chest's contents or the NPC's dialogue; may be empty
 * @param sprite     the object's picture, or null for the default
 * @param openSprite chests only: the picture once opened, or null for the default
 * @param once       events only: whether it happens only the first time the party steps there
 * @param battle     events only: ids of enemies the party has to fight when it steps there (a fixed
 *                   battle, such as a boss); empty for none
 * @param items      chests only: what the party gets on opening it
 * @param destination stairs only: where they lead
 * @param wall        stairs only: the side of the cell whose wall they're on, or null for the middle of the floor
 * @param walkThrough NPCs only: whether the party can walk through it (unless told otherwise, it can)
 */
public record MapObject(ObjectType type, int x, int y, String name, String text,
                        SpriteRef sprite, SpriteRef openSprite, boolean once, List<String> battle,
                        List<ItemStack> items, Destination destination,
                        Direction wall, boolean walkThrough) {

    public MapObject {
        Objects.requireNonNull(type, "type");
        name = name == null ? "" : name.strip();
        text = text == null ? "" : text.replace("\r", "").strip();
        openSprite = type == ObjectType.CHEST ? openSprite : null;
        once = type != ObjectType.EVENT || once;
        destination = type == ObjectType.STAIRS && destination != null ? destination : Destination.NONE;
        wall = type == ObjectType.STAIRS ? wall : null;
        walkThrough = type == ObjectType.NPC && walkThrough;
        items = type == ObjectType.CHEST && items != null ? List.copyOf(items) : List.of();
        battle = type == ObjectType.EVENT && battle != null ? List.copyOf(battle) : List.of();
    }

    /** An object without a battle (chests, NPCs and most events). */
    public MapObject(ObjectType type, int x, int y, String name, String text,
                     SpriteRef sprite, SpriteRef openSprite, boolean once) {
        this(type, x, y, name, text, sprite, openSprite, once, List.of());
    }

    /** Anything but stairs. */
    public MapObject(ObjectType type, int x, int y, String name, String text, SpriteRef sprite, SpriteRef openSprite,
                     boolean once, List<String> battle, List<ItemStack> items) {
        this(type, x, y, name, text, sprite, openSprite, once, battle, items, Destination.NONE, null, true);
    }

    /** An object without items. */
    public MapObject(ObjectType type, int x, int y, String name, String text,
                     SpriteRef sprite, SpriteRef openSprite, boolean once, List<String> battle) {
        this(type, x, y, name, text, sprite, openSprite, once, battle, List.of());
    }

    /** A new object with the default name and nothing else filled in. */
    public static MapObject create(ObjectType type, int x, int y) {
        String name = switch (type) {
            case EVENT, STAIRS -> "";
            case CHEST -> "Chest";
            case NPC -> "Stranger";
        };
        return new MapObject(type, x, y, name, "", null, null, true);
    }

    /** Whether it keeps the party out of its cell. */
    public boolean solid() {
        return type.solid && wall == null && !walkThrough;
    }

    /** The name, or the type's name if it has none. */
    public String displayName() {
        return name.isEmpty() ? type.displayName : name;
    }

    public MapObject at(int newX, int newY) {
        return new MapObject(type, newX, newY, name, text, sprite, openSprite, once, battle, items, destination, wall, walkThrough);
    }

    /** The same stairs on a wall, or (null) in the middle of the floor. */
    public MapObject onWall(Direction side) {
        return new MapObject(type, x, y, name, text, sprite, openSprite, once, battle, items, destination, side, walkThrough);
    }

    public MapObject withSprites(SpriteRef newSprite, SpriteRef newOpenSprite) {
        return new MapObject(type, x, y, name, text, newSprite, newOpenSprite, once, battle, items, destination, wall, walkThrough);
    }
}
