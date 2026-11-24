package crawler.core;

import java.io.IOException;
import java.util.List;

/**
 * Everything a game is made of apart from its maps: the project's settings, the ready-made
 * party, the classes, the kinds of enemy, the attack animations, the items and the enemy tiers.
 */
public record GameContent(ProjectSettings settings, List<PartyMember> party, List<CharacterClass> classes,
                          List<EnemyType> enemies, List<AttackAnimation> animations, List<Item> items, List<Tier> tiers) {
    /** No project: default settings, the built-in classes, no enemies. */
    public static final GameContent EMPTY =
            new GameContent(ProjectSettings.DEFAULT, List.of(), List.of(), List.of(), List.of());

    public GameContent {
        party = List.copyOf(party);
        classes = List.copyOf(classes);
        enemies = List.copyOf(enemies);
        animations = List.copyOf(animations);
        items = List.copyOf(items);
        tiers = List.copyOf(tiers);
    }

    /** A game with the built-in tiers. */
    public GameContent(ProjectSettings settings, List<PartyMember> party, List<CharacterClass> classes,
                       List<EnemyType> enemies, List<AttackAnimation> animations, List<Item> items) {
        this(settings, party, classes, enemies, animations, items, List.of());
    }

    /** A game without items. */
    public GameContent(ProjectSettings settings, List<PartyMember> party, List<CharacterClass> classes,
                       List<EnemyType> enemies, List<AttackAnimation> animations) {
        this(settings, party, classes, enemies, animations, List.of());
    }

    /** Reads everything from a project folder. */
    public static GameContent load(GameProject project) throws IOException {
        return load(project.settings(), ProjectFiles.folder(project.root()));
    }

    /** Reads everything from a project's files (a folder, a published game's pack, the classpath). */
    public static GameContent load(ProjectSettings settings, ProjectFiles files) throws IOException {
        GameContent content = new GameContent(settings,
                GameData.loadParty(files),
                GameData.loadClasses(files),
                GameData.loadEnemies(files),
                GameData.loadAnimations(files),
                GameData.loadItems(files),
                GameData.loadTiers(files));
        for (EnemyType enemy : content.enemies()) {
            if (enemy.tier() != null && content.tier(enemy.tier()) == null) {
                throw new IllegalArgumentException(GameData.ENEMIES_FILE + ": " + enemy.id()
                        + " is in a tier that doesn't exist: " + enemy.tier());
            }
        }
        return content;
    }

    /** The project's classes, or the built-in ones if it defines none. */
    public List<CharacterClass> availableClasses() {
        return classes.isEmpty() ? CharacterClass.builtIn() : classes;
    }

    /** The class a party member belongs to, or null if their class text matches none. */
    public CharacterClass classOf(PartyMember member) {
        for (CharacterClass c : availableClasses()) {
            if (c.matches(member.characterClass())) {
                return c;
            }
        }
        return null;
    }

    /** The ready-made party, or a starter party from the first classes if there is none. */
    public List<PartyMember> startingParty() {
        if (!party.isEmpty()) {
            return party;
        }
        List<CharacterClass> available = availableClasses();
        String[] names = {"Aldric", "Mira", "Tobin", "Wren"};
        int size = Math.min(Math.min(3, settings.partySize()), available.size());
        return java.util.stream.IntStream.range(0, Math.max(1, size))
                .mapToObj(i -> newMember(names[i], available.get(i % available.size())))
                .toList();
    }

    /** A level 1 member of a class. */
    public static PartyMember newMember(String name, CharacterClass characterClass) {
        return new PartyMember(name, characterClass.name(), 1, characterClass.base(), characterClass.portrait(),
                characterClass.equipment());
    }

    public EnemyType enemy(String id) {
        return GameData.byId(enemies, EnemyType::id, id);
    }

    /** The project's tiers, or the built-in ones if it defines none; lowest first. */
    public List<Tier> availableTiers() {
        return tiers.isEmpty() ? Tier.builtIn() : tiers;
    }

    /** A tier by id, or null. */
    public Tier tier(String id) {
        return GameData.byId(availableTiers(), Tier::id, id);
    }

    /** A tier's place in the list, from 1; 0 for none. */
    public int tierRank(String id) {
        Tier tier = tier(id);
        return tier == null ? 0 : availableTiers().indexOf(tier) + 1;
    }

    /** The enemies these ids mean: an enemy's own id, or {@code tier:elite} for all of a tier. Unknown ids are left out. */
    public List<EnemyType> enemiesFor(String id) {
        if (id.startsWith(Tier.PREFIX)) {
            String tier = id.substring(Tier.PREFIX.length()).strip();
            return enemies.stream().filter(e -> tier.equals(e.tier())).toList();
        }
        EnemyType enemy = enemy(id);
        return enemy == null ? List.of() : List.of(enemy);
    }

    public Item item(String id) {
        return GameData.byId(items, Item::id, id);
    }

    public AttackAnimation animation(String id) {
        return GameData.byId(animations, AttackAnimation::id, id);
    }
}
