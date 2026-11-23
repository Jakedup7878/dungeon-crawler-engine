package crawler.editor;

import crawler.core.PartyMember;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.util.ArrayList;
import java.util.List;

/** The fields for one party member, for {@link RosterDialog}. */
final class PartyForm implements RosterDialog.Form<PartyMember> {
    private static final String[] CLASSES = {"Fighter", "Mage", "Priest", "Thief"};

    private final JTextField name = new JTextField(18);
    private final JComboBox<String> characterClass = new JComboBox<>(CLASSES);
    private final JSpinner level = StatFields.spinner(1, 99);
    private final StatFields stats = new StatFields();
    private final ImagePicker portrait;
    private final JComponent component;

    PartyForm(AssetStore assets) {
        characterClass.setEditable(true); // pick one, or type your own
        portrait = new ImagePicker(assets, null, null, "No portrait yet");
        List<JComponent> rows = new ArrayList<>(List.of(
                new JLabel("Name"), name,
                new JLabel("Class"), characterClass,
                new JLabel("Level"), level));
        rows.addAll(List.of(stats.rows()));
        rows.addAll(List.of(new JLabel("Portrait"), portrait));
        component = MapDialogs.form(rows.toArray(new JComponent[0]));
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public void show(PartyMember member) {
        name.setText(member.name());
        characterClass.setSelectedItem(member.characterClass());
        level.setValue(member.level());
        stats.show(member.stats());
        portrait.set(member.portrait());
    }

    @Override
    public PartyMember read() {
        String typedClass = String.valueOf(characterClass.getEditor().getItem());
        return new PartyMember(name.getText(), typedClass, StatFields.value(level), stats.read(), portrait.reference());
    }

    @Override
    public PartyMember create(List<PartyMember> party) {
        return PartyMember.create("Adventurer " + (party.size() + 1));
    }

    @Override
    public String label(PartyMember member) {
        String shownName = member.name().isEmpty() ? "(no name)" : member.name();
        return shownName + "  -  " + member.characterClass() + ", level " + member.level();
    }

    @Override
    public String problem(List<PartyMember> party) {
        for (PartyMember member : party) {
            if (member.name().isEmpty()) {
                return "Every party member needs a name.";
            }
        }
        return null;
    }
}
