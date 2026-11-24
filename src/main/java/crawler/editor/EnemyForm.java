package crawler.editor;

import crawler.core.AttackAnimation;
import crawler.core.EnemyType;
import crawler.core.Tier;
import crawler.render.ObjectLooks;

import javax.swing.JComboBox;
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
    private final List<Tier> tiers;
    private final JComboBox<String> tier = new JComboBox<>();
    /** A tier that no longer exists, kept until another is chosen. */
    private String missingTier;
    private final ImagePicker sprite;
    private final AnimationChooser animation;
    private final JComponent component;

    EnemyForm(AssetStore assets, List<AttackAnimation> animations, List<Tier> tiers) {
        this.tiers = tiers;
        tier.addItem("No tier");
        tiers.forEach(t -> tier.addItem(t.displayName() + "  [" + t.id() + "]"));
        tier.setToolTipText("Make tiers in Project > Enemy Tiers");
        id.setToolTipText("How maps refer to this enemy. Leave empty to make one from the name.");
        sprite = new ImagePicker(assets, null, ObjectLooks.defaultEnemy("enemy").image(0),
                "Built-in blob (a colour of its own per enemy)", true);
        animation = new AnimationChooser(animations);
        List<JComponent> rows = new ArrayList<>(List.of(
                new JLabel("Name"), name,
                new JLabel("Id"), id,
                new JLabel("Tier"), tier));
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
        missingTier = null;
        while (tier.getItemCount() > tiers.size() + 1) {
            tier.removeItemAt(tier.getItemCount() - 1);
        }
        int index = 0;
        for (int i = 0; i < tiers.size(); i++) {
            if (tiers.get(i).id().equals(enemy.tier())) {
                index = i + 1;
            }
        }
        if (enemy.tier() != null && index == 0) {
            missingTier = enemy.tier();
            tier.addItem(missingTier + "  (missing)");
            index = tier.getItemCount() - 1;
        }
        tier.setSelectedIndex(index);
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
        int index = tier.getSelectedIndex();
        String tierId = index <= 0 ? null : index <= tiers.size() ? tiers.get(index - 1).id() : missingTier;
        return new EnemyType(typedId, name.getText(), stats.read(),
                StatFields.value(exp), StatFields.value(gold), sprite.sprite(), animation.animation(), tierId);
    }

    @Override
    public EnemyType create(List<EnemyType> enemies) {
        return EnemyType.create(freeId(enemies.stream().map(EnemyType::id).toList(), "enemy"), "New enemy");
    }

    @Override
    public String label(EnemyType enemy) {
        return (enemy.name().isEmpty() ? "(no name)" : enemy.name()) + "  [" + enemy.id() + "]"
                + (enemy.tier() == null ? "" : "  " + enemy.tier());
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
