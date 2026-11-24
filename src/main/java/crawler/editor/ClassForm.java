package crawler.editor;

import crawler.core.AttackAnimation;
import crawler.core.CharacterClass;
import crawler.core.Encounters;
import crawler.core.EnemyType;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.List;

/** The fields for one class (Fighter, Mage...), for {@link RosterDialog}. */
final class ClassForm implements RosterDialog.Form<CharacterClass> {
    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(18);
    private final StatFields base = new StatFields();
    private final StatFields growth = new StatFields(true);
    private final AnimationChooser animation;
    private final ImagePicker portrait;
    private final JTextField equipment = new JTextField(22);
    private final JComponent component;

    ClassForm(AssetStore assets, List<AttackAnimation> animations) {
        id.setToolTipText("Party members refer to their class by name or id. Leave empty to make one from the name.");
        animation = new AnimationChooser(animations);
        portrait = new ImagePicker(assets, null, null, "No portrait");
        equipment.setToolTipText("Ids of the items a new member starts out wearing, comma separated.");
        List<JComponent> rows = new ArrayList<>(List.of(
                new JLabel("Name"), name,
                new JLabel("Id"), id,
                new JLabel(), ObjectDialog.hint("Stats at level 1:")));
        rows.addAll(List.of(base.rows()));
        rows.addAll(List.of(new JLabel(), ObjectDialog.hint("Gained at every level up:")));
        rows.addAll(List.of(growth.rows()));
        rows.addAll(List.of(
                new JLabel("Attack animation"), animation,
                new JLabel("Equipment"), equipment,
                new JLabel("Portrait"), portrait,
                new JLabel(), ObjectDialog.hint("Members of this class without a portrait of their own use this one.")));
        component = MapDialogs.form(rows.toArray(new JComponent[0]));
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public void show(CharacterClass c) {
        id.setText(c.id());
        name.setText(c.name());
        base.show(c.base());
        growth.show(c.growth());
        animation.setAnimation(c.attackAnimation());
        portrait.set(c.portrait());
        equipment.setText(String.join(", ", c.equipment()));
    }

    @Override
    public CharacterClass read() {
        String typedId = id.getText().strip();
        if (typedId.isEmpty()) {
            typedId = EnemyType.idFor(name.getText());
        }
        return new CharacterClass(typedId, name.getText(), base.read(), growth.read(),
                animation.animation(), portrait.reference(), Encounters.parseIds(equipment.getText()));
    }

    @Override
    public CharacterClass create(List<CharacterClass> classes) {
        return CharacterClass.create(EnemyForm.freeId(classes.stream().map(CharacterClass::id).toList(), "class"), "New class");
    }

    @Override
    public String label(CharacterClass c) {
        return (c.name().isEmpty() ? "(no name)" : c.name()) + "  [" + c.id() + "]";
    }

    @Override
    public String problem(List<CharacterClass> classes) {
        return EnemyForm.idProblem(classes.stream().map(CharacterClass::id).toList(),
                classes.stream().map(CharacterClass::name).toList(), "class");
    }
}
