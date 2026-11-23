package crawler.editor;

import crawler.core.DungeonMap;
import crawler.core.GameProject;
import crawler.core.MapLoader;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point of the map editor, a separate program from the game.
 *
 * <pre>
 *   java crawler.editor.MapEditor               starts with a new, empty map
 *   java crawler.editor.MapEditor my-map.txt    opens a map (or starts a new one that saves there)
 *   java crawler.editor.MapEditor "My Game"     opens a project folder at its starting map
 * </pre>
 */
public final class MapEditor {
    private MapEditor() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
                // The default look and feel works fine too.
            }

            DungeonMap map = MapEdits.blank("New Floor", 20, 20);
            Path file = null;
            String error = null;
            if (args.length > 0) {
                Path requested = Path.of(args[0]).toAbsolutePath();
                if (GameProject.isProject(requested)) {
                    // A project folder: open the map the game starts on.
                    try {
                        requested = GameProject.open(requested).startMapFile();
                    } catch (IOException e) {
                        error = "Could not open the project:\n" + e.getMessage();
                    }
                }
                if (error != null) {
                    // Keep the new, empty map; the error is shown below.
                } else if (Files.exists(requested)) {
                    try {
                        map = MapLoader.load(requested);
                        file = requested;
                    } catch (IOException | IllegalArgumentException e) {
                        error = "Could not open " + requested.getFileName() + ":\n" + e.getMessage()
                                + "\n\nStarting with a new map instead.";
                    }
                } else {
                    // A new map that will be saved under the name given.
                    String name = requested.getFileName().toString().replaceFirst("\\.[^.]*$", "");
                    map = MapEdits.blank(name, 20, 20);
                    file = requested;
                }
            }

            EditorFrame frame = new EditorFrame(map, file);
            frame.showWindow();
            if (error != null) {
                JOptionPane.showMessageDialog(frame, error, "Map Editor", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
