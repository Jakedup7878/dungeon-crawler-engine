package crawler.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A game made with the editor: one folder that holds everything the game needs.
 *
 * <pre>
 * My Game/
 *   project.txt    the game's name and the map it starts on
 *   maps/          the floors
 *   assets/        every image the game uses
 * </pre>
 *
 * Image references in the maps (such as {@code assets/wall.png}) are relative to this
 * folder, so the folder can be copied, zipped or shipped with the game exactly as it is.
 */
public final class GameProject {
    public static final String FILE_NAME = "project.txt";
    public static final String MAPS = "maps";
    public static final String ASSETS = "assets";

    private final Path root;
    private String name;
    /** The first map, relative to the root, with forward slashes. */
    private String startMap;
    private ProjectSettings settings;
    /** The floors in order, as far as that's been decided; see {@link #floors()}. */
    private List<String> floorOrder = List.of();

    private GameProject(Path root, String name, String startMap, ProjectSettings settings) {
        this.root = root.toAbsolutePath().normalize();
        this.name = name;
        this.startMap = startMap;
        this.settings = settings;
    }

    /** Whether a folder holds a project. */
    public static boolean isProject(Path folder) {
        return folder != null && Files.isRegularFile(folder.resolve(FILE_NAME));
    }

    /**
     * Makes a new project: the folder, its maps and assets folders and project.txt.
     * The start map is {@code maps/b1f.txt}; writing that map is up to the caller.
     */
    public static GameProject create(Path root, String name) throws IOException {
        if (isProject(root)) {
            throw new IOException(root + " already has a project in it.");
        }
        Files.createDirectories(root.resolve(MAPS));
        Files.createDirectories(root.resolve(ASSETS));
        GameProject project = new GameProject(root, name.strip(), MAPS + "/b1f.txt", ProjectSettings.DEFAULT);
        project.save();
        return project;
    }

    /** Reads the project in a folder. */
    public static GameProject open(Path root) throws IOException {
        return read(ProjectFiles.folder(root), root);
    }

    /**
     * Reads project.txt from a project's files.
     *
     * @param root where the project is: its folder, or a published game's pack (for names and messages)
     */
    public static GameProject read(ProjectFiles files, Path root) throws IOException {
        List<String> lines = files.lines(FILE_NAME);
        if (lines == null) {
            throw new IOException(root + " has no " + FILE_NAME + ".");
        }
        Path folderName = root.toAbsolutePath().getFileName();
        String name = folderName == null ? "Game" : folderName.toString();
        String start = null;
        ProjectSettings d = ProjectSettings.DEFAULT;
        SpriteRef npc = d.npcSprite();
        SpriteRef chest = d.chestSprite();
        SpriteRef chestOpen = d.chestOpenSprite();
        String battleMusic = d.battleMusic();
        String victoryMusic = d.victoryMusic();
        boolean partyCreation = d.partyCreation();
        int partySize = d.partySize();
        int startingGold = d.startingGold();
        List<String> floorOrder = List.of();
        List<ItemStack> startingItems = d.startingItems();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            String where = FILE_NAME + ", line " + (i + 1) + ": ";
            if (eq <= 0) {
                throw new IOException(where + "expected 'key = value'");
            }
            String key = line.substring(0, eq).strip().toLowerCase();
            String value = line.substring(eq + 1).strip();
            switch (key) {
                case "name" -> name = value;
                case "start" -> start = value;
                case "npc_sprite" -> npc = SpriteRef.parse(value);
                case "chest_sprite" -> chest = SpriteRef.parse(value);
                case "chest_open_sprite" -> chestOpen = SpriteRef.parse(value);
                case "battle_music" -> battleMusic = value;
                case "victory_music" -> victoryMusic = value;
                case "party_creation" -> partyCreation = yesNo(value, where + "party_creation should be yes or no");
                case "party_size" -> partySize = number(value, 1, ProjectSettings.MAX_PARTY, where + "party_size");
                case "starting_gold" -> startingGold = number(value, 0, ProjectSettings.MAX_GOLD, where + "starting_gold");
                case "floors" -> floorOrder = Encounters.parseIds(value);
                case "starting_items" -> {
                    try {
                        startingItems = ItemStack.parse(value);
                    } catch (IllegalArgumentException e) {
                        throw new IOException(where + e.getMessage());
                    }
                }
                default -> throw new IOException(where + "unknown setting '" + key + "'");
            }
        }
        if (start == null) {
            throw new IOException(FILE_NAME + " doesn't say which map to start on (start = maps/b1f.txt)");
        }
        GameProject project = new GameProject(root, name, start, new ProjectSettings(npc, chest, chestOpen,
                battleMusic, victoryMusic, partyCreation, partySize, startingGold, startingItems));
        project.floorOrder = floorOrder;
        return project;
    }

    private static boolean yesNo(String value, String error) throws IOException {
        return switch (value.toLowerCase()) {
            case "yes", "true" -> true;
            case "no", "false" -> false;
            default -> throw new IOException(error);
        };
    }

    private static int number(String value, int min, int max, String what) throws IOException {
        try {
            int n = Integer.parseInt(value);
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IOException(what + " should be a whole number from " + min + " to " + max);
    }

    /** The project a file belongs to: the nearest folder above it with a project.txt, or null. */
    public static Path findRoot(Path file) {
        for (Path folder = file.toAbsolutePath().getParent(); folder != null; folder = folder.getParent()) {
            if (isProject(folder)) {
                return folder;
            }
        }
        return null;
    }

    public void save() throws IOException {
        StringBuilder text = new StringBuilder("; A Dungeon Crawler game. Paths are relative to this folder.\n")
                .append("name = ").append(name.replaceAll("[\\r\\n]+", " ")).append('\n')
                .append("start = ").append(startMap).append('\n');
        setting(text, "floors", floorOrder.isEmpty() ? null : String.join(", ", floorOrder));
        ProjectSettings s = settings;
        setting(text, "npc_sprite", s.npcSprite());
        setting(text, "chest_sprite", s.chestSprite());
        setting(text, "chest_open_sprite", s.chestOpenSprite());
        setting(text, "battle_music", s.battleMusic());
        setting(text, "victory_music", s.victoryMusic());
        setting(text, "party_creation", s.partyCreation() ? "yes" : "no");
        setting(text, "party_size", s.partySize());
        setting(text, "starting_gold", s.startingGold());
        setting(text, "starting_items", s.startingItems().isEmpty() ? null : ItemStack.text(s.startingItems()));
        Files.writeString(root.resolve(FILE_NAME), text.toString(), StandardCharsets.UTF_8);
    }

    private static void setting(StringBuilder text, String key, Object value) {
        if (value != null) {
            text.append(key).append(" = ").append(value).append('\n');
        }
    }

    public ProjectSettings settings() {
        return settings;
    }

    public void setSettings(ProjectSettings settings) {
        this.settings = java.util.Objects.requireNonNull(settings);
    }

    public Path root() {
        return root;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name.strip();
    }

    /**
     * Every map file in the maps folder, in the project's order; ones it doesn't mention come
     * last, by name (so b2f comes before b10f).
     */
    public List<String> floors() {
        List<String> found = new ArrayList<>();
        if (Files.isDirectory(mapsFolder())) {
            try (Stream<Path> files = Files.walk(mapsFolder())) {
                files.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".txt")).map(this::relative).forEach(found::add);
            } catch (IOException | UncheckedIOException e) {
                // Whatever was found will do.
            }
        }
        found.sort(Comparator.comparing(GameProject::sortKey));
        List<String> floors = new ArrayList<>(new LinkedHashSet<>(floorOrder));
        floors.retainAll(found);
        found.removeAll(floors);
        floors.addAll(found);
        return floors;
    }

    /** Numbers padded, so they sort by size. */
    private static String sortKey(String file) {
        StringBuilder key = new StringBuilder();
        Matcher m = Pattern.compile("\\d+").matcher(file.toLowerCase(Locale.ROOT));
        while (m.find()) {
            m.appendReplacement(key, "0".repeat(Math.max(0, 8 - m.group().length())) + m.group());
        }
        return m.appendTail(key).toString();
    }

    public void setFloors(List<String> order) {
        floorOrder = List.copyOf(order);
    }

    /** The floor some places after another (-1 for the one before), or null if there is none. */
    public String floorBeside(String floor, int places) {
        List<String> floors = floors();
        int at = floors.indexOf(floor);
        int wanted = at + places;
        return at < 0 || wanted < 0 || wanted >= floors.size() ? null : floors.get(wanted);
    }

    public Path mapsFolder() {
        return root.resolve(MAPS);
    }

    public Path assetsFolder() {
        return root.resolve(ASSETS);
    }

    public Path startMapFile() {
        return root.resolve(startMap);
    }

    /** The first map's path inside the project, such as {@code maps/b1f.txt}. */
    public String startMap() {
        return startMap;
    }

    public void setStartMap(Path mapFile) {
        startMap = relative(mapFile);
    }

    /** A file inside the project, written the way project files refer to it: relative, with forward slashes. */
    public String relative(Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /** Whether a file is somewhere inside this project's folder. */
    public boolean contains(Path file) {
        return file.toAbsolutePath().normalize().startsWith(root);
    }
}
