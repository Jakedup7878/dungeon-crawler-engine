package crawler.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    private GameProject(Path root, String name, String startMap) {
        this.root = root.toAbsolutePath().normalize();
        this.name = name;
        this.startMap = startMap;
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
        GameProject project = new GameProject(root, name.strip(), MAPS + "/b1f.txt");
        project.save();
        return project;
    }

    /** Reads the project in a folder. */
    public static GameProject open(Path root) throws IOException {
        List<String> lines = Files.readAllLines(root.resolve(FILE_NAME), StandardCharsets.UTF_8);
        String name = root.toAbsolutePath().getFileName().toString();
        String start = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IOException(FILE_NAME + ", line " + (i + 1) + ": expected 'key = value'");
            }
            String key = line.substring(0, eq).strip().toLowerCase();
            String value = line.substring(eq + 1).strip();
            switch (key) {
                case "name" -> name = value;
                case "start" -> start = value;
                default -> throw new IOException(FILE_NAME + ", line " + (i + 1) + ": unknown setting '" + key + "'");
            }
        }
        if (start == null) {
            throw new IOException(FILE_NAME + " doesn't say which map to start on (start = maps/b1f.txt)");
        }
        return new GameProject(root, name, start);
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
        String text = "; A Dungeon Crawler game. Paths are relative to this folder.\n"
                + "name = " + name.replaceAll("[\\r\\n]+", " ") + "\n"
                + "start = " + startMap + "\n";
        Files.writeString(root.resolve(FILE_NAME), text, StandardCharsets.UTF_8);
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

    public Path mapsFolder() {
        return root.resolve(MAPS);
    }

    public Path assetsFolder() {
        return root.resolve(ASSETS);
    }

    public Path startMapFile() {
        return root.resolve(startMap);
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
