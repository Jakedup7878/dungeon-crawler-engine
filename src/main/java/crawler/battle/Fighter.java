package crawler.battle;

import crawler.core.CharacterClass;
import crawler.core.EnemyType;
import crawler.core.PartyMember;
import crawler.core.SpriteRef;
import crawler.core.Stats;

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
        return new Fighter(member.name(), false, member.characterClass(), animation, null, portrait, growth,
                null, 0, 0, member.level(), member.stats());
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

    /** Id of the attack animation, or null for the built-in one. */
    public String attackAnimation() {
        return attackAnimation;
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

    /** Maximum HP and MP, and the other numbers. */
    public Stats stats() {
        return stats;
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
            hp = Math.min(stats.hp(), hp + growth.hp());
            mp = Math.min(stats.mp(), mp + growth.mp());
            gained++;
        }
        return gained;
    }

    /** Back to full HP and MP. */
    public void restore() {
        hp = stats.hp();
        mp = stats.mp();
    }

    @Override
    public String toString() {
        return name + " (" + hp + "/" + stats.hp() + " HP)";
    }
}
