package crawler.core;

import java.util.Locale;

/** What an item is for: something to use, something to wear (one per slot), or just something to have. */
public enum ItemKind {
    CONSUMABLE("Potion / consumable", false),
    WEAPON("Weapon", true),
    SHIELD("Shield", true),
    HEAD("Helmet", true),
    BODY("Body armor", true),
    ACCESSORY("Accessory", true),
    KEY("Key item", false);

    public final String displayName;
    /** Whether a party member can wear it. */
    public final boolean equippable;

    ItemKind(String displayName, boolean equippable) {
        this.displayName = displayName;
        this.equippable = equippable;
    }

    /** How data files write it. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The kind written this way, or null. */
    public static ItemKind of(String key) {
        for (ItemKind kind : values()) {
            if (kind.key().equalsIgnoreCase(key.strip())) {
                return kind;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
