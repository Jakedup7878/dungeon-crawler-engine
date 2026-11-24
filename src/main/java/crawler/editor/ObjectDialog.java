package crawler.editor;

import crawler.core.Destination;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Encounters;
import crawler.core.ItemStack;
import crawler.core.MapObject;
import crawler.core.ObjectType;
import crawler.render.ObjectLooks;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Edits an event, chest, NPC or staircase: its name, its text, its pictures, and what it does. */
final class ObjectDialog {
    private ObjectDialog() {
    }

    /**
     * Shows the dialog; returns the edited object, or null if cancelled.
     *
     * @param looks    the game's looks, so the built-in pictures shown are the project's defaults
     * @param enemyIds the enemy ids the project knows, to suggest for event battles (may be empty)
     * @param itemIds  the same for items, for chests
     * @param map      the map it's on, for which sides of the cell have walls
     * @param floors   the project's floors by file, for stairs (null values can't be read)
     */
    static MapObject show(Component parent, MapObject object, boolean isNew, AssetStore assets,
                          ObjectLooks looks, List<String> enemyIds, List<String> itemIds, Map<String, DungeonMap> floors, DungeonMap map) {
        ObjectType type = object.type();
        JTextField name = new JTextField(object.name(), 28);
        JTextArea text = new JTextArea(object.text(), 6, 36);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        JCheckBox once = new JCheckBox("Only the first time the party steps here", object.once());
        JTextField battle = new JTextField(String.join(", ", object.battle()), 28);
        JTextField items = new JTextField(ItemStack.text(object.items()), 28);
        ImagePicker sprite = new ImagePicker(assets, null, defaultImage(type, false, looks), switch (type) {
            case EVENT -> "None: the event can't be seen";
            case STAIRS -> "Built-in stairs, up or down";
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

        Destination current = object.destination();
        JComboBox<String> floor = new JComboBox<>(floors.keySet().stream().sorted().toArray(String[]::new));
        floor.setEditable(true);
        floor.setSelectedItem(current.map());
        JComboBox<String> arrive = new JComboBox<>();
        arrive.setEditable(true);
        Runnable offerArrivals = () -> {
            arrive.removeAllItems();
            arrive.addItem("start");
            DungeonMap chosen = floors.get(String.valueOf(floor.getEditor().getItem()).strip());
            if (chosen != null) {
                arrivals(chosen).forEach(arrive::addItem);
            }
        };
        offerArrivals.run();
        arrive.setSelectedItem(current.arrivalText());
        floor.addActionListener(e -> {
            offerArrivals.run();
            // On another floor the old cell means nothing; its first stairs are the likeliest way in.
            arrive.setSelectedIndex(arrive.getItemCount() > 1 ? 1 : 0);
        });
        JComboBox<String> way = new JComboBox<>(new String[]{"Down", "Up"});
        way.setSelectedIndex(current.up() ? 1 : 0);

        JComboBox<String> place = new JComboBox<>();
        for (Direction side : Direction.values()) {
            place.addItem("On the " + side.displayName().toLowerCase() + " wall"
                    + (map.edge(object.x(), object.y(), side) == EdgeType.WALL ? "" : "  (no wall there)"));
        }
        place.addItem("In the middle of the floor");
        place.setSelectedIndex(object.wall() == null ? Direction.values().length : object.wall().ordinal());
        JCheckBox walkThrough = new JCheckBox("Players can walk through", object.walkThrough());

        JPanel form = switch (type) {
            case STAIRS -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel(), hint("Shown in the log when the party takes them. May be empty."),
                    new JLabel("Leads to"), floor,
                    new JLabel(), hint(floors.isEmpty() ? "The other floor's file, such as maps/b2f.txt. Save this map in a "
                            + "project to choose from a list." : "The floor's file. To make a new floor: File > New, "
                            + "then save it in the project's maps folder. The same floor works too, as a teleporter."),
                    new JLabel("Arrive at"), arrive,
                    new JLabel(), hint("'start' (that floor's start), or a cell and a direction to face, like 3,4,north. "
                            + "The list offers that floor's stairs."),
                    new JLabel("Looks like"), way,
                    new JLabel("Placed"), place,
                    new JLabel(), hint("The party takes stairs by walking into them. On a wall, the picture covers the wall and "
                            + "the cell stays free; in the middle of the floor, they fill the cell."),
                    new JLabel("Message"), new JScrollPane(text),
                    new JLabel(), hint("Shown on arrival. Leave empty for none."),
                    new JLabel("Picture"), sprite);
            case EVENT -> MapDialogs.form(
                    new JLabel("Title"), name,
                    new JLabel("Message"), new JScrollPane(text),
                    new JLabel(), hint("Shown when the party steps onto this cell. Leave the title empty for none."),
                    new JLabel(), once,
                    new JLabel("Battle"), battle,
                    new JLabel(), hint(enemyIds.isEmpty()
                            ? "Enemy ids to fight here, comma separated (a boss, say). Make enemies in Project > Enemies."
                            : "Enemy ids to fight here, comma separated (or tier:boss for any boss), such as " + String.join(", ",
                            enemyIds.subList(0, Math.min(3, enemyIds.size()))) + ". Leave empty for no battle."),
                    new JLabel("Picture"), sprite,
                    new JLabel(), hint("Give an event a picture to make it visible, like a fountain or a sign."));
            case CHEST -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel("Contents"), new JScrollPane(text),
                    new JLabel(), hint("Text shown when the party opens the chest. It doesn't have to list the items."),
                    new JLabel("Items"), items,
                    new JLabel(), hint("What the party gets: item ids and how many, comma separated, such as "
                            + (itemIds.isEmpty() ? "potion x2. Make items in Project > Items."
                            : itemIds.get(0) + " x2" + (itemIds.size() > 1 ? ", " + itemIds.get(1) : "") + ".")),
                    new JLabel("Closed"), sprite,
                    new JLabel("Opened"), opened);
            case NPC -> MapDialogs.form(
                    new JLabel("Name"), name,
                    new JLabel("Dialogue"), new JScrollPane(text),
                    new JLabel(), walkThrough,
                    new JLabel(), hint("What they say when the party presses Space beside them, or walks into them if they block the way. "
                            + "Long text is shown a page at a time."),
                    new JLabel("Picture"), sprite);
        };
        String note = "Pictures are copied into the 'assets' folder when you save. PNGs with a transparent "
                + "background look best. For an animation, put the frames side by side in one image and set Frames.";
        focusWhenShown(name);
        String title = (isNew ? "New " : "") + type.displayName + " at " + object.x() + ", " + object.y();
        List<ItemStack> stacks = null;
        Destination destination = Destination.NONE;
        while (stacks == null) {
            int answer = JOptionPane.showConfirmDialog(parent, new Object[]{form, hint(note)}, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            try {
                destination = Destination.parse(String.valueOf(floor.getEditor().getItem()),
                        String.valueOf(arrive.getEditor().getItem()), way.getSelectedIndex() == 1);
                stacks = ItemStack.parse(items.getText());
            } catch (IllegalArgumentException e) { // ask again, with everything as it was typed
                JOptionPane.showMessageDialog(parent, e.getMessage(), title, JOptionPane.WARNING_MESSAGE);
            }
        }
        return new MapObject(type, object.x(), object.y(), name.getText(), text.getText(),
                sprite.sprite(), opened == null ? null : opened.sprite(), once.isSelected(),
                Encounters.parseIds(battle.getText()), stacks, destination,
                place.getSelectedIndex() < Direction.values().length ? Direction.values()[place.getSelectedIndex()] : null,
                walkThrough.isSelected());
    }

    /** Where the party can arrive by a floor's stairs: in front of them, facing away. */
    static List<String> arrivals(DungeonMap map) {
        List<String> out = new ArrayList<>();
        for (MapObject o : map.objects()) {
            if (o.type() == ObjectType.STAIRS) {
                if (o.wall() != null) {
                    out.add(new Destination("", o.x(), o.y(), o.wall().opposite(), false).arrivalText());
                    continue;
                }
                for (Direction d : Direction.values()) {
                    if (map.canStep(o.x(), o.y(), d)) {
                        out.add(new Destination("", o.x() + d.dx, o.y() + d.dy, d, false).arrivalText());
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static BufferedImage defaultImage(ObjectType type, boolean open, ObjectLooks looks) {
        return switch (type) {
            case EVENT -> null;
            case STAIRS -> ObjectLooks.defaultStairs(false).image(0);
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
