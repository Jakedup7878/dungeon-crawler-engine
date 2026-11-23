package crawler.core;

import java.util.Objects;

/**
 * An event, chest or NPC standing in cell (x, y). Immutable: edits make a new object.
 *
 * <p>Images are asset references: paths relative to the map file, such as
 * {@code assets/old-man.png}. Null means the built-in look (for events: invisible).
 *
 * @param name       shown in the game when the party meets it; may be empty
 * @param text       the event's message, the chest's contents or the NPC's dialogue; may be empty
 * @param sprite     the object's image, or null for the default
 * @param openSprite chests only: the image once opened, or null for the default
 * @param once       events only: whether it happens only the first time the party steps there
 */
public record MapObject(ObjectType type, int x, int y, String name, String text,
                        String sprite, String openSprite, boolean once) {

    public MapObject {
        Objects.requireNonNull(type, "type");
        name = name == null ? "" : name.strip();
        text = text == null ? "" : text.replace("\r", "").strip();
        sprite = blankToNull(sprite);
        openSprite = type == ObjectType.CHEST ? blankToNull(openSprite) : null;
        once = type != ObjectType.EVENT || once;
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
        return new MapObject(type, newX, newY, name, text, sprite, openSprite, once);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
