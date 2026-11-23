package crawler.editor;

import crawler.core.AttackAnimation;
import crawler.core.EnemyType;
import crawler.render.ObjectLooks;

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
    private final AnimationChooser animation;
    private final JComponent component;

    EnemyForm(AssetStore assets, List<AttackAnimation> animations) {
        id.setToolTipText("How maps refer to this enemy. Leave empty to make one from the name.");
        sprite = new ImagePicker(assets, null, ObjectLooks.defaultEnemy("enemy").image(0),
                "Built-in blob (a colour of its own per enemy)", true);
        animation = new AnimationChooser(animations);
        List<JComponent> rows = new ArrayList<>(List.of(
                new JLabel("Name"), name,
                new JLabel("Id"), id));
        rows.addAll(List.of(stats.rows()));
        rows.addAll(List.of(
                new JLabel("Experience"), exp,
                new JLabel("Gold"), gold,
                new JLabel("Attack animation"), animation,
                new JLabel("Picture"), sprite));
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
        animation.setAnimation(enemy.attackAnimation());
        sprite.setSprite(enemy.sprite());
    }

    @Override
    public EnemyType read() {
        String typedId = id.getText().strip();
        if (typedId.isEmpty()) {
            typedId = EnemyType.idFor(name.getText());
        }
        return new EnemyType(typedId, name.getText(), stats.read(),
                StatFields.value(exp), StatFields.value(gold), sprite.sprite(), animation.animation());
    }

    @Override
    public EnemyType create(List<EnemyType> enemies) {
        return EnemyType.create(freeId(enemies.stream().map(EnemyType::id).toList(), "enemy"), "New enemy");
    }

    @Override
    public String label(EnemyType enemy) {
        return (enemy.name().isEmpty() ? "(no name)" : enemy.name()) + "  [" + enemy.id() + "]";
    }

    @Override
    public String problem(List<EnemyType> enemies) {
        return idProblem(enemies.stream().map(EnemyType::id).toList(),
                enemies.stream().map(EnemyType::name).toList(), "enemy");
    }

    /** "enemy", or "enemy-2" and so on if that's taken. */
    static String freeId(List<String> taken, String base) {
        Set<String> used = new HashSet<>(taken);
        String id = base;
        for (int n = 2; used.contains(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    /** Names must be filled in, ids valid and different. Null if all is well. */
    static String idProblem(List<String> ids, List<String> names, String what) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (names.get(i).isEmpty()) {
                return "Every " + what + " needs a name.";
            }
            if (!EnemyType.isValidId(id)) {
                return "\"" + id + "\" can't be an id. Use lower-case letters, digits and dashes, like giant-rat.";
            }
            if (!seen.add(id)) {
                return "Two have the id \"" + id + "\". Each " + what + " needs its own.";
            }
        }
        return null;
    }
}
