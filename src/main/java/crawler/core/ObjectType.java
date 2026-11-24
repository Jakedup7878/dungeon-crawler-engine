package crawler.core;

/** The kinds of thing that can be placed in a cell. */
public enum ObjectType {
    /** Something that happens when the party steps onto the cell. Invisible unless given an image. */
    EVENT("event", "Event", false),
    /** A chest the party opens by walking into it. Blocks the cell. */
    CHEST("chest", "Chest", true),
    /** A character the party talks to. Blocks the cell only if it can't be walked through. */
    NPC("npc", "NPC", true),
    /**
     * Stairs to another floor (or anywhere else), taken by walking into them: either on one of the
     * cell's walls, leaving the cell free, or in the middle of the floor, blocking it.
     */
    STAIRS("stairs", "Stairs", true);

    /** Name used in map files. */
    public final String key;
    public final String displayName;
    /** Whether it normally blocks the cell; see {@link MapObject#solid()} for a particular one. */
    public final boolean solid;

    ObjectType(String key, String displayName, boolean solid) {
        this.key = key;
        this.displayName = displayName;
        this.solid = solid;
    }

    /** The name inside a sentence: "event", "chest", "NPC". */
    public String lowerName() {
        return this == NPC ? displayName : displayName.toLowerCase();
    }

    /** "an event", "a chest", "an NPC", "a staircase". */
    public String withArticle() {
        if (this == STAIRS) {
            return "a staircase";
        }
        return (this == CHEST ? "a " : "an ") + lowerName();
    }

    /** The type with this map-file key, or null. */
    public static ObjectType byKey(String key) {
        for (ObjectType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
