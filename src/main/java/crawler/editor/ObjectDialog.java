package crawler.editor;

import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.render.ObjectLooks;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;

/** Edits an event, chest or NPC: its name, its text and its images. */
final class ObjectDialog {
    private ObjectDialog() {
    }

    /** Shows the dialog; returns the edited object, or null if cancelled. */
    static MapObject show(Component parent, MapObject object, boolean isNew, AssetStore assets) {
        ObjectType type = object.type();
        JTextField name = new JTextField(object.name(), 28);
        JTextArea text = new JTextArea(object.text(), 6, 36);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JCheckBox once = new JCheckBox("Only the first time the party steps here", object.once());
        ImagePicker sprite = new ImagePicker(assets, object.sprite(), defaultImage(type, false), switch (type) {
            case EVENT -> "None: the event can't be seen";
            case CHEST -> "Built-in chest";
            case NPC -> "Built-in spinning blue top";
        });
        ImagePicker opened = type != ObjectType.CHEST ? null
                : new ImagePicker(assets, object.openSprite(), defaultImage(type, true),
                "Built-in open chest (or the closed image, if you chose one)");

        JPanel form = switch (type) {
            case EVENT -> MapDialogs.form(
                    new JLabel("Title"), name,
                    new JLabel("Message"), new JScrollPane(text),
                    new JLabel(), hint("Shown when the party steps onto this cell. Leave the title empty for none."),
                    new JLabel(), once,
                    new JLabel("Image"), sprite,
                    new JLabel(), hint("Give an event an image to make it visible, like a fountain or a sign."));
            case CHEST -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel("Contents"), new JScrollPane(text),
                    new JLabel(), hint("Shown when the party opens the chest. Empty means an empty chest."),
                    new JLabel("Closed image"), sprite,
                    new JLabel("Opened image"), opened);
            case NPC -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel("Dialogue"), new JScrollPane(text),
                    new JLabel(), hint("What they say when the party walks up to them. Long text is shown a page at a time."),
                    new JLabel("Image"), sprite);
        };
        String note = "Images are copied into an 'assets' folder next to the map when you save. "
                + "PNGs with a transparent background look best.";
        focusWhenShown(name);
        String title = (isNew ? "New " : "") + type.displayName + " at " + object.x() + ", " + object.y();
        int answer = JOptionPane.showConfirmDialog(parent, new Object[]{form, hint(note)}, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        return new MapObject(type, object.x(), object.y(), name.getText(), text.getText(),
                sprite.reference(), opened == null ? null : opened.reference(), once.isSelected());
    }

    private static BufferedImage defaultImage(ObjectType type, boolean open) {
        return switch (type) {
            case EVENT -> null;
            case CHEST -> ObjectLooks.defaultChest(open).image(0);
            case NPC -> ObjectLooks.defaultNpc().image(0);
        };
    }

    static JLabel hint(String text) {
        JLabel label = new JLabel("<html><body style='width: 330px'>" + text + "</body></html>");
        label.setForeground(new Color(100, 104, 116));
        return label;
    }

    private static void focusWhenShown(JComponent field) {
        field.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                field.requestFocusInWindow();
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
    }
}
