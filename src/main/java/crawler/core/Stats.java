package crawler.core;

/** The numbers that describe a fighter, shared by party members and enemies. */
public record Stats(int hp, int mp, int attack, int defense, int speed) {
    public static final int MAX_POINTS = 9999;
    public static final int MAX_RATING = 999;

    /** Reads hp, mp, attack, defense and speed from a section, using {@code fallback} for any left out. */
    static Stats read(SectionFile.Section section, Stats fallback) {
        return new Stats(
                section.number("hp", fallback.hp(), 1, MAX_POINTS),
                section.number("mp", fallback.mp(), 0, MAX_POINTS),
                section.number("attack", fallback.attack(), 0, MAX_RATING),
                section.number("defense", fallback.defense(), 0, MAX_RATING),
                section.number("speed", fallback.speed(), 0, MAX_RATING));
    }

    void write(SectionFile.Writer out) {
        out.field("hp", hp).field("mp", mp).field("attack", attack).field("defense", defense).field("speed", speed);
    }
}
