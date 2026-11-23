package crawler;

import crawler.core.DungeonMap;
import crawler.core.GameProject;
import crawler.core.MapLoader;
import crawler.render.AssetSource;
import crawler.ui.GameWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point.
 *
 * <pre>
 *   java crawler.Main                plays the published game (see below), or the built-in sample floor
 *   java crawler.Main "My Game"      plays a project folder, starting on its starting map
 *   java crawler.Main my-map.txt     plays one map file
 * </pre>
 *
 * A published game keeps its project in a folder called {@code game} next to the program,
 * and runs it when started with no arguments.
 *
 * The map editor is a separate program: {@link crawler.editor.MapEditor}.
 */
public final class Main {
    private static final String DEFAULT_MAP = "/maps/b1f.txt";
    /** Where a published game keeps its project, next to the program. */
    public static final String BUNDLED_FOLDER = "game";

    private Main() {
    }

    /** What to play: a map, where its images come from, and the window title. */
    public record Loaded(DungeonMap map, AssetSource assets, String title) {
    }

    public static void main(String[] args) {
        Loaded game;
        try {
            game = load(args.length > 0 ? Path.of(args[0]) : bundledGame(programFolder()));
        } catch (IOException | IllegalArgumentException e) {
            // A published game has no console, so say it in a window too.
            System.err.println("Could not start the game: " + e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "Could not start the game", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        SwingUtilities.invokeLater(() -> GameWindow.open(game.map(), game.assets(), game.title(), true));
    }

    /**
     * Loads a project folder, a single map file, or (for null) the built-in sample.
     * Images are looked up relative to the project folder, or for a map on its own, the map's folder.
     */
    public static Loaded load(Path target) throws IOException {
        if (target == null) {
            DungeonMap map = MapLoader.loadResource(DEFAULT_MAP);
            AssetSource assets = AssetSource.classpath(DEFAULT_MAP.substring(0, DEFAULT_MAP.lastIndexOf('/') + 1));
            return new Loaded(map, assets, "Dungeon Crawler - " + map.name());
        }
        target = target.toAbsolutePath();
        if (Files.isDirectory(target)) {
            if (!GameProject.isProject(target)) {
                throw new IOException(target + " isn't a game project: it has no " + GameProject.FILE_NAME + ".");
            }
            GameProject project = GameProject.open(target);
            DungeonMap map = MapLoader.load(project.startMapFile());
            return new Loaded(map, AssetSource.directory(project.root()), project.name() + " - " + map.name());
        }
        DungeonMap map = MapLoader.load(target);
        Path root = GameProject.findRoot(target);
        return new Loaded(map, AssetSource.directory(root != null ? root : target.getParent()),
                "Dungeon Crawler - " + map.name());
    }

    /** The project shipped with a published game, or null if there isn't one. */
    public static Path bundledGame(Path programFolder) {
        if (programFolder == null) {
            return null;
        }
        Path folder = programFolder.resolve(BUNDLED_FOLDER);
        return GameProject.isProject(folder) ? folder : null;
    }

    /** The folder holding the game's jar (or its classes folder, when run from the build). */
    private static Path programFolder() {
        try {
            Path code = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(code) ? code : code.getParent();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return null;
        }
    }
}
