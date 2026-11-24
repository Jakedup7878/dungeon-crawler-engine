package crawler.ui;

import crawler.battle.Fighter;
import crawler.core.Item;
import crawler.core.ItemKind;
import crawler.core.ItemTarget;
import crawler.core.Stats;
import crawler.game.Game;
import crawler.render.ObjectLooks;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * What the party carries and wears, drawn over the view while exploring. The Items page uses
 * and equips things; the Equipment page (Tab) shows each member's gear and takes it off.
 */
final class InventoryScreen {
    private enum Page { ITEMS, MEMBER, EQUIPMENT }

    private static final int ROWS = 11;
    static final List<ItemKind> SLOTS =
            List.of(ItemKind.WEAPON, ItemKind.SHIELD, ItemKind.HEAD, ItemKind.BODY, ItemKind.ACCESSORY);

    private final Game game;
    private final ObjectLooks looks;
    private Page page = Page.ITEMS;
    private int itemIndex;
    private int memberIndex;
    private int slotIndex;
    private Item chosen;
    private String note = "";
    private boolean finished;

    InventoryScreen(Game game, ObjectLooks looks) {
        this.game = game;
        this.looks = looks;
    }

    boolean isFinished() {
        return finished;
    }

    /** The member being chosen, to highlight in the party list; or null. */
    Fighter selectedHero() {
        return page == Page.ITEMS ? null : game.heroes().get(memberIndex);
    }

    // ------------------------------------------------------------ keys

    void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        boolean confirm = key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE;
        boolean back = key == KeyEvent.VK_ESCAPE || key == KeyEvent.VK_BACK_SPACE;
        int vertical = key == KeyEvent.VK_UP || key == KeyEvent.VK_W ? -1
                : key == KeyEvent.VK_DOWN || key == KeyEvent.VK_S ? 1 : 0;
        int sideways = key == KeyEvent.VK_LEFT || key == KeyEvent.VK_A ? -1
                : key == KeyEvent.VK_RIGHT || key == KeyEvent.VK_D ? 1 : 0;
        if (key == KeyEvent.VK_I) {
            finished = true;
        } else if (page == Page.MEMBER) {
            if (back) {
                page = Page.ITEMS;
            } else if (confirm) {
                apply(game.heroes().get(memberIndex));
            } else {
                memberIndex = wrap(memberIndex + vertical + sideways, game.heroes().size());
            }
        } else if (back) {
            finished = true;
        } else if (key == KeyEvent.VK_TAB) {
            page = page == Page.ITEMS ? Page.EQUIPMENT : Page.ITEMS;
            note = "";
        } else if (page == Page.EQUIPMENT) {
            memberIndex = wrap(memberIndex + sideways, game.heroes().size());
            slotIndex = wrap(slotIndex + vertical, SLOTS.size());
            if (confirm) {
                Fighter hero = game.heroes().get(memberIndex);
                Item worn = hero.equipped(SLOTS.get(slotIndex));
                if (worn != null) {
                    note = game.unequip(hero, worn.kind()) ? hero.name() + " takes off the " + worn.displayName() + "."
                            : "You can't carry any more of those.";
                } else {
                    note = "Nothing there. Equip things from the Items page (Tab).";
                }
            }
        } else {
            List<Item> items = game.inventory().items();
            itemIndex = items.isEmpty() ? 0 : wrap(itemIndex + vertical, items.size());
            if (confirm && !items.isEmpty()) {
                choose(items.get(itemIndex));
            }
        }
    }

    private static int wrap(int i, int size) {
        return Math.floorMod(i, size);
    }

    private void choose(Item item) {
        note = "";
        boolean forParty = item.usable()
                && (item.target() == ItemTarget.PARTY || item.target().battleOnly());
        if (forParty) {
            note = game.useItem(item, null);
            log();
        } else if (item.kind().equippable || item.usable()) {
            chosen = item;
            page = Page.MEMBER;
        } else {
            note = "There's no way to use that.";
        }
        keepIndex();
    }

    /** Use on, or equip to, the member chosen. Anything that can be worn is worn. */
    private void apply(Fighter hero) {
        if (chosen.kind().equippable) {
            if (!hero.canEquip(chosen)) {
                note = hero.name() + " can't equip that.";
                return;
            }
            note = game.equip(hero, chosen) ? hero.name() + " equips the " + chosen.displayName() + "."
                    : "There's no room to carry what's taken off.";
            page = Page.ITEMS;
        } else {
            note = game.useItem(chosen, hero);
            if (game.inventory().count(chosen) == 0) {
                page = Page.ITEMS; // otherwise stay, to use another
            }
        }
        log();
        keepIndex();
    }

    private void log() {
        if (!note.isEmpty()) {
            game.log().add(note);
        }
    }

    private void keepIndex() {
        itemIndex = Math.max(0, Math.min(itemIndex, game.inventory().items().size() - 1));
    }

    // ------------------------------------------------------------ drawing

    void draw(Graphics2D g, Rectangle view, double clock) {
        Rectangle box = new Rectangle(view.x + 20, view.y + 20, view.width - 40, view.height - 40);
        Hud.panel(g, box);
        g.setFont(Hud.FONT);
        g.setColor(page == Page.EQUIPMENT ? Hud.DIM : Hud.HIGHLIGHT);
        g.drawString("Items", box.x + 18, box.y + 28);
        g.setColor(page == Page.EQUIPMENT ? Hud.HIGHLIGHT : Hud.DIM);
        g.drawString("Equipment", box.x + 100, box.y + 28);
        if (page == Page.EQUIPMENT) {
            drawEquipment(g, box);
        } else {
            drawItems(g, box, clock);
        }
        g.setFont(Hud.FONT);
        g.setColor(Hud.TEXT);
        List<String> lines = Hud.wrap(note, g.getFontMetrics(), box.width - 36);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(Hud.fit(g, lines.get(i), box.width - 36), box.x + 18, box.y + box.height - 52 + i * 19);
        }
        g.setFont(Hud.SMALL_FONT);
        g.setColor(Hud.DIM);
        String hint = switch (page) {
            case ITEMS -> "Enter: use / equip   Tab: equipment   Esc: close";
            case MEMBER -> "Up/Down: choose who   Enter: confirm   Esc: back";
            case EQUIPMENT -> "Left/Right: member   Enter: take off   Tab: items   Esc: close";
        };
        g.drawString(hint, box.x + 18, box.y + box.height - 12);
    }

    private void drawItems(Graphics2D g, Rectangle box, double clock) {
        List<Item> items = game.inventory().items();
        int listWidth = 250;
        int x = box.x + 18;
        int y = box.y + 60;
        g.setFont(Hud.SMALL_FONT);
        if (items.isEmpty()) {
            g.setColor(Hud.DIM);
            g.drawString("You aren't carrying anything.", x, y);
            return;
        }
        int first = Math.max(0, Math.min(itemIndex - ROWS / 2, items.size() - ROWS));
        for (int i = first; i < Math.min(items.size(), first + ROWS); i++) {
            Item item = items.get(i);
            g.setColor(i == itemIndex ? Hud.HIGHLIGHT : Hud.TEXT);
            g.drawString(Hud.fit(g, (i == itemIndex ? "> " : "  ") + item.displayName(), listWidth - 44), x, y);
            String count = "x" + game.inventory().count(item);
            g.drawString(count, x + listWidth - 8 - g.getFontMetrics().stringWidth(count), y);
            y += 24;
        }
        int dx = x + listWidth + 18;
        int width = box.x + box.width - 18 - dx;
        if (page == Page.MEMBER) {
            drawMembers(g, dx, box.y + 60, width);
        } else {
            drawDetails(g, items.get(itemIndex), dx, box.y + 60, width, clock);
        }
    }

    private void drawDetails(Graphics2D g, Item item, int x, int y, int width, double clock) {
        if (item.icon() != null) {
            var sprite = looks.custom(item.icon());
            if (sprite != null) {
                Hud.drawFitted(g, sprite.image(clock), new Rectangle(x, y - 14, 72, 72), 6);
                y += 80;
            }
        }
        g.setFont(Hud.FONT);
        g.setColor(Hud.HIGHLIGHT);
        g.drawString(Hud.fit(g, item.displayName(), width), x, y);
        g.setFont(Hud.SMALL_FONT);
        List<String> lines = new ArrayList<>();
        lines.add(item.kind().displayName);
        if (!item.bonusText().isEmpty()) {
            lines.addAll(Hud.wrap(item.bonusText(), g.getFontMetrics(), width));
        }
        if (!item.classes().isEmpty()) {
            lines.addAll(Hud.wrap("For: " + String.join(", ", item.classes()), g.getFontMetrics(), width));
        }
        if (!item.description().isEmpty()) {
            lines.add("");
            lines.addAll(Hud.wrap(item.description(), g.getFontMetrics(), width));
        }
        g.setColor(Hud.TEXT);
        for (int i = 0; i < Math.min(11, lines.size()); i++) {
            y += 20;
            g.drawString(lines.get(i), x, y);
        }
    }

    /** Who to use it on or equip it to, with what would change. */
    private void drawMembers(Graphics2D g, int x, int y, int width) {
        g.setFont(Hud.FONT);
        g.setColor(Hud.HIGHLIGHT);
        g.drawString(chosen.kind().equippable ? "Who equips it?" : "Use it on whom?", x, y);
        g.setFont(Hud.SMALL_FONT);
        y += 26;
        List<Fighter> heroes = game.heroes();
        for (int i = 0; i < heroes.size(); i++) {
            Fighter hero = heroes.get(i);
            boolean can = !chosen.kind().equippable || hero.canEquip(chosen);
            g.setColor(!can ? Hud.DIM : i == memberIndex ? Hud.HIGHLIGHT : Hud.TEXT);
            g.drawString(Hud.fit(g, (i == memberIndex ? "> " : "  ") + hero.name(), width - 90), x, y);
            String right;
            if (!chosen.kind().equippable) {
                right = hero.hp() + "/" + hero.stats().hp();
            } else if (!can) {
                right = "can't";
            } else {
                Item worn = hero.equipped(chosen.kind());
                right = worn == null ? "" : Hud.fit(g, worn.displayName(), 120);
            }
            g.drawString(right, x + width - g.getFontMetrics().stringWidth(right), y);
            y += 22;
        }
        if (chosen.kind().equippable) {
            Item worn = heroes.get(memberIndex).equipped(chosen.kind());
            Stats before = worn == null ? Stats.ZERO : worn.bonus();
            Stats after = chosen.bonus();
            int[][] changes = {{before.hp(), after.hp()}, {before.mp(), after.mp()}, {before.attack(), after.attack()},
                    {before.defense(), after.defense()}, {before.speed(), after.speed()}};
            String[] names = {"HP", "MP", "Attack", "Defense", "Speed"};
            y += 10;
            for (int i = 0; i < names.length; i++) {
                int change = changes[i][1] - changes[i][0];
                if (change != 0) {
                    g.setColor(change > 0 ? Hud.GOOD : Hud.BAD);
                    g.drawString(names[i] + (change > 0 ? " +" : " ") + change, x, y);
                    y += 20;
                }
            }
        }
    }

    private void drawEquipment(Graphics2D g, Rectangle box) {
        Fighter hero = game.heroes().get(memberIndex);
        int x = box.x + 18;
        int y = box.y + 66;
        g.setFont(Hud.FONT);
        g.setColor(Hud.HIGHLIGHT);
        g.drawString(Hud.fit(g, "< " + hero.name() + " >  " + hero.characterClass() + " L" + hero.level(), box.width - 36), x, y);
        g.setFont(Hud.SMALL_FONT);
        for (int i = 0; i < SLOTS.size(); i++) {
            y += 28;
            Item worn = hero.equipped(SLOTS.get(i));
            g.setColor(i == slotIndex ? Hud.HIGHLIGHT : Hud.TEXT);
            g.drawString((i == slotIndex ? "> " : "  ") + SLOTS.get(i).displayName, x, y);
            g.setColor(worn == null ? Hud.DIM : i == slotIndex ? Hud.HIGHLIGHT : Hud.TEXT);
            g.drawString(worn == null ? "-" : Hud.fit(g, worn.displayName()
                    + (worn.bonusText().isEmpty() ? "" : "  (" + worn.bonusText() + ")"), box.width - 170), x + 130, y);
        }
        Stats s = hero.stats();
        y += 44;
        g.setColor(Hud.TEXT);
        g.drawString("HP " + hero.hp() + "/" + s.hp() + "   MP " + hero.mp() + "/" + s.mp(), x, y);
        g.drawString("Attack " + s.attack() + "   Defense " + s.defense() + "   Speed " + s.speed(), x, y + 22);
        g.drawString("Experience " + hero.exp() + "   next level at " + hero.expForNextLevel(), x, y + 44);
    }
}
