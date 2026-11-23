package crawler.editor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
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
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edits a list of things, such as the party or the kinds of enemy: the list on the left,
 * a form for the selected one on the right, and Add / Remove / Up / Down buttons. It works
 * on a copy, so Cancel throws every change away.
 */
final class RosterDialog {
    /** The part that knows about one kind of thing. */
    interface Form<T> {
        /** The fields, laid out and ready to go in the dialog. */
        JComponent component();

        /** Fills the fields from an item. */
        void show(T item);

        /** The item as the fields describe it now. */
        T read();

        /** A new item for the Add button, given the ones already in the list. */
        T create(List<T> items);

        /** How an item appears in the list. */
        String label(T item);

        /** What's wrong with the list, which stops OK, or null if it's fine. */
        default String problem(List<T> items) {
            return null;
        }
    }

    private RosterDialog() {
    }

    /** Shows the dialog; returns the edited list, or null if cancelled. */
    static <T> List<T> show(Component parent, String title, String note, List<T> items, Form<T> form, int maxItems) {
        DefaultListModel<T> model = new DefaultListModel<>();
        items.forEach(model::addElement);
        JList<T> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(14);
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        list.setCellRenderer((l, item, index, selected, focused) ->
                renderer.getListCellRendererComponent(l, form.label(item), index, selected, focused));

        // The form shows one item at a time. Before the selection moves, its edits are written back.
        int[] shown = {-1};
        Runnable keepEdits = () -> {
            if (shown[0] >= 0 && shown[0] < model.size()) {
                model.set(shown[0], form.read());
            }
        };
        Runnable showSelected = () -> {
            int index = list.getSelectedIndex();
            shown[0] = index;
            setEnabledDeep(form.component(), index >= 0);
            if (index >= 0) {
                form.show(model.get(index));
            }
        };
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                keepEdits.run();
                showSelected.run();
            }
        });

        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");
        JButton up = new JButton("Move Up");
        JButton down = new JButton("Move Down");
        add.addActionListener(e -> {
            keepEdits.run();
            if (model.size() >= maxItems) {
                JOptionPane.showMessageDialog(list, "There can be at most " + maxItems + ".");
                return;
            }
            model.addElement(form.create(Collections.list(model.elements())));
            list.setSelectedIndex(model.size() - 1);
        });
        remove.addActionListener(e -> {
            int index = list.getSelectedIndex();
            if (index < 0) {
                return;
            }
            shown[0] = -1; // it's going: don't write the form back into it
            model.remove(index);
            list.setSelectedIndex(Math.min(index, model.size() - 1));
            showSelected.run();
        });
        up.addActionListener(e -> move(list, model, keepEdits, shown, -1));
        down.addActionListener(e -> move(list, model, keepEdits, shown, 1));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(up);
        buttons.add(down);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(230, 300));
        JPanel left = new JPanel(new BorderLayout(0, 6));
        left.add(scroll, BorderLayout.CENTER);
        left.add(buttons, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title, JDialog.ModalityType.APPLICATION_MODAL);
        List<List<T>> result = new ArrayList<>();
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> {
            keepEdits.run();
            List<T> edited = Collections.list(model.elements());
            String problem = form.problem(edited);
            if (problem != null) {
                JOptionPane.showMessageDialog(dialog, problem, title, JOptionPane.WARNING_MESSAGE);
                return;
            }
            result.add(edited);
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(ObjectDialog.hint(note), BorderLayout.CENTER);
        JPanel okCancel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        okCancel.add(ok);
        okCancel.add(cancel);
        bottom.add(okCancel, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(14, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        content.add(left, BorderLayout.WEST);
        content.add(form.component(), BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        if (model.isEmpty()) {
            showSelected.run();
        } else {
            list.setSelectedIndex(0);
        }
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // waits here until OK or Cancel
        return result.isEmpty() ? null : result.get(0);
    }

    private static <T> void move(JList<T> list, DefaultListModel<T> model, Runnable keepEdits, int[] shown, int delta) {
        int from = list.getSelectedIndex();
        int to = from + delta;
        if (from < 0 || to < 0 || to >= model.size()) {
            return;
        }
        keepEdits.run();
        T item = model.get(from);
        model.set(from, model.get(to));
        model.set(to, item);
        shown[0] = -1; // both items are already up to date
        list.setSelectedIndex(to);
    }

    private static void setEnabledDeep(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setEnabledDeep(child, enabled);
            }
        }
    }
}
