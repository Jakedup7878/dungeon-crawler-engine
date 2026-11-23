package crawler.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Game-wide data kept in the project folder next to project.txt: the ready-made party
 * ({@code party.txt}), the classes ({@code classes.txt}), the kinds of enemy
 * ({@code enemies.txt}) and the attack animations ({@code animations.txt}).
 * A missing file just means there are none yet.
 */
public final class GameData {
    public static final String PARTY_FILE = "party.txt";
    public static final String CLASSES_FILE = "classes.txt";
    public static final String ENEMIES_FILE = "enemies.txt";
    public static final String ANIMATIONS_FILE = "animations.txt";
    public static final int MAX_PARTY = ProjectSettings.MAX_PARTY;
    private static final int MAX_REWARD = 999_999;
    private static final String PATHS_NOTE = "Image and sound paths are relative to the project folder.";

    private GameData() {
    }

    // ------------------------------------------------------------ party

    public static List<PartyMember> loadParty(Path projectRoot) throws IOException {
        return loadParty(ProjectFiles.folder(projectRoot));
    }

    public static List<PartyMember> loadParty(ProjectFiles files) throws IOException {
        List<PartyMember> party = new ArrayList<>();
        for (SectionFile.Section s : sections(files, PARTY_FILE)) {
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
                .comment("The ready-made party the game can start with, in marching order.\n" + PATHS_NOTE);
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

    // ------------------------------------------------------------ classes

    public static List<CharacterClass> loadClasses(Path projectRoot) throws IOException {
        return loadClasses(ProjectFiles.folder(projectRoot));
    }

    public static List<CharacterClass> loadClasses(ProjectFiles files) throws IOException {
        List<CharacterClass> classes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (SectionFile.Section s : sections(files, CLASSES_FILE)) {
            s.expect("class", true);
            s.allow("name", "hp", "mp", "attack", "defense", "speed",
                    "growth_hp", "growth_mp", "growth_attack", "growth_defense", "growth_speed",
                    "animation", "portrait");
            checkId(s, ids, "classes");
            classes.add(new CharacterClass(s.id(), s.text("name"),
                    Stats.read(s, "", CharacterClass.DEFAULT_BASE, 1),
                    Stats.read(s, "growth_", CharacterClass.DEFAULT_GROWTH, 0),
                    s.text("animation"), s.image("portrait")));
        }
        return classes;
    }

    public static void saveClasses(Path projectRoot, List<CharacterClass> classes) throws IOException {
        SectionFile.Writer out = new SectionFile.Writer()
                .comment("Classes party members can belong to. Stats are for level 1; growth_ is added\n"
                        + "at every level up. animation is the id of an attack animation.\n" + PATHS_NOTE);
        for (CharacterClass c : classes) {
            out.section("class", c.id()).field("name", c.name());
            c.base().write(out);
            c.growth().write(out, "growth_");
            out.field("animation", c.attackAnimation()).field("portrait", c.portrait());
        }
        out.save(projectRoot.resolve(CLASSES_FILE));
    }

    // ------------------------------------------------------------ enemies

    public static List<EnemyType> loadEnemies(Path projectRoot) throws IOException {
        return loadEnemies(ProjectFiles.folder(projectRoot));
    }

    public static List<EnemyType> loadEnemies(ProjectFiles files) throws IOException {
        List<EnemyType> enemies = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (SectionFile.Section s : sections(files, ENEMIES_FILE)) {
            s.expect("enemy", true);
            s.allow("name", "hp", "mp", "attack", "defense", "speed", "exp", "gold", "sprite", "animation");
            checkId(s, ids, "enemies");
            enemies.add(new EnemyType(s.id(), s.text("name"), Stats.read(s, EnemyType.DEFAULT_STATS),
                    s.number("exp", 0, 0, MAX_REWARD), s.number("gold", 0, 0, MAX_REWARD),
                    SpriteRef.parse(s.image("sprite")), s.text("animation")));
        }
        return enemies;
    }

    public static void saveEnemies(Path projectRoot, List<EnemyType> enemies) throws IOException {
        SectionFile.Writer out = new SectionFile.Writer()
                .comment("The kinds of enemy in the game. Maps refer to them by the id in brackets.\n" + PATHS_NOTE);
        for (EnemyType enemy : enemies) {
            out.section("enemy", enemy.id()).field("name", enemy.name());
            enemy.stats().write(out);
            out.field("exp", enemy.exp()).field("gold", enemy.gold())
                    .field("sprite", enemy.sprite()).field("animation", enemy.attackAnimation());
        }
        out.save(projectRoot.resolve(ENEMIES_FILE));
    }

    // ------------------------------------------------------------ attack animations

    public static List<AttackAnimation> loadAnimations(Path projectRoot) throws IOException {
        return loadAnimations(ProjectFiles.folder(projectRoot));
    }

    public static List<AttackAnimation> loadAnimations(ProjectFiles files) throws IOException {
        List<AttackAnimation> animations = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (SectionFile.Section s : sections(files, ANIMATIONS_FILE)) {
            s.expect("animation", true);
            s.allow("name", "image", "sound", "size");
            checkId(s, ids, "animations");
            animations.add(new AttackAnimation(s.id(), s.text("name"), SpriteRef.parse(s.image("image")),
                    s.image("sound"), s.number("size", 100, AttackAnimation.MIN_SIZE, AttackAnimation.MAX_SIZE)));
        }
        return animations;
    }

    public static void saveAnimations(Path projectRoot, List<AttackAnimation> animations) throws IOException {
        SectionFile.Writer out = new SectionFile.Writer()
                .comment("Attack animations. image is a strip of frames side by side, for example\n"
                        + "  image = assets/slash.png, 6 frames, 18 fps\n"
                        + "size is in percent of the target's height. " + PATHS_NOTE);
        for (AttackAnimation a : animations) {
            out.section("animation", a.id()).field("name", a.name())
                    .field("image", a.image()).field("sound", a.sound()).field("size", a.size());
        }
        out.save(projectRoot.resolve(ANIMATIONS_FILE));
    }

    // ------------------------------------------------------------ helpers

    private static List<SectionFile.Section> sections(ProjectFiles files, String fileName) throws IOException {
        List<String> lines = files.lines(fileName);
        return lines == null ? List.of() : SectionFile.parse(fileName, lines);
    }

    private static void checkId(SectionFile.Section s, Set<String> ids, String what) {
        if (!EnemyType.isValidId(s.id())) {
            throw s.error(null, "'" + s.id() + "' isn't a valid id: use lower-case letters, digits and dashes");
        }
        if (!ids.add(s.id())) {
            throw s.error(null, "there are two " + what + " with the id '" + s.id() + "'");
        }
    }

    /** The first item with an id, or null. */
    public static <T> T byId(List<T> items, Function<T, String> id, String wanted) {
        if (wanted == null) {
            return null;
        }
        for (T item : items) {
            if (id.apply(item).equals(wanted)) {
                return item;
            }
        }
        return null;
    }
}
