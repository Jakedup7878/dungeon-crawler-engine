package crawler.editor;

import crawler.core.DungeonMap;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/** The editor's small dialogs. Each returns null if cancelled. */
final class MapDialogs {
    static final int MAX_SIZE = 100;

    record NewMap(String name, int width, int height) {
    }

    /** New size, plus where the old map lands on the new grid. */
    record Resize(int width, int height, int offsetX, int offsetY) {
    }

    private MapDialogs() {
    }

    static NewMap askNewMap(Component parent) {
        JTextField name = new JTextField("New Floor", 18);
        JSpinner width = sizeSpinner(20);
        JSpinner height = sizeSpinner(20);
        JPanel form = form(
                new JLabel("Name"), name,
                new JLabel("Width (cells)"), width,
                new JLabel("Height (cells)"), height);
        String note = "The new map is solid rock with one open cell in the middle, where the party starts.";
        int answer = JOptionPane.showConfirmDialog(parent, new Object[]{form, note}, "New map",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        String text = name.getText().strip();
        return new NewMap(text.isEmpty() ? "Untitled" : text, (Integer) width.getValue(), (Integer) height.getValue());
    }

    static Resize askResize(Component parent, DungeonMap map) {
        JSpinner width = sizeSpinner(map.width());
        JSpinner height = sizeSpinner(map.height());

        // Which side the existing map sticks to: like "canvas size" in a paint program.
        String[] arrows = {"↖", "↑", "↗", "←", "●", "→", "↙", "↓", "↘"};
        JToggleButton[] anchors = new JToggleButton[9];
        JPanel anchorGrid = new JPanel(new GridLayout(3, 3, 2, 2));
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < 9; i++) {
            anchors[i] = new JToggleButton(arrows[i]);
            anchors[i].setMargin(new Insets(2, 2, 2, 2));
            anchors[i].setPreferredSize(new Dimension(34, 30));
            anchors[i].setToolTipText("Keep the existing map in this corner or side");
            group.add(anchors[i]);
            anchorGrid.add(anchors[i]);
        }
        anchors[0].setSelected(true);

        JPanel form = form(
                new JLabel("Width (cells)"), width,
                new JLabel("Height (cells)"), height,
                new JLabel("Keep the map at"), anchorGrid);
        String note = "Now " + map.width() + " x " + map.height() + ". New space is solid rock; "
                + "anything outside the new size is cut off.";
        int answer = JOptionPane.showConfirmDialog(parent, new Object[]{form, note}, "Resize map",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        int anchor = 0;
        for (int i = 0; i < 9; i++) {
            if (anchors[i].isSelected()) {
                anchor = i;
            }
        }
        int newWidth = (Integer) width.getValue();
        int newHeight = (Integer) height.getValue();
        return new Resize(newWidth, newHeight,
                offset(anchor % 3, newWidth - map.width()),
                offset(anchor / 3, newHeight - map.height()));
    }

    /** 0 = keep at the start, 1 = centre, 2 = keep at the end. */
    private static int offset(int anchor, int growth) {
        return switch (anchor) {
            case 0 -> 0;
            case 1 -> growth / 2;
            default -> growth;
        };
    }

    static String askName(Component parent, String current) {
        Object answer = JOptionPane.showInputDialog(parent, "Map name (shown in the game):", "Rename map",
                JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (answer == null || answer.toString().isBlank()) {
            return null;
        }
        return answer.toString().strip();
    }

    static void showHelp(Component parent) {
        String html = """
                <html><body style='width: 430px'>
                <h3 style='margin-top:0'>Building a floor</h3>
                <p>Walls sit on the grid lines <i>between</i> cells, so a cell can have a wall on one side
                and a door on another. Grey hatched cells are solid rock that the party never enters.</p>
                <table cellpadding='2'>
                <tr><td><b>1 / R</b></td><td>Room: drag a rectangle to carve a walled room.</td></tr>
                <tr><td><b>2 / C</b></td><td>Corridor: drag to dig a passage. Drag into a room to break through its wall.</td></tr>
                <tr><td><b>3 / W</b></td><td>Wall: click or drag along grid lines.</td></tr>
                <tr><td><b>4 / D</b></td><td>Door: click a grid line.</td></tr>
                <tr><td><b>5 / S</b></td><td>Start: where the party begins; click it again to turn.</td></tr>
                <tr><td><b>6 / V</b></td><td>Event: a message when the party steps on the cell. Invisible unless you give it an image.</td></tr>
                <tr><td><b>7 / T</b></td><td>Chest: the party opens it by walking into it.</td></tr>
                <tr><td><b>8 / N</b></td><td>NPC: someone to talk to by walking into them. Looks like a spinning top until you give it an image.</td></tr>
                <tr><td><b>Right mouse</b></td><td>Does the opposite: fills with rock, removes walls, doors and objects.</td></tr>
                </table>
                <p>With the Event, Chest or NPC tool, click an object to edit it and drag it to move it.
                <b>Edit &gt; Textures</b> (Ctrl+T) sets your own wall, door, floor and ceiling images, and
                <b>Edit &gt; Map Settings</b> (Ctrl+M) the floor's music and random battles.
                Every picture and sound you choose is copied into the project's <code>assets</code> folder when you save.</p>
                <h3>The rest of the game</h3>
                <p>The <b>Project</b> menu holds what belongs to the whole game: <b>Settings</b> (party creation,
                starting gold, the default NPC and chest looks, battle and victory music), <b>Party Members</b>,
                <b>Classes</b>, <b>Enemies</b> and <b>Attack Animations</b>. For an animation, put the frames side by side
                in one image and set <b>Frames</b>.</p>
                <h3>Getting around</h3>
                <table cellpadding='2'>
                <tr><td><b>Mouse wheel</b></td><td>Scroll (hold Shift to scroll sideways)</td></tr>
                <tr><td><b>Ctrl + wheel</b></td><td>Zoom</td></tr>
                <tr><td><b>Middle drag</b></td><td>Pan</td></tr>
                <tr><td><b>Q / E</b></td><td>Turn the 3D preview</td></tr>
                <tr><td><b>Ctrl+Z / Ctrl+Y</b></td><td>Undo / redo</td></tr>
                <tr><td><b>F5</b></td><td>Play test from the start</td></tr>
                <tr><td><b>F6</b></td><td>Play test from where the 3D preview is looking</td></tr>
                </table>
                <p>Maps are saved as plain text in the format the game reads, so you can also open them in
                any text editor. Play one with <code>run.cmd my-map.txt</code>.</p>
                </body></html>
                """;
        JOptionPane.showMessageDialog(parent, html, "Map Editor help", JOptionPane.PLAIN_MESSAGE);
    }

    private static JSpinner sizeSpinner(int value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.min(MAX_SIZE, value), 1, MAX_SIZE, 1));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(4);
        return spinner;
    }

    /** Label / field pairs in two columns. */
    static JPanel form(JComponent... labelsAndFields) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        for (int i = 0; i < labelsAndFields.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.NONE;
            panel.add(labelsAndFields[i], c);
            c.gridx = 1;
            c.fill = GridBagConstraints.NONE;
            panel.add(labelsAndFields[i + 1], c);
        }
        return panel;
    }
}
