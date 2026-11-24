package crawler.core;

import java.util.List;
import java.util.Objects;

/**
 * An event, chest or NPC standing in cell (x, y). Immutable: edits make a new object.
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
 */
public record MapObject(ObjectType type, int x, int y, String name, String text,
                        SpriteRef sprite, SpriteRef openSprite, boolean once, List<String> battle,
                        List<ItemStack> items) {

    public MapObject {
        Objects.requireNonNull(type, "type");
        name = name == null ? "" : name.strip();
        text = text == null ? "" : text.replace("\r", "").strip();
        openSprite = type == ObjectType.CHEST ? openSprite : null;
        once = type != ObjectType.EVENT || once;
        items = type == ObjectType.CHEST && items != null ? List.copyOf(items) : List.of();
        battle = type == ObjectType.EVENT && battle != null ? List.copyOf(battle) : List.of();
    }

    /** An object without a battle (chests, NPCs and most events). */
    public MapObject(ObjectType type, int x, int y, String name, String text,
                     SpriteRef sprite, SpriteRef openSprite, boolean once) {
        this(type, x, y, name, text, sprite, openSprite, once, List.of());
    }

    /** An object without items. */
    public MapObject(ObjectType type, int x, int y, String name, String text,
                     SpriteRef sprite, SpriteRef openSprite, boolean once, List<String> battle) {
        this(type, x, y, name, text, sprite, openSprite, once, battle, List.of());
    }

    /** A new object with the default name and nothing else filled in. */
    public static MapObject create(ObjectType type, int x, int y) {
        String name = switch (type) {
            case EVENT -> "";
            case CHEST -> "Chest";
            case NPC -> "Stranger";
        };
        return new MapObject(type, x, y, name, "", null, null, true);
    }

    public boolean solid() {
        return type.solid;
    }

    /** The name, or the type's name if it has none. */
    public String displayName() {
        return name.isEmpty() ? type.displayName : name;
    }

    public MapObject at(int newX, int newY) {
        return new MapObject(type, newX, newY, name, text, sprite, openSprite, once, battle, items);
    }

    public MapObject withSprites(SpriteRef newSprite, SpriteRef newOpenSprite) {
        return new MapObject(type, x, y, name, text, newSprite, newOpenSprite, once, battle, items);
    }
}
