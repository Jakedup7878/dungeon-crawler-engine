package crawler.editor;

import crawler.core.DungeonMap;
import crawler.core.GameProject;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The project's floors, in order: open one, add one, reorder them, choose the starting one. */
final class FloorsDialog {
    /** What the user chose to do, which closes the dialog. */
    record Choice(boolean add, String open) {
    }

    private FloorsDialog() {
    }

    /**
     * Shows the list. Reordering and the starting floor are saved in the project at once.
     *
     * @param maps    the floors by file, for their names (null values can't be read)
     * @param current the open floor's file, or null
     * @return what to do next, or null if just closed
     */
    static Choice show(Frame parent, GameProject project, Map<String, DungeonMap> maps, String current) {
        JDialog dialog = new JDialog(parent, "Floors - " + project.name(), true);
        DefaultListModel<String> model = new DefaultListModel<>();
        project.floors().forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        javax.swing.DefaultListCellRenderer renderer = new javax.swing.DefaultListCellRenderer();
        list.setCellRenderer((l, floor, index, selected, focused) -> renderer.getListCellRendererComponent(l,
                label(index, floor, maps.get(floor), project.startMap(), current), index, selected, focused));
        Choice[] choice = {null};

        Runnable open = () -> {
            if (list.getSelectedValue() != null) {
                choice[0] = new Choice(false, list.getSelectedValue());
                dialog.dispose();
            }
        };
        JButton openButton = new JButton("Open");
        openButton.addActionListener(e -> open.run());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    open.run();
                }
            }
        });
        JButton add = new JButton("Add Floor...");
        add.addActionListener(e -> {
            choice[0] = new Choice(true, null);
            dialog.dispose();
        });
        JButton up = new JButton("Move Up");
        up.addActionListener(e -> move(dialog, project, list, model, -1));
        JButton down = new JButton("Move Down");
        down.addActionListener(e -> move(dialog, project, list, model, 1));
        JButton start = new JButton("Make Starting Floor");
        start.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                project.setStartMap(project.root().resolve(list.getSelectedValue()));
                save(dialog, project);
                list.repaint();
            }
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        for (JButton b : List.of(openButton, add, up, down, start, close)) {
            buttons.add(b);
        }
        JPanel right = new JPanel(new BorderLayout());
        right.add(buttons, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(420, 260));
        JPanel content = new JPanel(new BorderLayout(10, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));
        content.add(scroll, BorderLayout.CENTER);
        content.add(right, BorderLayout.EAST);
        content.add(ObjectDialog.hint("The order decides which floor is the previous one for View > Floor Guide. "
                + "Stairs decide how the party gets between them."), BorderLayout.SOUTH);
        dialog.setContentPane(content);
        list.setSelectedIndex(Math.max(0, model.indexOf(current == null ? "" : current)));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // waits here until it's closed
        return choice[0];
    }

    static String label(int index, String floor, DungeonMap map, String start, String current) {
        return (index + 1) + ".  " + (map == null ? "(can't be read)" : map.name()) + "   [" + floor + "]"
                + (floor.equals(start) ? "   start" : "") + (floor.equals(current) ? "   open" : "");
    }

    private static void move(JDialog dialog, GameProject project, JList<String> list, DefaultListModel<String> model, int delta) {
        int from = list.getSelectedIndex();
        int to = from + delta;
        if (from < 0 || to < 0 || to >= model.size()) {
            return;
        }
        List<String> floors = new ArrayList<>(Collections.list(model.elements()));
        Collections.swap(floors, from, to);
        project.setFloors(floors);
        save(dialog, project);
        model.clear();
        floors.forEach(model::addElement);
        list.setSelectedIndex(to);
    }

    private static void save(JDialog dialog, GameProject project) {
        try {
            project.save();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(dialog, "Could not save " + GameProject.FILE_NAME + ":\n" + e.getMessage(),
                    "Floors", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** A file in a folder that doesn't exist yet: "b2f.txt", or "b2f-2.txt" and so on. */
    static Path freeFile(Path folder, String name) {
        String base = name.endsWith(".txt") ? name.substring(0, name.length() - 4) : name;
        Path file = folder.resolve(base + ".txt");
        for (int n = 2; Files.exists(file); n++) {
            file = folder.resolve(base + "-" + n + ".txt");
        }
        return file;
    }
}
