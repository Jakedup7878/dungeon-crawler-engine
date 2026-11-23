package crawler.editor;

import crawler.core.EnemyType;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The fields for one kind of enemy, for {@link RosterDialog}. */
final class EnemyForm implements RosterDialog.Form<EnemyType> {
    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(18);
    private final StatFields stats = new StatFields();
    private final JSpinner exp = StatFields.spinner(0, 999_999);
    private final JSpinner gold = StatFields.spinner(0, 999_999);
    private final ImagePicker sprite;
    private final JComponent component;

    EnemyForm(AssetStore assets) {
        id.setToolTipText("How encounters refer to this enemy. Leave empty to make one from the name.");
        sprite = new ImagePicker(assets, null, null, "No image yet");
        List<JComponent> rows = new ArrayList<>(List.of(
                new JLabel("Name"), name,
                new JLabel("Id"), id));
        rows.addAll(List.of(stats.rows()));
        rows.addAll(List.of(
                new JLabel("Experience"), exp,
                new JLabel("Gold"), gold,
                new JLabel("Image"), sprite));
        component = MapDialogs.form(rows.toArray(new JComponent[0]));
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public void show(EnemyType enemy) {
        id.setText(enemy.id());
        name.setText(enemy.name());
        stats.show(enemy.stats());
        exp.setValue(enemy.exp());
        gold.setValue(enemy.gold());
        sprite.set(enemy.sprite());
    }

    @Override
    public EnemyType read() {
        String typedId = id.getText().strip();
        if (typedId.isEmpty()) {
            typedId = EnemyType.idFor(name.getText());
        }
        return new EnemyType(typedId, name.getText(), stats.read(),
                StatFields.value(exp), StatFields.value(gold), sprite.reference());
    }

    @Override
    public EnemyType create(List<EnemyType> enemies) {
        Set<String> taken = new HashSet<>();
        enemies.forEach(e -> taken.add(e.id()));
        String newId = "enemy";
        for (int n = 2; taken.contains(newId); n++) {
            newId = "enemy-" + n;
        }
        return EnemyType.create(newId, "New enemy");
    }

    @Override
    public String label(EnemyType enemy) {
        return (enemy.name().isEmpty() ? "(no name)" : enemy.name()) + "  [" + enemy.id() + "]";
    }

    @Override
    public String problem(List<EnemyType> enemies) {
        Set<String> seen = new HashSet<>();
        for (EnemyType enemy : enemies) {
            if (enemy.name().isEmpty()) {
                return "Every enemy needs a name.";
            }
            if (!EnemyType.isValidId(enemy.id())) {
                return "\"" + enemy.id() + "\" can't be an id. Use lower-case letters, digits and dashes, like giant-rat.";
            }
            if (!seen.add(enemy.id())) {
                return "Two enemies have the id \"" + enemy.id() + "\". Each needs its own.";
            }
        }
        return null;
    }
}
