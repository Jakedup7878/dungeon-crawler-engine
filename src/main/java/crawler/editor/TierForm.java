package crawler.editor;

import crawler.core.EnemyType;
import crawler.core.Tier;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;

/** The fields for one enemy tier, for {@link RosterDialog}. */
final class TierForm implements RosterDialog.Form<Tier> {
    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(18);
    private final JButton color = new JButton("Choose...");
    private final JLabel swatch = new JLabel("  Enemy name  ");
    private final JSpinner weight = StatFields.spinner(1, Tier.MAX_WEIGHT);
    private final JSpinner hp = StatFields.spinner(1, Tier.MAX_PERCENT);
    private final JSpinner attack = StatFields.spinner(0, Tier.MAX_PERCENT);
    private final JSpinner defense = StatFields.spinner(0, Tier.MAX_PERCENT);
    private final JSpinner speed = StatFields.spinner(0, Tier.MAX_PERCENT);
    private final JSpinner exp = StatFields.spinner(0, Tier.MAX_PERCENT);
    private final JSpinner gold = StatFields.spinner(0, Tier.MAX_PERCENT);
    private final JCheckBox canFlee = new JCheckBox("The party can run from battles with these enemies");
    /** Tier ids in use, with the name of an enemy that uses each. */
    private final Map<String, String> inUse;
    private final JComponent component;

    TierForm(Map<String, String> inUse) {
        this.inUse = inUse;
        swatch.setOpaque(true);
        swatch.setBackground(new Color(18, 19, 26));
        swatch.setPreferredSize(new Dimension(110, 24));
        color.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(color, "Name colour", swatch.getForeground());
            if (chosen != null) {
                swatch.setForeground(chosen);
            }
        });
        component = MapDialogs.form(
                new JLabel("Name"), name,
                new JLabel("Id"), id,
                new JLabel("Name colour"), swatch,
                new JLabel(), color,
                new JLabel("Likelihood"), weight,
                new JLabel(), ObjectDialog.hint("In random battles, next to other tiers. An enemy with no tier has "
                        + Tier.DEFAULT_WEIGHT + "."),
                new JLabel("HP %"), hp,
                new JLabel("Attack %"), attack,
                new JLabel("Defense %"), defense,
                new JLabel("Speed %"), speed,
                new JLabel("Experience %"), exp,
                new JLabel("Gold %"), gold,
                new JLabel(), ObjectDialog.hint("Of each enemy's own numbers; 100 leaves them alone."),
                new JLabel(), canFlee);
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public void show(Tier t) {
        id.setText(t.id());
        name.setText(t.name());
        swatch.setForeground(new Color(t.color()));
        weight.setValue(t.weight());
        hp.setValue(t.hp());
        attack.setValue(t.attack());
        defense.setValue(t.defense());
        speed.setValue(t.speed());
        exp.setValue(t.exp());
        gold.setValue(t.gold());
        canFlee.setSelected(t.canFlee());
    }

    @Override
    public Tier read() {
        String typedId = id.getText().strip();
        return new Tier(typedId.isEmpty() ? EnemyType.idFor(name.getText()) : typedId, name.getText(),
                swatch.getForeground().getRGB(), StatFields.value(weight), StatFields.value(hp), StatFields.value(attack),
                StatFields.value(defense), StatFields.value(speed), StatFields.value(exp), StatFields.value(gold),
                canFlee.isSelected());
    }

    @Override
    public Tier create(List<Tier> tiers) {
        return Tier.create(EnemyForm.freeId(tiers.stream().map(Tier::id).toList(), "tier"), "New tier");
    }

    @Override
    public String label(Tier t) {
        return t.displayName() + "  [" + t.id() + "]";
    }

    @Override
    public String problem(List<Tier> tiers) {
        List<String> ids = tiers.stream().map(Tier::id).toList();
        // An empty list would bring the built-in tiers back, so those still count.
        List<String> left = ids.isEmpty() ? Tier.builtIn().stream().map(Tier::id).toList() : ids;
        for (Map.Entry<String, String> use : inUse.entrySet()) {
            if (!left.contains(use.getKey())) {
                return "\"" + use.getValue() + "\" is in the tier \"" + use.getKey()
                        + "\", so it can't go. Change that enemy's tier first (Project > Enemies).";
            }
        }
        return EnemyForm.idProblem(ids, tiers.stream().map(Tier::name).toList(), "tier");
    }
}
