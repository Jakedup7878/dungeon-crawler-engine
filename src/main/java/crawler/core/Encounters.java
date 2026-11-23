package crawler.core;

import java.util.Arrays;
import java.util.List;

/**
 * Random battles on a map: which enemies can turn up, how often, and how many at once.
 *
 * @param enemies  ids of the enemy types that can appear (see {@link EnemyType})
 * @param rate     chance of a battle on each step, in percent (0 = never)
 * @param minGroup fewest enemies in one battle
 * @param maxGroup most enemies in one battle
 */
public record Encounters(List<String> enemies, int rate, int minGroup, int maxGroup) {
    public static final int MAX_GROUP = 6;
    public static final Encounters NONE = new Encounters(List.of(), 0, 1, 3);

    public Encounters {
        enemies = List.copyOf(enemies);
        rate = Math.max(0, Math.min(100, rate));
        minGroup = Math.max(1, Math.min(MAX_GROUP, minGroup));
        maxGroup = Math.max(minGroup, Math.min(MAX_GROUP, maxGroup));
    }

    /** Whether battles can happen at all. */
    public boolean active() {
        return rate > 0 && !enemies.isEmpty();
    }

    /** "slime, giant-rat" to a list of ids. */
    public static List<String> parseIds(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** "1-3" or "2" for the group size. */
    public String groupText() {
        return minGroup == maxGroup ? Integer.toString(minGroup) : minGroup + "-" + maxGroup;
    }
}
