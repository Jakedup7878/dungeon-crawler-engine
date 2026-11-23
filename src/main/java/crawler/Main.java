package crawler;

import crawler.core.DungeonMap;
import crawler.core.GameContent;
import crawler.core.GamePack;
import crawler.core.GameProject;
import crawler.core.MapLoader;
import crawler.core.ProjectFiles;
import crawler.render.AssetSource;
import crawler.ui.GameWindow;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Entry point.
 *
 * <pre>
 *   java crawler.Main                plays the published game (see below), or the built-in sample game
 *   java crawler.Main "My Game"      plays a project folder, starting on its starting map
 *   java crawler.Main game.pak       plays a published game's encrypted pack
 *   java crawler.Main my-map.txt     plays one map file
 * </pre>
 *
 * A published game keeps its project next to the program, encrypted in {@code game.pak}
 * (see {@link GamePack}), and plays it when started with no arguments.
 *
 * The map editor is a separate program: {@link crawler.editor.MapEditor}.
 */
public final class Main {
    /** The sample game that comes with the engine, on the classpath. */
    private static final String SAMPLE = "/sample/";

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
     * Loads a project folder, a published game's pack, a single map file, or (for null) the
     * built-in sample game. Files are looked up in the project, or for a map on its own, in
     * the map's folder.
     */
    public static Loaded load(Path target) throws IOException {
        if (target == null) {
            return loadProject(ProjectFiles.classpath(SAMPLE), Path.of("Sample Quest"));
        }
        target = target.toAbsolutePath();
        if (Files.isDirectory(target)) {
            if (!GameProject.isProject(target)) {
                throw new IOException(target + " isn't a game project: it has no " + GameProject.FILE_NAME + ".");
            }
            return loadProject(ProjectFiles.folder(target), target);
        }
        if (target.getFileName().toString().endsWith(".pak")) {
            return loadProject(GamePack.read(target), target);
        }
        DungeonMap map = MapLoader.load(target);
        Path root = GameProject.findRoot(target);
        GameContent content = root == null ? GameContent.EMPTY : GameContent.load(GameProject.open(root));
        return new Loaded(map, content, AssetSource.directory(root != null ? root : target.getParent()),
                "Dungeon Crawler - " + map.name());
    }

    /**
     * Loads a whole project from its files, wherever they are: everything the game needs
     * is checked here, so a broken project fails now rather than halfway through a game.
     *
     * @param where the project's folder or pack, for messages
     */
    public static Loaded loadProject(ProjectFiles files, Path where) throws IOException {
        GameProject project = GameProject.read(files, where);
        List<String> lines = files.lines(project.startMap());
        if (lines == null) {
            throw new IOException("The game's starting map, " + project.startMap() + ", is missing.");
        }
        String start = project.startMap();
        DungeonMap map = MapLoader.parse(start.substring(start.lastIndexOf('/') + 1), lines);
        GameContent content = GameContent.load(project.settings(), files);
        return new Loaded(map, content, AssetSource.of(files), project.name() + " - " + map.name());
    }

    /** The game shipped with a published program (its pack, or a plain project folder), or null. */
    public static Path bundledGame(Path programFolder) {
        if (programFolder == null) {
            return null;
        }
        Path pack = programFolder.resolve(GamePack.FILE_NAME);
        if (Files.isRegularFile(pack)) {
            return pack;
        }
        Path folder = programFolder.resolve("game");
        return GameProject.isProject(folder) ? folder : null;
    }

    /** The folder holding the game's jar (or its classes folder, when run from the build). */
    public static Path programFolder() {
        try {
            Path code = Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isDirectory(code) ? code : code.getParent();
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            return null;
        }
    }
}
