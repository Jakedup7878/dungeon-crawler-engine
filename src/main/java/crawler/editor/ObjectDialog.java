package crawler.editor;

import crawler.core.Encounters;
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
import java.util.List;

/** Edits an event, chest or NPC: its name, its text, its pictures and (for events) a battle. */
final class ObjectDialog {
    private ObjectDialog() {
    }

    /**
     * Shows the dialog; returns the edited object, or null if cancelled.
     *
     * @param looks    the game's looks, so the built-in pictures shown are the project's defaults
     * @param enemyIds the enemy ids the project knows, to suggest for event battles (may be empty)
     */
    static MapObject show(Component parent, MapObject object, boolean isNew, AssetStore assets,
                          ObjectLooks looks, List<String> enemyIds) {
        ObjectType type = object.type();
        JTextField name = new JTextField(object.name(), 28);
        JTextArea text = new JTextArea(object.text(), 6, 36);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JCheckBox once = new JCheckBox("Only the first time the party steps here", object.once());
        JTextField battle = new JTextField(String.join(", ", object.battle()), 28);
        ImagePicker sprite = new ImagePicker(assets, null, defaultImage(type, false, looks), switch (type) {
            case EVENT -> "None: the event can't be seen";
            case CHEST -> "The game's chest";
            case NPC -> "The game's NPC look";
        }, true);
        sprite.setSprite(object.sprite());
        ImagePicker opened = null;
        if (type == ObjectType.CHEST) {
            opened = new ImagePicker(assets, null, defaultImage(type, true, looks),
                    "The game's open chest (or the closed picture, if you chose one)", true);
            opened.setSprite(object.openSprite());
        }

        JPanel form = switch (type) {
            case EVENT -> MapDialogs.form(
                    new JLabel("Title"), name,
                    new JLabel("Message"), new JScrollPane(text),
                    new JLabel(), hint("Shown when the party steps onto this cell. Leave the title empty for none."),
                    new JLabel(), once,
                    new JLabel("Battle"), battle,
                    new JLabel(), hint(enemyIds.isEmpty()
                            ? "Enemy ids to fight here, comma separated (a boss, say). Make enemies in Project > Enemies."
                            : "Enemy ids to fight here, comma separated, such as " + String.join(", ",
                            enemyIds.subList(0, Math.min(3, enemyIds.size()))) + ". Leave empty for no battle."),
                    new JLabel("Picture"), sprite,
                    new JLabel(), hint("Give an event a picture to make it visible, like a fountain or a sign."));
            case CHEST -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel("Contents"), new JScrollPane(text),
                    new JLabel(), hint("Shown when the party opens the chest. Empty means an empty chest."),
                    new JLabel("Closed"), sprite,
                    new JLabel("Opened"), opened);
            case NPC -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel("Dialogue"), new JScrollPane(text),
                    new JLabel(), hint("What they say when the party walks up to them. Long text is shown a page at a time."),
                    new JLabel("Picture"), sprite);
        };
        String note = "Pictures are copied into the 'assets' folder when you save. PNGs with a transparent "
                + "background look best. For an animation, put the frames side by side in one image and set Frames.";
        focusWhenShown(name);
        String title = (isNew ? "New " : "") + type.displayName + " at " + object.x() + ", " + object.y();
        int answer = JOptionPane.showConfirmDialog(parent, new Object[]{form, hint(note)}, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        return new MapObject(type, object.x(), object.y(), name.getText(), text.getText(),
                sprite.sprite(), opened == null ? null : opened.sprite(), once.isSelected(),
                Encounters.parseIds(battle.getText()));
    }

    private static BufferedImage defaultImage(ObjectType type, boolean open, ObjectLooks looks) {
        return switch (type) {
            case EVENT -> null;
            case CHEST -> looks.chest(open).image(0);
            case NPC -> looks.npc().image(0);
        };
    }

    static JLabel hint(String text) {
        JLabel label = new JLabel("<html><body style='width: 330px'>" + text + "</body></html>");
        label.setForeground(new Color(100, 104, 116));
        return label;
    }

    static void focusWhenShown(JComponent field) {
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
