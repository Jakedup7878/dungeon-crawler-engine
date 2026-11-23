package crawler.core;

import java.util.Locale;

/**
 * A kind of enemy. Encounters refer to it by its id, such as {@code slime}.
 *
 * @param exp             experience the party earns for beating one
 * @param gold            gold it drops
 * @param sprite          its picture (possibly an animation strip), or null for the built-in blob
 * @param attackAnimation id of an {@link AttackAnimation}, or null for the built-in claw
 */
public record EnemyType(String id, String name, Stats stats, int exp, int gold, SpriteRef sprite, String attackAnimation) {
    public static final Stats DEFAULT_STATS = new Stats(12, 0, 4, 2, 3);

    public EnemyType {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        attackAnimation = attackAnimation == null || attackAnimation.isBlank() ? null : attackAnimation.strip();
    }

    /** An enemy with the default attack animation. */
    public EnemyType(String id, String name, Stats stats, int exp, int gold, SpriteRef sprite) {
        this(id, name, stats, exp, gold, sprite, null);
    }

    public static EnemyType create(String id, String name) {
        return new EnemyType(id, name, DEFAULT_STATS, 5, 3, null, null);
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

    public EnemyType withSprite(SpriteRef newSprite) {
        return new EnemyType(id, name, stats, exp, gold, newSprite, attackAnimation);
    }
}
