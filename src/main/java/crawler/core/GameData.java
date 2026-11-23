package crawler.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Game-wide data kept in the project folder next to project.txt: the party the game
 * starts with ({@code party.txt}) and the kinds of enemy ({@code enemies.txt}).
 * A missing file just means there are none yet.
 */
public final class GameData {
    public static final String PARTY_FILE = "party.txt";
    public static final String ENEMIES_FILE = "enemies.txt";
    public static final int MAX_PARTY = 6;
    private static final int MAX_REWARD = 999_999;

    private GameData() {
    }

    public static List<PartyMember> loadParty(Path projectRoot) throws IOException {
        Path file = projectRoot.resolve(PARTY_FILE);
        List<PartyMember> party = new ArrayList<>();
        if (!Files.exists(file)) {
            return party;
        }
        for (SectionFile.Section s : SectionFile.read(file)) {
            s.expect("member", false);
            s.allow("name", "class", "level", "hp", "mp", "attack", "defense", "speed", "portrait");
            party.add(new PartyMember(s.text("name"), s.text("class"), s.number("level", 1, 1, 99),
                    Stats.read(s, PartyMember.DEFAULT_STATS), s.image("portrait")));
        }
        if (party.size() > MAX_PARTY) {
            throw new IllegalArgumentException(PARTY_FILE + ": a party has at most " + MAX_PARTY + " members");
        }
        return party;
    }

    public static void saveParty(Path projectRoot, List<PartyMember> party) throws IOException {
        SectionFile.Writer out = new SectionFile.Writer()
                .comment("The party the game starts with, in marching order.\n"
                        + "Image paths are relative to the project folder.");
        for (PartyMember member : party) {
            out.section("member", null)
                    .field("name", member.name())
                    .field("class", member.characterClass())
                    .field("level", member.level());
            member.stats().write(out);
            out.field("portrait", member.portrait());
        }
        out.save(projectRoot.resolve(PARTY_FILE));
    }

    public static List<EnemyType> loadEnemies(Path projectRoot) throws IOException {
        Path file = projectRoot.resolve(ENEMIES_FILE);
        List<EnemyType> enemies = new ArrayList<>();
        if (!Files.exists(file)) {
            return enemies;
        }
        Set<String> ids = new HashSet<>();
        for (SectionFile.Section s : SectionFile.read(file)) {
            s.expect("enemy", true);
            s.allow("name", "hp", "mp", "attack", "defense", "speed", "exp", "gold", "sprite");
            if (!EnemyType.isValidId(s.id())) {
                throw s.error(null, "'" + s.id() + "' isn't a valid id: use lower-case letters, digits and dashes");
            }
            if (!ids.add(s.id())) {
                throw s.error(null, "there are two enemies with the id '" + s.id() + "'");
            }
            enemies.add(new EnemyType(s.id(), s.text("name"), Stats.read(s, EnemyType.DEFAULT_STATS),
                    s.number("exp", 0, 0, MAX_REWARD), s.number("gold", 0, 0, MAX_REWARD), s.image("sprite")));
        }
        return enemies;
    }

    public static void saveEnemies(Path projectRoot, List<EnemyType> enemies) throws IOException {
        SectionFile.Writer out = new SectionFile.Writer()
                .comment("The kinds of enemy in the game. Encounters refer to them by the id in brackets.\n"
                        + "Image paths are relative to the project folder.");
        for (EnemyType enemy : enemies) {
            out.section("enemy", enemy.id()).field("name", enemy.name());
            enemy.stats().write(out);
            out.field("exp", enemy.exp()).field("gold", enemy.gold()).field("sprite", enemy.sprite());
        }
        out.save(projectRoot.resolve(ENEMIES_FILE));
    }
}
