package crawler.core;

import java.util.List;

/**
 * One of the adventurers in the party.
 *
 * @param characterClass Fighter, Mage and so on; any text (the game doesn't use it yet)
 * @param portrait       image reference relative to the project folder, or null
 * @param equipment      ids of the items they start out wearing
 */
public record PartyMember(String name, String characterClass, int level, Stats stats, String portrait,
                          List<String> equipment) {
    public static final Stats DEFAULT_STATS = new Stats(30, 0, 8, 6, 5);

    public PartyMember {
        name = name == null ? "" : name.strip();
        characterClass = characterClass == null ? "" : characterClass.strip();
        portrait = portrait == null || portrait.isBlank() ? null : portrait.strip();
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
    }

    /** A member wearing nothing special. */
    public PartyMember(String name, String characterClass, int level, Stats stats, String portrait) {
        this(name, characterClass, level, stats, portrait, List.of());
    }

    /** A level 1 fighter with the default stats. */
    public static PartyMember create(String name) {
        return new PartyMember(name, "Fighter", 1, DEFAULT_STATS, null);
    }

    public PartyMember withPortrait(String newPortrait) {
        return new PartyMember(name, characterClass, level, stats, newPortrait, equipment);
    }
}
