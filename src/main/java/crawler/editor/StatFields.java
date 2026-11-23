package crawler.editor;

import crawler.core.Stats;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.text.ParseException;

/**
 * Number boxes for HP, MP, attack, defense and speed, shared by the party, class and enemy
 * forms. A growth set (how much a class gains per level) allows 0 HP.
 */
final class StatFields {
    private final boolean growth;
    private final JSpinner hp;
    private final JSpinner mp = spinner(0, Stats.MAX_POINTS);
    private final JSpinner attack = spinner(0, Stats.MAX_RATING);
    private final JSpinner defense = spinner(0, Stats.MAX_RATING);
    private final JSpinner speed = spinner(0, Stats.MAX_RATING);

    StatFields() {
        this(false);
    }

    /** @param growth true for "per level" numbers */
    StatFields(boolean growth) {
        this.growth = growth;
        this.hp = spinner(growth ? 0 : 1, Stats.MAX_POINTS);
    }

    /** Label / field pairs, for {@link MapDialogs#form}. */
    JComponent[] rows() {
        String after = growth ? " per level" : "";
        return new JComponent[]{
                new JLabel("HP" + after), hp,
                new JLabel("MP" + after), mp,
                new JLabel("Attack" + after), attack,
                new JLabel("Defense" + after), defense,
                new JLabel("Speed" + after), speed};
    }

    void show(Stats stats) {
        hp.setValue(stats.hp());
        mp.setValue(stats.mp());
        attack.setValue(stats.attack());
        defense.setValue(stats.defense());
        speed.setValue(stats.speed());
    }

    Stats read() {
        return new Stats(value(hp), value(mp), value(attack), value(defense), value(speed));
    }

    static JSpinner spinner(int min, int max) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, 1));
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(6);
        return spinner;
    }

    /** The number in a box, including one typed but not yet confirmed with Enter. */
    static int value(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException e) {
            // Not a number: keep the last good value.
        }
        return ((Number) spinner.getValue()).intValue();
    }
}
