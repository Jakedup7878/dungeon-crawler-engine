package crawler.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Game-wide options kept in project.txt.
 *
 * @param npcSprite       how NPCs without a picture of their own look, or null for the spinning top
 * @param chestSprite     how chests look, or null for the built-in chest
 * @param chestOpenSprite how open chests look, or null for the built-in open chest
 * @param battleMusic     music during battles (WAV or MIDI), or null to keep the map's music
 * @param victoryMusic    music after winning a battle, or null for none
 * @param partyCreation   whether players may create their own party when a new game starts
 * @param partySize       how many members a party can have (1 to 6)
 * @param startingGold    gold the party starts with
 */
public record ProjectSettings(SpriteRef npcSprite, SpriteRef chestSprite, SpriteRef chestOpenSprite,
                              String battleMusic, String victoryMusic,
                              boolean partyCreation, int partySize, int startingGold) {
    public static final int MAX_PARTY = 6;
    public static final int MAX_GOLD = 999_999;
    public static final ProjectSettings DEFAULT = new ProjectSettings(null, null, null, null, null, true, 4, 0);

    public ProjectSettings {
        battleMusic = battleMusic == null || battleMusic.isBlank() ? null : battleMusic.strip();
        victoryMusic = victoryMusic == null || victoryMusic.isBlank() ? null : victoryMusic.strip();
        partySize = Math.max(1, Math.min(MAX_PARTY, partySize));
        startingGold = Math.max(0, Math.min(MAX_GOLD, startingGold));
    }

    /** Every file these settings refer to. */
    public Set<String> assetReferences() {
        Set<String> references = new LinkedHashSet<>();
        for (SpriteRef sprite : new SpriteRef[]{npcSprite, chestSprite, chestOpenSprite}) {
            if (sprite != null) {
                references.add(sprite.image());
            }
        }
        if (battleMusic != null) {
            references.add(battleMusic);
        }
        if (victoryMusic != null) {
            references.add(victoryMusic);
        }
        return references;
    }
}
