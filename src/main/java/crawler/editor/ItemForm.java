package crawler.editor;

import crawler.core.AttackAnimation;
import crawler.core.Encounters;
import crawler.core.EnemyType;
import crawler.core.Item;
import crawler.core.ItemKind;
import crawler.core.ItemTarget;
import crawler.core.Stats;
import crawler.script.Script;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** The fields for one item, with its script, for {@link RosterDialog}. */
final class ItemForm implements RosterDialog.Form<Item> {
    /** A ready-made script to start from, with the kind and target that suit it. */
    record Example(String name, ItemKind kind, ItemTarget target, String script) {
        @Override
        public String toString() {
            return name;
        }
    }

    static final List<Example> EXAMPLES = List.of(
            new Example("Potion: heal HP", ItemKind.CONSUMABLE, ItemTarget.ALLY, "on use\n  heal target 30"),
            new Example("Potion: restore MP", ItemKind.CONSUMABLE, ItemTarget.ALLY, "on use\n  mana target 15"),
            new Example("Potion: cure poison and stun", ItemKind.CONSUMABLE, ItemTarget.ALLY, "on use\n  cure target"),
            new Example("Potion: revive with half HP", ItemKind.CONSUMABLE, ItemTarget.FALLEN,
                    "on use\n  revive target target.maxhp / 2"),
            new Example("Potion: strengthen for 3 rounds", ItemKind.CONSUMABLE, ItemTarget.ALLY,
                    "on use\n  boost target attack 5 for 3"),
            new Example("Bomb: damage every enemy", ItemKind.CONSUMABLE, ItemTarget.ENEMIES,
                    "on use\n  say \"The {item} explodes!\"\n  damage enemies random(10, 20)"),
            new Example("Weapon: sometimes burns", ItemKind.WEAPON, ItemTarget.SELF,
                    "on hit\n  if chance(30) and target.alive\n    say \"Flames engulf {target}!\"\n"
                            + "    damage target 4 + user.level\n  end"),
            new Example("Weapon: drains life", ItemKind.WEAPON, ItemTarget.SELF, "on hit\n  heal user damage / 4"),
            new Example("Weapon: poisons", ItemKind.WEAPON, ItemTarget.SELF,
                    "on hit\n  if chance(40) and target.alive\n    poison target 3 for 3\n  end"),
            new Example("Weapon: stuns", ItemKind.WEAPON, ItemTarget.SELF,
                    "on hit\n  if chance(20) and target.alive\n    stun target 1\n  end"),
            new Example("Weapon: strong against bosses (tier 3 up)", ItemKind.WEAPON, ItemTarget.SELF,
                    "on hit\n  if target.tier >= 3\n    damage target damage / 2\n  end"),
            new Example("Armor: thorns", ItemKind.BODY, ItemTarget.SELF,
                    "on hurt\n  say \"Thorns prick {target}!\"\n  damage target 1 + damage / 5"),
            new Example("Armor: heals its wearer each round", ItemKind.BODY, ItemTarget.SELF,
                    "on round\n  heal user 2"),
            new Example("Armor: defense up when a battle starts", ItemKind.SHIELD, ItemTarget.SELF,
                    "on battle\n  boost user defense 4 for 3"));

    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(18);
    private final JComboBox<ItemKind> kind = new JComboBox<>(ItemKind.values());
    private final JTextField description = new JTextField(24);
    private final JSpinner[] bonus = new JSpinner[5];
    private final JComboBox<ItemTarget> target = new JComboBox<>(ItemTarget.values());
    private final JCheckBox consumed = new JCheckBox("Used up when used");
    private final JTextField classes = new JTextField(18);
    private final ImagePicker icon;
    private final AnimationChooser animation;
    private final JTextArea script = new JTextArea(18, 40);
    private final JLabel scriptStatus = new JLabel(" ");
    private final JComponent component;

    ItemForm(AssetStore assets, List<AttackAnimation> animations) {
        id.setToolTipText("How chests and party members refer to this item. Leave empty to make one from the name.");
        classes.setToolTipText("Ids of the only classes that can equip it, comma separated. Empty: everyone.");
        icon = new ImagePicker(assets, null, null, "No picture", true);
        animation = new AnimationChooser(animations);
        JPanel bonuses = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        String[] names = {"HP", "MP", "Atk", "Def", "Spd"};
        for (int i = 0; i < bonus.length; i++) {
            bonus[i] = StatFields.spinner(-Item.MAX_BONUS, Item.MAX_BONUS);
            ((JSpinner.DefaultEditor) bonus[i].getEditor()).getTextField().setColumns(3);
            bonuses.add(new JLabel(names[i]));
            bonuses.add(bonus[i]);
        }
        JComponent fields = MapDialogs.form(
                new JLabel("Name"), name,
                new JLabel("Id"), id,
                new JLabel("Kind"), kind,
                new JLabel("Description"), description,
                new JLabel("Bonuses"), bonuses,
                new JLabel(), ObjectDialog.hint("Added while equipped (weapons, armor and accessories only)."),
                new JLabel("Who can equip"), classes,
                new JLabel("Attack animation"), animation,
                new JLabel(), ObjectDialog.hint("Weapons only. Built-in: the wearer's own."),
                new JLabel("Used on"), target,
                new JLabel(), consumed,
                new JLabel("Picture"), icon);
        component = new JPanel(new BorderLayout(14, 0));
        component.add(fields, BorderLayout.WEST);
        component.add(scriptPanel(), BorderLayout.CENTER);
    }

    private JComponent scriptPanel() {
        script.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        script.setTabSize(2);
        script.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                check();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                check();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                check();
            }
        });
        JComboBox<Object> examples = new JComboBox<>();
        examples.addItem("Start from an example...");
        EXAMPLES.forEach(examples::addItem);
        examples.addActionListener(e -> {
            if (examples.getSelectedItem() instanceof Example example) {
                if (script.getText().isBlank() || JOptionPane.showConfirmDialog(component,
                        "Replace this item's script, kind and target with the example's?", example.name(),
                        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                    use(example);
                }
                examples.setSelectedIndex(0);
            }
        });
        JButton load = new JButton("Load...");
        load.setToolTipText("Read the script from a text file");
        load.addActionListener(e -> load());
        JButton help = new JButton("Script help");
        help.addActionListener(e -> showHelp());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        top.add(new JLabel("Script"));
        top.add(examples);
        top.add(load);
        top.add(help);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(script), BorderLayout.CENTER);
        panel.add(scriptStatus, BorderLayout.SOUTH);
        return panel;
    }

    void use(Example example) {
        script.setText(example.script());
        script.setCaretPosition(0);
        kind.setSelectedItem(example.kind());
        target.setSelectedItem(example.target());
        consumed.setSelected(example.kind() == ItemKind.CONSUMABLE);
    }

    private void load() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Script (text) files", "txt", "script"));
        if (chooser.showOpenDialog(component) == JFileChooser.APPROVE_OPTION) {
            try {
                script.setText(Files.readString(chooser.getSelectedFile().toPath()).replace("\r", ""));
                script.setCaretPosition(0);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(component, "Could not read that file as text:\n" + e.getMessage());
            }
        }
    }

    /** Says whether the script is right, as it's typed. */
    private void check() {
        String problem = scriptProblem(script.getText());
        scriptStatus.setForeground(problem == null ? new Color(30, 120, 50) : new Color(180, 30, 30));
        scriptStatus.setText(problem != null ? "Script " + problem : script.getText().isBlank() ? " " : "Script OK");
        scriptStatus.setToolTipText(problem);
    }

    private static String scriptProblem(String text) {
        try {
            Script.parse(text.strip());
            return null;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    private void showHelp() {
        JTextArea text = new JTextArea("""
                A script is made of blocks. Each says when it runs:

                  on use       the item is used (consumables and key items)
                  on hit       the wearer hits someone (target = who was hit)
                  on hurt      the wearer is hit (target = the attacker)
                  on battle    a battle starts
                  on round     every round of a battle starts

                Commands (WHO: user, target, party, enemies, random_enemy, random_ally):

                  heal WHO 20              give back HP
                  mana WHO 10              give back MP (negative takes it)
                  damage WHO 8             take HP
                  revive WHO 15            bring back the fallen with this much HP
                  poison WHO 3 for 4       3 damage a round, for 4 rounds
                  stun WHO 1               miss 1 turn
                  cure WHO                 end poison and stun
                  boost WHO attack 5 for 3   attack, defense or speed; may be negative
                  gold 50                  give the party gold
                  say "Hello, {target}!"   also {user}, {item}, {damage}, {1 + 2}

                  if chance(30) and target.hp < 10
                    ...
                  else
                    ...
                  end
                  repeat 3 ... end         (each on lines of their own)
                  set power = user.level * 2
                  stop

                Numbers: 12, + - * / %, random(1, 6), chance(30), damage, round,
                battle (1 in battle), and user. or target. followed by hp, maxhp,
                mp, maxmp, attack, defense, speed, level, tier, alive, poisoned, stunned.
                Compare with < <= > >= == != and join with and, or, not.
                A line from # on is a comment.""", 34, 70);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setEditable(false);
        JOptionPane.showMessageDialog(component, new JScrollPane(text), "Item scripts", JOptionPane.PLAIN_MESSAGE);
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public void show(Item item) {
        id.setText(item.id());
        name.setText(item.name());
        kind.setSelectedItem(item.kind());
        description.setText(item.description());
        Stats b = item.bonus();
        int[] values = {b.hp(), b.mp(), b.attack(), b.defense(), b.speed()};
        for (int i = 0; i < bonus.length; i++) {
            bonus[i].setValue(values[i]);
        }
        target.setSelectedItem(item.target());
        consumed.setSelected(item.consumed());
        classes.setText(String.join(", ", item.classes()));
        icon.setSprite(item.icon());
        animation.setAnimation(item.animation());
        script.setText(item.scriptText());
        script.setCaretPosition(0);
        check();
    }

    @Override
    public Item read() {
        String typedId = id.getText().strip();
        if (typedId.isEmpty()) {
            typedId = EnemyType.idFor(name.getText());
        }
        Stats b = new Stats(StatFields.value(bonus[0]), StatFields.value(bonus[1]), StatFields.value(bonus[2]),
                StatFields.value(bonus[3]), StatFields.value(bonus[4]));
        String text = script.getText();
        // A script with a mistake is kept as typed; problem() stops it being saved.
        Script parsed = scriptProblem(text) == null ? Script.parse(text.strip()) : Script.EMPTY;
        return new Item(typedId, name.getText(), (ItemKind) kind.getSelectedItem(), description.getText(), b,
                (ItemTarget) target.getSelectedItem(), consumed.isSelected(), Encounters.parseIds(classes.getText()),
                icon.sprite(), animation.animation(), text, parsed);
    }

    @Override
    public Item create(List<Item> items) {
        return Item.create(EnemyForm.freeId(items.stream().map(Item::id).toList(), "item"), "New item");
    }

    @Override
    public String label(Item item) {
        return (item.name().isEmpty() ? "(no name)" : item.name()) + "  [" + item.id() + "]";
    }

    @Override
    public String problem(List<Item> items) {
        for (Item item : items) {
            String problem = scriptProblem(item.scriptText());
            if (problem != null) {
                return "The script of \"" + item.displayName() + "\", " + problem;
            }
        }
        return EnemyForm.idProblem(items.stream().map(Item::id).toList(), items.stream().map(Item::name).toList(), "item");
    }
}
