package crawler.core;

import java.util.List;

/**
 * A class party members can belong to, such as Fighter or Mage: the stats a new member
 * starts with, how much they grow each level, and how its attacks look.
 *
 * @param base            stats at level 1
 * @param growth          added at each level up
 * @param attackAnimation id of an {@link AttackAnimation}, or null for the built-in slash
 * @param portrait        picture for members of this class who have none of their own, or null
 */
public record CharacterClass(String id, String name, Stats base, Stats growth, String attackAnimation, String portrait) {
    public static final Stats DEFAULT_BASE = new Stats(26, 0, 7, 5, 5);
    public static final Stats DEFAULT_GROWTH = new Stats(6, 2, 2, 2, 1);

    public CharacterClass {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        attackAnimation = attackAnimation == null || attackAnimation.isBlank() ? null : attackAnimation.strip();
        portrait = portrait == null || portrait.isBlank() ? null : portrait.strip();
    }

    public static CharacterClass create(String id, String name) {
        return new CharacterClass(id, name, DEFAULT_BASE, DEFAULT_GROWTH, null, null);
    }

    /** The classes a game gets when its project defines none. */
    public static List<CharacterClass> builtIn() {
        return List.of(
                new CharacterClass("fighter", "Fighter", new Stats(32, 0, 9, 7, 5), new Stats(8, 0, 3, 2, 1), null, null),
                new CharacterClass("mage", "Mage", new Stats(18, 20, 4, 3, 6), new Stats(4, 5, 1, 1, 1), null, null),
                new CharacterClass("priest", "Priest", new Stats(24, 14, 6, 6, 4), new Stats(6, 4, 2, 2, 1), null, null),
                new CharacterClass("thief", "Thief", new Stats(22, 0, 7, 4, 9), new Stats(5, 0, 2, 1, 3), null, null));
    }

    /** Whether a party member's class text ("Fighter" or "fighter") means this class. */
    public boolean matches(String classText) {
        return classText != null && (id.equalsIgnoreCase(classText.strip()) || name.equalsIgnoreCase(classText.strip()));
    }

    public CharacterClass withPortrait(String newPortrait) {
        return new CharacterClass(id, name, base, growth, attackAnimation, newPortrait);
    }
}
