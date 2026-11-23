package crawler.core;

import java.io.IOException;
import java.util.List;

/**
 * Everything a game is made of apart from its maps: the project's settings, the ready-made
 * party, the classes, the kinds of enemy and the attack animations.
 */
public record GameContent(ProjectSettings settings, List<PartyMember> party, List<CharacterClass> classes,
                          List<EnemyType> enemies, List<AttackAnimation> animations) {
    /** No project: default settings, the built-in classes, no enemies. */
    public static final GameContent EMPTY =
            new GameContent(ProjectSettings.DEFAULT, List.of(), List.of(), List.of(), List.of());

    public GameContent {
        party = List.copyOf(party);
        classes = List.copyOf(classes);
        enemies = List.copyOf(enemies);
        animations = List.copyOf(animations);
    }

    /** Reads everything from a project folder. */
    public static GameContent load(GameProject project) throws IOException {
        return load(project.settings(), ProjectFiles.folder(project.root()));
    }

    /** Reads everything from a project's files (a folder, a published game's pack, the classpath). */
    public static GameContent load(ProjectSettings settings, ProjectFiles files) throws IOException {
        return new GameContent(settings,
                GameData.loadParty(files),
                GameData.loadClasses(files),
                GameData.loadEnemies(files),
                GameData.loadAnimations(files));
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
        return new PartyMember(name, characterClass.name(), 1, characterClass.base(), characterClass.portrait());
    }

    public EnemyType enemy(String id) {
        return GameData.byId(enemies, EnemyType::id, id);
    }

    public AttackAnimation animation(String id) {
        return GameData.byId(animations, AttackAnimation::id, id);
    }
}
