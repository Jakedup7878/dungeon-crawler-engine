package crawler.battle;

import crawler.core.Item;
import crawler.script.Script;

import java.util.List;
import java.util.Random;

/**
 * Carries out an item's script: what its commands do to fighters, in a battle or outside one.
 * Everything that happens is added to a list of {@link BattleEvent}s.
 */
public final class Effects implements Script.Host {
    private final Item item;
    private final Fighter user;
    private final Fighter target;
    private final List<Fighter> allies;
    private final List<Fighter> foes;
    private final Random random;
    private final List<BattleEvent> events;
    private final int damage;
    private final int round;
    private int gold;
    private boolean changed;

    /**
     * @param target who it's aimed at, or null
     * @param foes   the enemies; empty outside battle
     * @param damage the damage of the hit that set it off (0 for none)
     * @param round  the battle's round, or 0 outside battle
     */
    public Effects(Item item, Fighter user, Fighter target, List<Fighter> allies, List<Fighter> foes,
                   Random random, List<BattleEvent> events, int damage, int round) {
        this.item = item;
        this.user = user;
        this.target = target;
        this.allies = allies;
        this.foes = foes;
        this.random = random;
        this.events = events;
        this.damage = damage;
        this.round = round;
    }

    public Effects run(String trigger) {
        item.script().run(trigger, this);
        return this;
    }

    /** Gold the script gave the party. */
    public int gold() {
        return gold;
    }

    /** Whether it did anything, apart from saying things. */
    public boolean changed() {
        return changed;
    }

    @Override
    public int number(String name) {
        switch (name) {
            case "damage":
                return damage;
            case "round":
                return round;
            case "battle":
                return round > 0 ? 1 : 0;
            default:
                break;
        }
        Fighter f = name.startsWith("user.") ? user : target;
        if (f == null) {
            return 0;
        }
        return switch (name.substring(name.indexOf('.') + 1)) {
            case "hp" -> f.hp();
            case "maxhp" -> f.stats().hp();
            case "mp" -> f.mp();
            case "maxmp" -> f.stats().mp();
            case "attack" -> f.stats().attack();
            case "defense" -> f.stats().defense();
            case "speed" -> f.stats().speed();
            case "level" -> f.level();
            case "tier" -> f.tierRank();
            case "alive" -> f.alive() ? 1 : 0;
            case "poisoned" -> f.poisoned() ? 1 : 0;
            case "stunned" -> f.stunned() ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public String name(String who) {
        Fighter f = who.equals("user") ? user : target;
        return who.equals("item") ? item.displayName() : f == null ? "everyone" : f.name();
    }

    @Override
    public int random(int bound) {
        return random.nextInt(Math.max(1, bound));
    }

    @Override
    public void say(String text) {
        events.add(new BattleEvent.Note(text));
    }

    @Override
    public void command(String command, String who, String stat, int[] n) {
        if (command.equals("gold")) {
            if (n[0] > 0) {
                gold = Math.min(1_000_000, gold + n[0]);
                changed = true;
            }
            return;
        }
        for (Fighter f : who(who)) {
            apply(command, f, stat, n);
        }
    }

    private List<Fighter> who(String who) {
        return switch (who) {
            case "user" -> List.of(user);
            case "target" -> target == null ? List.of() : List.of(target);
            case "party" -> allies;
            case "enemies" -> foes;
            case "random_enemy" -> pick(foes);
            default -> pick(allies);
        };
    }

    private List<Fighter> pick(List<Fighter> from) {
        List<Fighter> living = from.stream().filter(Fighter::alive).toList();
        return living.isEmpty() ? List.of() : List.of(living.get(random.nextInt(living.size())));
    }

    private void apply(String command, Fighter f, String stat, int[] n) {
        if (command.equals("revive") == f.alive()) {
            return; // only revive works on the fallen, and only on them
        }
        boolean inBattle = round > 0;
        switch (command) {
            case "heal" -> {
                int healed = f.heal(n[0]);
                if (healed > 0) {
                    record(new BattleEvent.Heal(f, healed, false));
                }
            }
            case "mana" -> {
                int restored = f.restoreMp(n[0]);
                if (restored > 0) {
                    record(new BattleEvent.Heal(f, restored, true));
                }
            }
            case "revive" -> record(new BattleEvent.Revived(f, f.revive(n[0])));
            case "damage" -> {
                // Outside battle nothing can kill: there is no game over on the item screen.
                int amount = Math.min(n[0], inBattle ? f.hp() : f.hp() - 1);
                if (amount > 0) {
                    f.takeDamage(amount);
                    record(new BattleEvent.Hurt(f, amount, "", !f.alive()));
                }
            }
            case "cure" -> {
                if (f.poisoned() || f.stunned()) {
                    f.cure();
                    record(new BattleEvent.Note(f.name() + " is cured."));
                }
            }
            case "poison" -> {
                if (inBattle && n[0] > 0 && n[1] > 0) {
                    f.poison(n[0], n[1]);
                    record(new BattleEvent.Note(f.name() + " is poisoned!"));
                }
            }
            case "stun" -> {
                if (inBattle && n[0] > 0) {
                    f.stun(n[0]);
                    record(new BattleEvent.Note(f.name() + " is stunned!"));
                }
            }
            case "boost" -> {
                if (inBattle && n[0] != 0 && n[1] > 0) {
                    f.boost(stat, n[0], n[1]);
                    record(new BattleEvent.Note(f.name() + "'s " + stat + (n[0] > 0 ? " rises!" : " falls!")));
                }
            }
            default -> throw new IllegalStateException(command);
        }
    }

    private void record(BattleEvent event) {
        changed = true;
        events.add(event);
    }
}
