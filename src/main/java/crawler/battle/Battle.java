package crawler.battle;

import crawler.core.Inventory;
import crawler.core.Item;
import crawler.core.ItemTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A turn-based battle, Wizardry style. Each round the player gives every living party
 * member an order; then everyone acts, fastest first, and the enemies attack whoever
 * they like. The result is a list of {@link BattleEvent}s for the screen to show.
 *
 * <p>No Swing here, and the dice come from a {@link Random} you pass in, so a test with
 * a fixed seed gets the same battle every time.
 */
public final class Battle {
    /** Chance of missing, in percent. */
    static final int MISS_CHANCE = 5;

    /** What a party member does this round. */
    public enum Command { ATTACK, DEFEND, ITEM, FLEE }

    /** A party member's order: the command, who it's aimed at (or null), and for ITEM, which item. */
    public record Order(Fighter actor, Command command, Fighter target, Item item) {
        public Order(Fighter actor, Command command, Fighter target) {
            this(actor, command, target, null);
        }
    }

    public enum Outcome { ONGOING, VICTORY, DEFEAT, FLED }

    private final List<Fighter> party;
    private final List<Fighter> enemies;
    private final boolean canFlee;
    private final Random random;
    private final Inventory inventory;
    private Outcome outcome = Outcome.ONGOING;
    private int round;
    private int goldWon;
    private int goldFromItems;

    /**
     * @param party   the party members, in marching order (fallen members stay in the list)
     * @param canFlee false for battles that can't be run from
     */
    public Battle(List<Fighter> party, List<Fighter> enemies, boolean canFlee, Random random) {
        this(party, enemies, canFlee, random, new Inventory());
    }

    /** @param inventory what the party can use; used-up items are taken from it */
    public Battle(List<Fighter> party, List<Fighter> enemies, boolean canFlee, Random random, Inventory inventory) {
        if (enemies.isEmpty()) {
            throw new IllegalArgumentException("A battle needs at least one enemy");
        }
        this.party = List.copyOf(party);
        this.enemies = List.copyOf(enemies);
        this.canFlee = canFlee;
        this.random = random;
        this.inventory = inventory;
    }

    public List<Fighter> party() {
        return party;
    }

    public List<Fighter> enemies() {
        return enemies;
    }

    public boolean canFlee() {
        return canFlee;
    }

    public Outcome outcome() {
        return outcome;
    }

    /** Rounds played so far. */
    public int round() {
        return round;
    }

    /** Gold the party won (once the outcome is VICTORY). */
    public int goldWon() {
        return goldWon;
    }

    /** Gold that items' scripts gave the party, however the battle ended. */
    public int goldFromItems() {
        return goldFromItems;
    }

    public List<Fighter> livingParty() {
        return party.stream().filter(Fighter::alive).toList();
    }

    public List<Fighter> livingEnemies() {
        return enemies.stream().filter(Fighter::alive).toList();
    }

    /**
     * Plays one round. Party members without an order attack a random enemy.
     *
     * @return what happened, in order; the last event is the outcome if the battle ended
     */
    public List<BattleEvent> playRound(List<Order> orders) {
        if (outcome != Outcome.ONGOING) {
            throw new IllegalStateException("The battle is over");
        }
        round++;
        List<BattleEvent> events = new ArrayList<>();
        Map<Fighter, Order> orderOf = new HashMap<>();
        for (Order order : orders) {
            orderOf.put(order.actor(), order);
        }

        if (round == 1) {
            triggerAll("battle", events);
        }
        triggerAll("round", events);
        checkForEnd(events);

        // Defending lasts the whole round, so it happens before anyone acts.
        for (Fighter f : party) {
            f.setDefending(false);
        }
        for (Fighter f : livingParty()) {
            Order order = orderOf.get(f);
            if (order != null && order.command() == Command.DEFEND
                    && !f.stunned() && outcome == Outcome.ONGOING) {
                f.setDefending(true);
                events.add(new BattleEvent.Defend(f));
            }
        }

        // Fastest first; a small random bonus breaks ties and keeps it lively.
        Map<Fighter, Integer> initiative = new HashMap<>();
        List<Fighter> everyone = new ArrayList<>(livingParty());
        everyone.addAll(livingEnemies());
        for (Fighter f : everyone) {
            initiative.put(f, f.stats().speed() + random.nextInt(4));
        }
        everyone.sort(Comparator.comparing((Fighter f) -> initiative.get(f)).reversed());

        for (Fighter actor : everyone) {
            if (outcome != Outcome.ONGOING || !actor.alive()) {
                continue;
            }
            if (actor.loseTurn()) {
                events.add(new BattleEvent.Note(actor.name() + " is stunned and can't move!"));
                continue;
            }
            if (actor.isEnemy()) {
                List<Fighter> targets = livingParty();
                attack(actor, targets.get(random.nextInt(targets.size())), events);
            } else {
                Order order = orderOf.get(actor);
                Command command = order == null ? Command.ATTACK : order.command();
                switch (command) {
                    case ATTACK -> {
                        Fighter target = order == null ? null : order.target();
                        if (target == null || !target.alive() || !target.isEnemy()) {
                            List<Fighter> targets = livingEnemies();
                            target = targets.get(random.nextInt(targets.size()));
                        }
                        attack(actor, target, events);
                    }
                    case DEFEND -> { }
                    case ITEM -> useItem(order, events);
                    case FLEE -> flee(actor, events);
                }
            }
            if (outcome == Outcome.ONGOING) {
                checkForEnd(events);
            }
        }
        if (outcome == Outcome.ONGOING) {
            endOfRound(events);
        }
        if (outcome != Outcome.ONGOING) {
            party.forEach(Fighter::clearConditions);
        }
        return events;
    }

    /** Poison does its work, and boosts wear down. */
    private void endOfRound(List<BattleEvent> events) {
        List<Fighter> everyone = new ArrayList<>(party);
        everyone.addAll(enemies);
        for (Fighter f : everyone) {
            int damage = f.tickPoison();
            if (damage > 0) {
                events.add(new BattleEvent.Hurt(f, damage, "poison", !f.alive()));
            }
            f.tickBoosts();
        }
        checkForEnd(events);
    }

    /** Runs a script block of everything the party wears. */
    private void triggerAll(String trigger, List<BattleEvent> events) {
        for (Fighter hero : livingParty()) {
            for (Item item : hero.equipment()) {
                if (hero.alive()) {
                    trigger(trigger, item, hero, hero, 0, events);
                }
            }
        }
    }

    private Effects trigger(String trigger, Item item, Fighter user, Fighter target, int damage, List<BattleEvent> events) {
        Effects effects = new Effects(item, user, target, party, enemies, random, events, damage, round).run(trigger);
        goldFromItems += effects.gold();
        return effects;
    }

    private void useItem(Order order, List<BattleEvent> events) {
        Item item = order.item();
        Fighter actor = order.actor();
        if (item == null || !item.usable() || inventory.count(item) == 0) {
            events.add(new BattleEvent.Note(actor.name() + " has nothing to use!"));
            return;
        }
        Fighter target = switch (item.target()) {
            case SELF -> actor;
            case ALLY, FALLEN -> order.target();
            case ENEMY -> {
                Fighter chosen = order.target();
                List<Fighter> living = livingEnemies();
                yield chosen != null && chosen.isEnemy() && chosen.alive() ? chosen : living.get(random.nextInt(living.size()));
            }
            case PARTY, ENEMIES -> null;
        };
        events.add(new BattleEvent.ItemUsed(actor, item, target));
        if (item.target().picksAlly()
                && (target == null || target.isEnemy() || target.alive() != (item.target() == ItemTarget.ALLY))) {
            events.add(new BattleEvent.Note("It has no effect."));
            return; // and it isn't used up
        }
        int before = events.size();
        Effects effects = trigger("use", item, actor, target, 0, events);
        if (item.consumed()) {
            inventory.remove(item);
        }
        if (!effects.changed() && events.size() == before) {
            events.add(new BattleEvent.Note("Nothing happens."));
        }
    }

    private void attack(Fighter attacker, Fighter target, List<BattleEvent> events) {
        if (random.nextInt(100) < MISS_CHANCE) {
            events.add(new BattleEvent.Attack(attacker, target, 0, true, false));
            return;
        }
        int damage = damage(attacker, target, 85 + random.nextInt(31));
        target.takeDamage(damage);
        events.add(new BattleEvent.Attack(attacker, target, damage, false, !target.alive()));
        for (Item item : attacker.equipment()) {
            if (attacker.alive()) {
                trigger("hit", item, attacker, target, damage, events);
            }
        }
        for (Item item : target.equipment()) {
            if (target.alive()) {
                trigger("hurt", item, target, attacker, damage, events);
            }
        }
    }

    /**
     * Damage for one hit: attack times a roll (85 to 115 percent), minus half the target's
     * defense, at least 1; halved (still at least 1) if the target is defending.
     */
    static int damage(Fighter attacker, Fighter target, int rollPercent) {
        int damage = Math.max(1, attacker.stats().attack() * rollPercent / 100 - target.stats().defense() / 2);
        return target.defending() ? Math.max(1, damage / 2) : damage;
    }

    private void flee(Fighter actor, List<BattleEvent> events) {
        if (!canFlee) {
            events.add(new BattleEvent.CantFlee(actor));
            return;
        }
        if (random.nextInt(100) < fleeChance()) {
            outcome = Outcome.FLED;
            events.add(new BattleEvent.Fled(actor));
        } else {
            events.add(new BattleEvent.FleeFailed(actor));
        }
    }

    /** 50% plus 5% for each point the party is faster on average, from 10% to 90%. */
    int fleeChance() {
        double partySpeed = livingParty().stream().mapToInt(f -> f.stats().speed()).average().orElse(0);
        double enemySpeed = livingEnemies().stream().mapToInt(f -> f.stats().speed()).average().orElse(0);
        return (int) Math.max(10, Math.min(90, 50 + 5 * (partySpeed - enemySpeed)));
    }

    private void checkForEnd(List<BattleEvent> events) {
        if (livingParty().isEmpty()) {
            outcome = Outcome.DEFEAT;
            events.add(new BattleEvent.Defeat());
        } else if (livingEnemies().isEmpty()) {
            outcome = Outcome.VICTORY;
            int exp = enemies.stream().mapToInt(Fighter::expReward).sum();
            goldWon = enemies.stream().mapToInt(Fighter::goldReward).sum();
            List<Fighter> survivors = livingParty();
            int expEach = survivors.isEmpty() ? 0 : (exp + survivors.size() - 1) / survivors.size();
            List<BattleEvent.LevelUp> levelUps = new ArrayList<>();
            for (Fighter hero : survivors) {
                if (hero.gainExp(expEach) > 0) {
                    levelUps.add(new BattleEvent.LevelUp(hero, hero.level()));
                }
            }
            events.add(new BattleEvent.Victory(expEach, goldWon, levelUps));
        }
    }
}
