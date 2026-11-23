package crawler.core;

/**
 * One of the adventurers in the party.
 *
 * @param characterClass Fighter, Mage and so on; any text (the game doesn't use it yet)
 * @param portrait       image reference relative to the project folder, or null
 */
public record PartyMember(String name, String characterClass, int level, Stats stats, String portrait) {
    public static final Stats DEFAULT_STATS = new Stats(30, 0, 8, 6, 5);

    public PartyMember {
        name = name == null ? "" : name.strip();
        characterClass = characterClass == null ? "" : characterClass.strip();
        portrait = portrait == null || portrait.isBlank() ? null : portrait.strip();
    }

    /** A level 1 fighter with the default stats. */
    public static PartyMember create(String name) {
        return new PartyMember(name, "Fighter", 1, DEFAULT_STATS, null);
    }

    public PartyMember withPortrait(String newPortrait) {
        return new PartyMember(name, characterClass, level, stats, newPortrait);
    }
}
