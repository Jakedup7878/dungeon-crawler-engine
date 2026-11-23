package crawler.editor;

import crawler.core.Stats;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.text.ParseException;

/** Number boxes for HP, MP, attack, defense and speed, shared by the party and enemy forms. */
final class StatFields {
    private final JSpinner hp = spinner(1, Stats.MAX_POINTS);
    private final JSpinner mp = spinner(0, Stats.MAX_POINTS);
    private final JSpinner attack = spinner(0, Stats.MAX_RATING);
    private final JSpinner defense = spinner(0, Stats.MAX_RATING);
    private final JSpinner speed = spinner(0, Stats.MAX_RATING);

    /** Label / field pairs, for {@link MapDialogs#form}. */
    JComponent[] rows() {
        return new JComponent[]{
                new JLabel("HP"), hp,
                new JLabel("MP"), mp,
                new JLabel("Attack"), attack,
                new JLabel("Defense"), defense,
                new JLabel("Speed"), speed};
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
        return (Integer) spinner.getValue();
    }
}
