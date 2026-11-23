package crawler.core;

/**
 * The numbers that describe a fighter, shared by party members, classes and enemies.
 * Classes also use a {@code Stats} for growth: how much each number goes up per level.
 */
public record Stats(int hp, int mp, int attack, int defense, int speed) {
    public static final int MAX_POINTS = 9999;
    public static final int MAX_RATING = 999;
    public static final Stats ZERO = new Stats(0, 0, 0, 0, 0);

    /** Reads hp, mp, attack, defense and speed from a section, using {@code fallback} for any left out. */
    static Stats read(SectionFile.Section section, Stats fallback) {
        return read(section, "", fallback, 1);
    }

    /**
     * Reads the five numbers with a key prefix ({@code growth_hp} and so on).
     *
     * @param minHp 1 for real stats, 0 for growth
     */
    static Stats read(SectionFile.Section section, String prefix, Stats fallback, int minHp) {
        return new Stats(
                section.number(prefix + "hp", fallback.hp(), minHp, MAX_POINTS),
                section.number(prefix + "mp", fallback.mp(), 0, MAX_POINTS),
                section.number(prefix + "attack", fallback.attack(), 0, MAX_RATING),
                section.number(prefix + "defense", fallback.defense(), 0, MAX_RATING),
                section.number(prefix + "speed", fallback.speed(), 0, MAX_RATING));
    }

    void write(SectionFile.Writer out) {
        write(out, "");
    }

    void write(SectionFile.Writer out, String prefix) {
        out.field(prefix + "hp", hp).field(prefix + "mp", mp).field(prefix + "attack", attack)
                .field(prefix + "defense", defense).field(prefix + "speed", speed);
    }

    /** These stats plus {@code more}, kept within the limits. */
    public Stats plus(Stats more) {
        return new Stats(
                Math.min(MAX_POINTS, hp + more.hp()),
                Math.min(MAX_POINTS, mp + more.mp()),
                Math.min(MAX_RATING, attack + more.attack()),
                Math.min(MAX_RATING, defense + more.defense()),
                Math.min(MAX_RATING, speed + more.speed()));
    }
}
