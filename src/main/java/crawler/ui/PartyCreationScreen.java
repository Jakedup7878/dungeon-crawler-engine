package crawler.ui;

import crawler.core.CharacterClass;
import crawler.core.GameContent;
import crawler.core.PartyMember;
import crawler.core.Stats;
import crawler.render.AssetSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shown when a new game starts, if the project lets players make their own party: they
 * add members one at a time (a name and a class), or take the ready-made party.
 * Keyboard only: arrows to choose, Enter to confirm, Esc to go back, and typing for names.
 */
final class PartyCreationScreen {
    private static final int MAX_NAME = 12;
    private static final String[] SUGGESTED_NAMES = {"Aldric", "Mira", "Tobin", "Wren", "Sable", "Corin"};

    private final GameContent content;
    private final List<CharacterClass> classes;
    private final AssetSource assets;
    private final Consumer<List<PartyMember>> done;
    private final List<PartyMember> members = new ArrayList<>();
    private boolean naming;
    private final StringBuilder name = new StringBuilder();
    private int classIndex;
    private int menuIndex;

    /** @param done called with the finished party */
    PartyCreationScreen(GameContent content, AssetSource assets, Consumer<List<PartyMember>> done) {
        this.content = content;
        this.classes = content.availableClasses();
        this.assets = assets;
        this.done = done;
    }

    private int maxSize() {
        return content.settings().partySize();
    }

    /** The menu as it stands: what can be done depends on how full the party is. */
    private List<String> options() {
        List<String> options = new ArrayList<>();
        if (members.size() < maxSize()) {
            options.add("Add a member");
        }
        if (!members.isEmpty()) {
            options.add("Remove the last member");
        }
        if (!content.party().isEmpty()) {
            options.add("Use the ready-made party");
        }
        if (!members.isEmpty()) {
            options.add("Begin the adventure");
        }
        return options;
    }

    void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (naming) {
            switch (key) {
                case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> classIndex = (classIndex + classes.size() - 1) % classes.size();
                case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> classIndex = (classIndex + 1) % classes.size();
                case KeyEvent.VK_BACK_SPACE -> {
                    if (!name.isEmpty()) {
                        name.setLength(name.length() - 1);
                    }
                }
                case KeyEvent.VK_ESCAPE -> naming = false;
                case KeyEvent.VK_ENTER -> addMember();
                default -> { }
            }
            return;
        }
        List<String> options = options();
        switch (key) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> menuIndex = (menuIndex + options.size() - 1) % options.size();
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> menuIndex = (menuIndex + 1) % options.size();
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> act(options.get(Math.min(menuIndex, options.size() - 1)));
            default -> { }
        }
    }

    /** Letters typed while naming a member. */
    void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        if (naming && name.length() < MAX_NAME && (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'')) {
            name.append(c);
        }
    }

    private void act(String option) {
        switch (option) {
            case "Add a member" -> {
                naming = true;
                name.setLength(0);
            }
            case "Remove the last member" -> members.remove(members.size() - 1);
            case "Use the ready-made party" -> done.accept(content.party());
            case "Begin the adventure" -> done.accept(List.copyOf(members));
            default -> { }
        }
        menuIndex = Math.min(menuIndex, options().size() - 1);
    }

    private void addMember() {
        String chosen = name.toString().strip();
        if (chosen.isEmpty()) {
            chosen = SUGGESTED_NAMES[members.size() % SUGGESTED_NAMES.length];
        }
        members.add(GameContent.newMember(chosen, classes.get(classIndex)));
        naming = false;
        List<String> options = options();
        menuIndex = options.indexOf(members.size() < maxSize() ? "Add a member" : "Begin the adventure");
    }

    // ------------------------------------------------------------ drawing

    /** Draws the whole screen on the logical canvas. */
    void draw(Graphics2D g, int width, int height, double clock) {
        g.setColor(Hud.BACKGROUND);
        g.fillRect(0, 0, width, height);
        g.setFont(Hud.BIG_FONT);
        g.setColor(Hud.HIGHLIGHT);
        Hud.centered(g, "Create Your Party", width / 2, 64);
        g.setFont(Hud.FONT);
        g.setColor(Hud.DIM);
        Hud.centered(g, "Up to " + maxSize() + " adventurers. Choose a name and a class for each.", width / 2, 94);

        Rectangle list = new Rectangle(40, 130, 420, 450);
        Hud.panel(g, list);
        g.setFont(Hud.FONT);
        g.setColor(Hud.HIGHLIGHT);
        g.drawString("Party  " + members.size() + " / " + maxSize(), list.x + 16, list.y + 28);
        int y = list.y + 64;
        if (members.isEmpty()) {
            g.setColor(Hud.DIM);
            g.drawString("Nobody yet.", list.x + 16, y);
        }
        for (PartyMember member : members) {
            g.setColor(Hud.TEXT);
            g.drawString(Hud.fit(g, member.name(), 160), list.x + 16, y);
            g.setColor(Hud.DIM);
            g.drawString(member.characterClass(), list.x + 190, y);
            g.drawString("HP " + member.stats().hp(), list.x + 320, y);
            y += 30;
        }

        Rectangle side = new Rectangle(500, 130, 420, 450);
        Hud.panel(g, side);
        if (naming) {
            drawNaming(g, side, clock);
        } else {
            drawMenu(g, side);
        }
    }

    private void drawMenu(Graphics2D g, Rectangle side) {
        List<String> options = options();
        g.setFont(Hud.FONT);
        int y = side.y + 40;
        for (int i = 0; i < options.size(); i++) {
            boolean selected = i == menuIndex;
            g.setColor(selected ? Hud.HIGHLIGHT : Hud.TEXT);
            g.drawString((selected ? "> " : "  ") + options.get(i), side.x + 20, y);
            y += 32;
        }
        g.setFont(Hud.SMALL_FONT);
        g.setColor(Hud.DIM);
        g.drawString("Up / Down to choose, Enter to confirm", side.x + 20, side.y + side.height - 20);
    }

    private void drawNaming(Graphics2D g, Rectangle side, double clock) {
        CharacterClass chosen = classes.get(classIndex);
        int x = side.x + 20;
        g.setFont(Hud.FONT);
        g.setColor(Hud.HIGHLIGHT);
        g.drawString("New member", x, side.y + 36);
        g.setColor(Hud.TEXT);
        String cursor = (int) (clock * 2) % 2 == 0 ? "_" : " ";
        String shownName = name.isEmpty() ? SUGGESTED_NAMES[members.size() % SUGGESTED_NAMES.length] : name.toString();
        g.drawString("Name:   ", x, side.y + 76);
        g.setColor(name.isEmpty() ? Hud.DIM : Hud.TEXT);
        g.drawString(shownName + cursor, x + 80, side.y + 76);
        g.setColor(Hud.TEXT);
        g.drawString("Class:  < " + chosen.name() + " >", x, side.y + 110);

        Stats s = chosen.base();
        Stats grow = chosen.growth();
        String[][] rows = {
                {"HP", Integer.toString(s.hp()), "+" + grow.hp()},
                {"MP", Integer.toString(s.mp()), "+" + grow.mp()},
                {"Attack", Integer.toString(s.attack()), "+" + grow.attack()},
                {"Defense", Integer.toString(s.defense()), "+" + grow.defense()},
                {"Speed", Integer.toString(s.speed()), "+" + grow.speed()}};
        g.setFont(Hud.SMALL_FONT);
        g.setColor(Hud.DIM);
        g.drawString("start   per level", x + 110, side.y + 150);
        int y = side.y + 172;
        for (String[] row : rows) {
            g.setColor(Hud.TEXT);
            g.drawString(row[0], x, y);
            g.drawString(String.format("%5s", row[1]), x + 100, y);
            g.setColor(Hud.GOOD);
            g.drawString(String.format("%6s", row[2]), x + 160, y);
            y += 22;
        }
        if (chosen.portrait() != null) {
            BufferedImage portrait = assets.image(chosen.portrait());
            if (portrait != null) {
                Rectangle frame = new Rectangle(side.x + side.width - 140, side.y + 150, 120, 120);
                g.setColor(new Color(22, 26, 40));
                g.fill(frame);
                Hud.drawFitted(g, portrait, frame, 4);
            }
        }
        g.setColor(Hud.DIM);
        g.drawString("Type a name. Left / Right: class.", x, side.y + side.height - 40);
        g.drawString("Enter: add.  Esc: cancel.", x, side.y + side.height - 20);
    }
}
