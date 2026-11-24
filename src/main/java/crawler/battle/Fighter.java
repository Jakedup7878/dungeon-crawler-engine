package crawler.battle;

import crawler.core.CharacterClass;
import crawler.core.EnemyType;
import crawler.core.Item;
import crawler.core.ItemKind;
import crawler.core.PartyMember;
import crawler.core.SpriteRef;
import crawler.core.Stats;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Someone taking part in battles: a party member, who keeps their HP, experience and level
 * from one battle to the next, or an enemy.
 */
public final class Fighter {
    public static final int MAX_LEVEL = 99;

    private final String name;
    private final boolean enemy;
    private final String characterClass;
    private final String attackAnimation;
    private final SpriteRef sprite;
    private final String portrait;
    private final Stats growth;
    private final String enemyId;
    private final int expReward;
    private final int goldReward;
    private int level;
    private int exp;
    private Stats stats;
    private int hp;
    private int mp;
    private boolean defending;
    private CharacterClass heroClass;
    private final Map<ItemKind, Item> equipment = new EnumMap<>(ItemKind.class);
    private int poisonDamage;
    private int poisonRounds;
    private int stunTurns;
    private final List<Boost> boosts = new ArrayList<>();

    /** A change to a stat that wears off at the end of a battle, if not before. */
    private static final class Boost {
        final String stat;
        final int amount;
        int rounds;

        Boost(String stat, int amount, int rounds) {
            this.stat = stat;
            this.amount = amount;
            this.rounds = rounds;
        }
    }

    private Fighter(String name, boolean enemy, String characterClass, String attackAnimation, SpriteRef sprite,
                    String portrait, Stats growth, String enemyId, int expReward, int goldReward, int level, Stats stats) {
        this.name = name;
        this.enemy = enemy;
        this.characterClass = characterClass;
        this.attackAnimation = attackAnimation;
        this.sprite = sprite;
        this.portrait = portrait;
        this.growth = growth;
        this.enemyId = enemyId;
        this.expReward = expReward;
        this.goldReward = goldReward;
        this.level = level;
        this.stats = stats;
        this.hp = stats.hp();
        this.mp = stats.mp();
    }

    /**
     * A party member at full health.
     *
     * @param characterClass their class, for growth, attack animation and a default portrait; may be null
     */
    public static Fighter hero(PartyMember member, CharacterClass characterClass) {
        Stats growth = characterClass == null ? CharacterClass.DEFAULT_GROWTH : characterClass.growth();
        String animation = characterClass == null ? null : characterClass.attackAnimation();
        String portrait = member.portrait() != null ? member.portrait()
                : characterClass == null ? null : characterClass.portrait();
        Fighter hero = new Fighter(member.name(), false, member.characterClass(), animation, null, portrait, growth,
                null, 0, 0, member.level(), member.stats());
        hero.heroClass = characterClass;
        return hero;
    }

    /** An enemy at full health, shown under {@code name} (such as "Blue Slime B"). */
    public static Fighter enemy(EnemyType type, String name) {
        return new Fighter(name, true, "", type.attackAnimation(), type.sprite(), null, Stats.ZERO,
                type.id(), type.exp(), type.gold(), 1, type.stats());
    }

    public String name() {
        return name;
    }

    public boolean isEnemy() {
        return enemy;
    }

    /** The class text of a party member ("" for enemies). */
    public String characterClass() {
        return characterClass;
    }

    /** Id of the attack animation (the weapon's, if it has one), or null for the built-in one. */
    public String attackAnimation() {
        Item weapon = equipment.get(ItemKind.WEAPON);
        return weapon != null && weapon.animation() != null ? weapon.animation() : attackAnimation;
    }

    /** An enemy's picture, or null for the built-in look. */
    public SpriteRef sprite() {
        return sprite;
    }

    /** A party member's portrait, or null. */
    public String portrait() {
        return portrait;
    }

    /** The enemy type's id (null for party members). */
    public String enemyId() {
        return enemyId;
    }

    public int expReward() {
        return expReward;
    }

    public int goldReward() {
        return goldReward;
    }

    public int level() {
        return level;
    }

    public int exp() {
        return exp;
    }

    /** Maximum HP and MP, and the other numbers, counting equipment and boosts. */
    public Stats stats() {
        if (equipment.isEmpty() && boosts.isEmpty()) {
            return stats;
        }
        int hpMore = 0;
        int mpMore = 0;
        int attack = 0;
        int defense = 0;
        int speed = 0;
        for (Boost b : boosts) {
            switch (b.stat) {
                case "attack" -> attack += b.amount;
                case "defense" -> defense += b.amount;
                default -> speed += b.amount;
            }
        }
        for (Item item : equipment.values()) {
            hpMore += item.bonus().hp();
            mpMore += item.bonus().mp();
            attack += item.bonus().attack();
            defense += item.bonus().defense();
            speed += item.bonus().speed();
        }
        return new Stats(clamp(stats.hp() + hpMore, 1, Stats.MAX_POINTS), clamp(stats.mp() + mpMore, 0, Stats.MAX_POINTS),
                clamp(stats.attack() + attack, 0, Stats.MAX_RATING), clamp(stats.defense() + defense, 0, Stats.MAX_RATING),
                clamp(stats.speed() + speed, 0, Stats.MAX_RATING));
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    // ------------------------------------------------------------ equipment

    /** What they wear in a slot, or null. */
    public Item equipped(ItemKind slot) {
        return equipment.get(slot);
    }

    /** Everything they wear. */
    public List<Item> equipment() {
        return new ArrayList<>(equipment.values());
    }

    public boolean canEquip(Item item) {
        return !enemy && item.equippableBy(characterClass, heroClass);
    }

    /** Puts an item on (check {@link #canEquip} first); returns what was in that slot, or null. */
    public Item equip(Item item) {
        if (!canEquip(item)) {
            throw new IllegalArgumentException(name + " can't equip " + item.displayName());
        }
        Item before = equipment.put(item.kind(), item);
        keepWithinLimits();
        return before;
    }

    /** Takes off what's in a slot; returns it, or null. */
    public Item unequip(ItemKind slot) {
        Item before = equipment.remove(slot);
        keepWithinLimits();
        return before;
    }

    private void keepWithinLimits() {
        hp = Math.min(hp, stats().hp());
        mp = Math.min(mp, stats().mp());
    }

    // ------------------------------------------------------------ what items do

    /** Gives back HP; returns how much. */
    int heal(int amount) {
        int before = hp;
        hp = Math.max(hp, Math.min(stats().hp(), hp + Math.max(0, amount)));
        return hp - before;
    }

    /** Gives back MP, or takes it if negative; returns the change. */
    int restoreMp(int amount) {
        int before = mp;
        mp = clamp(mp + amount, 0, Math.max(mp, stats().mp()));
        return mp - before;
    }

    /** Brings a fallen fighter back, with at least 1 HP; returns their HP. */
    int revive(int amount) {
        clearConditions();
        hp = clamp(amount, 1, stats().hp());
        return hp;
    }

    void poison(int damage, int rounds) {
        poisonDamage = Math.max(poisonDamage, damage);
        poisonRounds = Math.max(poisonRounds, rounds);
    }

    public boolean poisoned() {
        return poisonRounds > 0;
    }

    /** Poison at the end of a round: the damage done (0 if not poisoned). */
    int tickPoison() {
        if (poisonRounds <= 0 || !alive()) {
            return 0;
        }
        int amount = Math.min(hp, poisonDamage);
        takeDamage(amount);
        if (--poisonRounds == 0) {
            poisonDamage = 0;
        }
        return amount;
    }

    void stun(int turns) {
        stunTurns = Math.max(stunTurns, turns);
    }

    public boolean stunned() {
        return stunTurns > 0;
    }

    /** Uses up one stunned turn; false if they can act. */
    boolean loseTurn() {
        if (stunTurns <= 0) {
            return false;
        }
        stunTurns--;
        return true;
    }

    void cure() {
        poisonDamage = 0;
        poisonRounds = 0;
        stunTurns = 0;
    }

    void boost(String stat, int amount, int rounds) {
        boosts.add(new Boost(stat, amount, rounds));
    }

    /** Boosts wear down by a round. */
    void tickBoosts() {
        for (Boost b : boosts) {
            b.rounds--;
        }
        boosts.removeIf(b -> b.rounds <= 0);
    }

    /** Back to normal: no poison, stun or boosts. */
    void clearConditions() {
        cure();
        boosts.clear();
    }

    public int hp() {
        return hp;
    }

    public int mp() {
        return mp;
    }

    public boolean alive() {
        return hp > 0;
    }

    public boolean defending() {
        return defending;
    }

    void setDefending(boolean defending) {
        this.defending = defending;
    }

    void takeDamage(int amount) {
        hp = Math.max(0, hp - amount);
    }

    /** Total experience needed to reach the next level: 20, 60, 120, 200... */
    public int expForNextLevel() {
        return 10 * level * (level + 1);
    }

    /**
     * Adds experience. Each level gained adds the class's growth to the stats, and the
     * HP and MP growth to the current HP and MP as well.
     *
     * @return how many levels were gained
     */
    int gainExp(int amount) {
        exp += amount;
        int gained = 0;
        while (level < MAX_LEVEL && exp >= expForNextLevel()) {
            level++;
            stats = stats.plus(growth);
            hp = Math.min(stats().hp(), hp + growth.hp());
            mp = Math.min(stats().mp(), mp + growth.mp());
            gained++;
        }
        return gained;
    }

    /** Back to full HP and MP. */
    public void restore() {
        hp = stats().hp();
        mp = stats().mp();
    }

    @Override
    public String toString() {
        return name + " (" + hp + "/" + stats().hp() + " HP)";
    }
}
