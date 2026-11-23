package crawler.editor;

import crawler.core.DungeonMap;
import crawler.core.Encounters;
import crawler.core.EnemyType;
import crawler.core.GameProject;
import crawler.core.ProjectSettings;
import crawler.render.ObjectLooks;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Map Settings (music, random battles) and Project Settings (game-wide options). */
final class SettingsDialogs {
    private SettingsDialogs() {
    }

    /** A map's music and random battles. */
    record MapSettings(String music, Encounters encounters) {
    }

    /** The game's name and its settings. */
    record ProjectChoices(String name, ProjectSettings settings) {
    }

    /**
     * Music and random battles for one map; null if cancelled.
     *
     * @param enemies the project's enemies, to tick; empty when there is no project
     */
    static MapSettings mapSettings(Component parent, DungeonMap map, AssetStore assets, List<EnemyType> enemies) {
        SoundPicker music = new SoundPicker(assets, map.music(), "No music");
        Encounters current = map.encounters();
        JSpinner rate = StatFields.spinner(0, 100);
        rate.setValue(current.rate());
        JSpinner min = StatFields.spinner(1, Encounters.MAX_GROUP);
        min.setValue(current.minGroup());
        JSpinner max = StatFields.spinner(1, Encounters.MAX_GROUP);
        max.setValue(current.maxGroup());

        // One tick box per enemy; ids the map names but the project doesn't know stay, marked missing.
        Map<String, JCheckBox> boxes = new LinkedHashMap<>();
        for (EnemyType enemy : enemies) {
            String label = (enemy.name().isEmpty() ? enemy.id() : enemy.name()) + "  [" + enemy.id() + "]";
            boxes.put(enemy.id(), new JCheckBox(label, current.enemies().contains(enemy.id())));
        }
        for (String id : current.enemies()) {
            boxes.computeIfAbsent(id, k -> new JCheckBox(k + "  (missing: not in Project > Enemies)", true));
        }
        JComponent enemyField;
        JTextField typedIds = null;
        if (boxes.isEmpty()) {
            typedIds = new JTextField(String.join(", ", current.enemies()), 28);
            enemyField = typedIds;
        } else {
            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            boxes.values().forEach(list::add);
            JScrollPane scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(330, Math.min(180, 26 * boxes.size() + 8)));
            enemyField = scroll;
        }

        JPanel form = MapDialogs.form(
                new JLabel("Music"), music,
                new JLabel(), ObjectDialog.hint("WAV or MIDI. It loops while the party is on this floor."),
                new JLabel("Battle chance"), rate,
                new JLabel(), ObjectDialog.hint("Percent chance of a random battle at each step (0 = none; 5 to 10 feels classic)."),
                new JLabel("Fewest enemies"), min,
                new JLabel("Most enemies"), max,
                new JLabel("Enemies here"), enemyField,
                new JLabel(), ObjectDialog.hint(boxes.isEmpty()
                        ? "Enemy ids, comma separated. Make enemies in Project > Enemies to pick them from a list."
                        : "Which enemies can turn up on this floor."));
        int answer = JOptionPane.showConfirmDialog(parent, form, "Map Settings - " + map.name(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        List<String> chosen = new ArrayList<>();
        if (typedIds != null) {
            chosen.addAll(Encounters.parseIds(typedIds.getText()));
        } else {
            boxes.forEach((id, box) -> {
                if (box.isSelected()) {
                    chosen.add(id);
                }
            });
        }
        int low = StatFields.value(min);
        int high = Math.max(low, StatFields.value(max));
        return new MapSettings(music.reference(), new Encounters(chosen, StatFields.value(rate), low, high));
    }

    /** The project's name and game-wide settings; null if cancelled. */
    static ProjectChoices projectSettings(Component parent, GameProject project, AssetStore assets) {
        ProjectSettings s = project.settings();
        JTextField name = new JTextField(project.name(), 24);
        JCheckBox creation = new JCheckBox("Players can create their own party when a new game starts", s.partyCreation());
        JSpinner size = StatFields.spinner(1, ProjectSettings.MAX_PARTY);
        size.setValue(s.partySize());
        JSpinner gold = StatFields.spinner(0, ProjectSettings.MAX_GOLD);
        gold.setValue(s.startingGold());
        ImagePicker npc = new ImagePicker(assets, null, ObjectLooks.defaultNpc().image(0), "Built-in spinning blue top", true);
        npc.setSprite(s.npcSprite());
        ImagePicker chest = new ImagePicker(assets, null, ObjectLooks.defaultChest(false).image(0), "Built-in chest", true);
        chest.setSprite(s.chestSprite());
        ImagePicker chestOpen = new ImagePicker(assets, null, ObjectLooks.defaultChest(true).image(0),
                "Built-in open chest (or the closed picture, if you chose one)", true);
        chestOpen.setSprite(s.chestOpenSprite());
        SoundPicker battleMusic = new SoundPicker(assets, s.battleMusic(), "None: the map's music keeps playing");
        SoundPicker victoryMusic = new SoundPicker(assets, s.victoryMusic(), "None");

        JPanel form = MapDialogs.form(
                new JLabel("Game name"), name,
                new JLabel("Party"), creation,
                new JLabel("Party size"), size,
                new JLabel(), ObjectDialog.hint("The most members a party can have (1 to 6)."),
                new JLabel("Starting gold"), gold,
                new JLabel("NPC look"), npc,
                new JLabel(), ObjectDialog.hint("For every NPC without a picture of its own."),
                new JLabel("Chest look"), chest,
                new JLabel("Open chest"), chestOpen,
                new JLabel("Battle music"), battleMusic,
                new JLabel("Victory music"), victoryMusic,
                new JLabel(), ObjectDialog.hint("Played once after winning a battle."));
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Dimension preferred = form.getPreferredSize();
        scroll.setPreferredSize(new Dimension(preferred.width + 24, Math.min(preferred.height + 8, 620)));
        int answer = JOptionPane.showConfirmDialog(parent, scroll, "Project Settings - " + project.name(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }
        String newName = name.getText().strip().isEmpty() ? project.name() : name.getText().strip();
        return new ProjectChoices(newName, new ProjectSettings(npc.sprite(), chest.sprite(), chestOpen.sprite(),
                battleMusic.reference(), victoryMusic.reference(), creation.isSelected(),
                StatFields.value(size), StatFields.value(gold)));
    }
}
