package crawler.core;

import java.util.Locale;

/** Who an item can be used on. */
public enum ItemTarget {
    ALLY("One party member"),
    FALLEN("One fallen party member"),
    PARTY("The whole party"),
    ENEMY("One enemy (battle only)"),
    ENEMIES("All enemies (battle only)"),
    SELF("Whoever uses it");

    public final String displayName;

    ItemTarget(String displayName) {
        this.displayName = displayName;
    }

    public boolean battleOnly() {
        return this == ENEMY || this == ENEMIES;
    }

    /** Whether the player picks a party member for it. */
    public boolean picksAlly() {
        return this == ALLY || this == FALLEN;
    }

    /** How data files write it. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The target written this way, or null. */
    public static ItemTarget of(String key) {
        for (ItemTarget target : values()) {
            if (target.key().equalsIgnoreCase(key.strip())) {
                return target;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
