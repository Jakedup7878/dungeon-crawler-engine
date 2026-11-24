package crawler.core;

import java.util.List;

/**
 * A tier enemies can be tagged with, such as Elite or Boss. Percentages change the numbers of
 * every enemy in the tier (100 leaves them alone).
 *
 * @param color   what its enemies' names are shown in, as 0xRRGGBB
 * @param weight  how likely its enemies are to be picked for random battles, next to other tiers'
 * @param canFlee false if battles with any of its enemies can't be run from
 */
public record Tier(String id, String name, int color, int weight, int hp, int attack, int defense, int speed,
                   int exp, int gold, boolean canFlee) {
    /** The weight of an enemy with no tier. */
    public static final int DEFAULT_WEIGHT = 10;
    public static final int MAX_WEIGHT = 1000;
    public static final int MAX_PERCENT = 1000;
    /** Encounters and event battles name a whole tier with this in front: {@code tier:elite}. */
    public static final String PREFIX = "tier:";

    public Tier {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        color &= 0xFFFFFF;
    }

    public static Tier create(String id, String name) {
        return new Tier(id, name, 0xDEE0E8, DEFAULT_WEIGHT, 100, 100, 100, 100, 100, 100, true);
    }

    /** The tiers a game gets when its project defines none, lowest first. */
    public static List<Tier> builtIn() {
        return List.of(
                create("common", "Common"),
                new Tier("elite", "Elite", 0x6EB4FF, 3, 150, 120, 120, 100, 200, 200, true),
                new Tier("boss", "Boss", 0xFF7850, 1, 200, 125, 125, 100, 300, 300, false));
    }

    public String displayName() {
        return name.isEmpty() ? id : name;
    }

    /** The enemy as it is in this tier. */
    public EnemyType apply(EnemyType e) {
        Stats s = e.stats();
        return new EnemyType(e.id(), e.name(), new Stats(
                Math.max(1, scaled(s.hp(), hp, Stats.MAX_POINTS)), s.mp(), scaled(s.attack(), attack, Stats.MAX_RATING),
                scaled(s.defense(), defense, Stats.MAX_RATING), scaled(s.speed(), speed, Stats.MAX_RATING)),
                scaled(e.exp(), exp, 999_999), scaled(e.gold(), gold, 999_999), e.sprite(), e.attackAnimation(), e.tier());
    }

    private static int scaled(int value, int percent, int max) {
        return (int) Math.min(max, Math.round(value * (double) percent / 100));
    }
}
