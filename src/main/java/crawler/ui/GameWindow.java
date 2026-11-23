package crawler.ui;

import crawler.core.DungeonMap;
import crawler.game.Game;
import crawler.render.AssetSource;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** A window playing one map. Used by the game itself and by the map editor's play test. */
public final class GameWindow {
    private GameWindow() {
    }

    /**
     * Opens a window that plays {@code map} from its start position. Call on the Swing thread.
     *
     * @param assets      where the map's own textures and object images come from
     * @param exitOnClose quit the program when the window closes (the game). Otherwise only the
     *                    window closes, and Esc closes it too (play-testing from the editor).
     */
    public static JFrame open(DungeonMap map, AssetSource assets, String title, boolean exitOnClose) {
        Game game = new Game(map);
        GamePanel panel = new GamePanel(game, assets);
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(exitOnClose ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        frame.add(panel);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                panel.stop();
            }
        });
        if (!exitOnClose) {
            JRootPane root = frame.getRootPane();
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
            root.getActionMap().put("close", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    frame.dispose();
                }
            });
        }
        frame.pack();
        frame.setMinimumSize(new Dimension(480, 320));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.start();
        return frame;
    }
}
