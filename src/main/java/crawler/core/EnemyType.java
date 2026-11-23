package crawler.core;

import java.util.Locale;

/**
 * A kind of enemy. Encounters will refer to it by its id, such as {@code slime}.
 *
 * @param exp    experience the party earns for beating one
 * @param gold   gold it drops
 * @param sprite image reference relative to the project folder, or null
 */
public record EnemyType(String id, String name, Stats stats, int exp, int gold, String sprite) {
    public static final Stats DEFAULT_STATS = new Stats(12, 0, 4, 2, 3);

    public EnemyType {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        sprite = sprite == null || sprite.isBlank() ? null : sprite.strip();
    }

    public static EnemyType create(String id, String name) {
        return new EnemyType(id, name, DEFAULT_STATS, 5, 3, null);
    }

    /** Ids are lower-case letters, digits, dashes and underscores, like {@code giant-rat}. */
    public static boolean isValidId(String id) {
        return id.matches("[a-z0-9][a-z0-9_-]*");
    }

    /** An id made from a name: "Giant Rat" gives "giant-rat". */
    public static String idFor(String name) {
        String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return id.isEmpty() ? "enemy" : id;
    }

    public EnemyType withSprite(String newSprite) {
        return new EnemyType(id, name, stats, exp, gold, newSprite);
    }
}
