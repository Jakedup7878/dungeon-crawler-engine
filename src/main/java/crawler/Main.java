package crawler;

import crawler.core.DungeonMap;
import crawler.core.GameContent;
import crawler.core.GameProject;
import crawler.core.MapLoader;
import crawler.render.AssetSource;
import crawler.ui.GameWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point.
 *
 * <pre>
 *   java crawler.Main                plays the published game (see below), or the built-in sample game
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
    /** The sample game that comes with the engine, on the classpath. */
    private static final String SAMPLE = "/sample/";
    private static final String SAMPLE_MAP = SAMPLE + "maps/b1f.txt";
    /** Where a published game keeps its project, next to the program. */
    public static final String BUNDLED_FOLDER = "game";

    private Main() {
    }

    /** What to play: a map, the rest of the game, where its files come from, and the window title. */
    public record Loaded(DungeonMap map, GameContent content, AssetSource assets, String title) {
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
        SwingUtilities.invokeLater(() -> GameWindow.open(game.map(), game.content(), game.assets(), game.title(), true, true));
    }

    /**
     * Loads a project folder, a single map file, or (for null) the built-in sample game.
     * Files are looked up relative to the project folder, or for a map on its own, the map's folder.
     */
    public static Loaded load(Path target) throws IOException {
        if (target == null) {
            Path sample = sampleFolder();
            if (sample != null) {
                return load(sample);
            }
            // Packed inside a jar: just the sample map.
            DungeonMap map = MapLoader.loadResource(SAMPLE_MAP);
            return new Loaded(map, GameContent.EMPTY, AssetSource.classpath(SAMPLE), "Dungeon Crawler - " + map.name());
        }
        target = target.toAbsolutePath();
        if (Files.isDirectory(target)) {
            if (!GameProject.isProject(target)) {
                throw new IOException(target + " isn't a game project: it has no " + GameProject.FILE_NAME + ".");
            }
            GameProject project = GameProject.open(target);
            DungeonMap map = MapLoader.load(project.startMapFile());
            return new Loaded(map, GameContent.load(project), AssetSource.directory(project.root()),
                    project.name() + " - " + map.name());
        }
        DungeonMap map = MapLoader.load(target);
        Path root = GameProject.findRoot(target);
        GameContent content = root == null ? GameContent.EMPTY : GameContent.load(GameProject.open(root));
        return new Loaded(map, content, AssetSource.directory(root != null ? root : target.getParent()),
                "Dungeon Crawler - " + map.name());
    }

    /** The sample game's folder, when the classpath is a folder of files (as when run from the build). */
    private static Path sampleFolder() {
        URL url = Main.class.getResource(SAMPLE + GameProject.FILE_NAME);
        if (url == null || !"file".equals(url.getProtocol())) {
            return null;
        }
        try {
            return Path.of(url.toURI()).getParent();
        } catch (URISyntaxException e) {
            return null;
        }
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
