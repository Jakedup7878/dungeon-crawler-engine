package crawler.core;

import crawler.script.Script;

import java.util.List;

/**
 * A kind of item: a potion, a weapon, armor and so on. What it does beyond its bonuses is up
 * to its {@link Script}.
 *
 * @param bonus      added to the wearer's stats while equipped (numbers may be negative)
 * @param target     who it's used on, for items with an {@code on use} block
 * @param consumed   whether using it uses it up
 * @param classes    ids or names of the only classes that can equip it; empty for everyone
 * @param icon       its picture, or null
 * @param animation  weapons only: the attack animation's id, or null for the wearer's own
 * @param scriptText the script as written; "" for none
 * @param script     the script, parsed
 */
public record Item(String id, String name, ItemKind kind, String description, Stats bonus, ItemTarget target,
                   boolean consumed, List<String> classes, SpriteRef icon, String animation, String scriptText,
                   Script script) {
    public static final int MAX_BONUS = Stats.MAX_RATING;

    public Item {
        id = id == null ? "" : id.strip();
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        classes = classes == null ? List.of() : List.copyOf(classes);
        animation = kind != ItemKind.WEAPON || animation == null || animation.isBlank() ? null : animation.strip();
        scriptText = scriptText == null ? "" : scriptText.replace("\r", "").strip();
        bonus = kind.equippable ? bonus : Stats.ZERO;
    }

    /** An item with its script parsed; IllegalArgumentException if the script has a mistake. */
    public Item(String id, String name, ItemKind kind, String description, Stats bonus, ItemTarget target,
                boolean consumed, List<String> classes, SpriteRef icon, String animation, String scriptText) {
        this(id, name, kind, description, bonus, target, consumed, classes, icon, animation, scriptText,
                Script.parse(scriptText));
    }

    public static Item create(String id, String name) {
        return new Item(id, name, ItemKind.CONSUMABLE, "", Stats.ZERO, ItemTarget.ALLY, true, List.of(), null, null,
                "on use\n  heal target 20");
    }

    public String displayName() {
        return name.isEmpty() ? id : name;
    }

    /** Whether the player can use it, rather than just wear it or have it. */
    public boolean usable() {
        return !kind.equippable && script.has("use");
    }

    /** Whether a class (as written on the party member, and as found; may be null) may equip it. */
    public boolean equippableBy(String classText, CharacterClass characterClass) {
        if (!kind.equippable) {
            return false;
        }
        if (classes.isEmpty()) {
            return true;
        }
        for (String c : classes) {
            if (c.equalsIgnoreCase(classText) || (characterClass != null && characterClass.matches(c))) {
                return true;
            }
        }
        return false;
    }

    public Item withIcon(SpriteRef newIcon) {
        return new Item(id, name, kind, description, bonus, target, consumed, classes, newIcon, animation, scriptText, script);
    }

    /** "+4 Attack, -1 Speed", or "" with no bonuses. */
    public String bonusText() {
        StringBuilder out = new StringBuilder();
        int[] values = {bonus.hp(), bonus.mp(), bonus.attack(), bonus.defense(), bonus.speed()};
        String[] names = {"HP", "MP", "Attack", "Defense", "Speed"};
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0) {
                out.append(out.isEmpty() ? "" : ", ").append(values[i] > 0 ? "+" : "").append(values[i])
                        .append(' ').append(names[i]);
            }
        }
        return out.toString();
    }
}
