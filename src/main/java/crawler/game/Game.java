package crawler.game;

import crawler.battle.Battle;
import crawler.battle.BattleEvent;
import crawler.battle.Effects;
import crawler.battle.Fighter;
import crawler.core.Destination;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.EnemyType;
import crawler.core.Exploration;
import crawler.core.Floors;
import crawler.core.GameContent;
import crawler.core.Inventory;
import crawler.core.Item;
import crawler.core.ItemKind;
import crawler.core.ItemStack;
import crawler.core.ItemTarget;
import crawler.core.MapObject;
import crawler.core.MoveResult;
import crawler.core.MovementSystem;
import crawler.core.ObjectType;
import crawler.core.Party;
import crawler.core.PartyMember;
import crawler.core.Tier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The running game: the current floor, the party, and the systems that act on them.
 * Contains no Swing code, so it can be driven by tests or a different front end.
 *
 * <p>Objects on the map come alive here: walking into a chest opens it, walking into an
 * NPC talks to it, and stepping onto an event's cell sets it off. Each produces a
 * {@link GameMessage} the player reads before carrying on. Walking around can also start
 * a {@link Battle}: random encounters set per map, or fixed battles set on events.
 */
public final class Game {
    /** What's remembered about one floor. */
    private record FloorState(Exploration exploration, Set<Integer> openedChests, Set<Integer> triggeredEvents) {
        FloorState(DungeonMap map) {
            this(new Exploration(map), new HashSet<>(), new HashSet<>());
        }
    }

    private final Floors floors;
    private final Map<String, FloorState> states = new HashMap<>();
    private DungeonMap map;
    private FloorState floor;
    private int travels;
    private final GameContent content;
    private final Random random;
    private final Party party;
    private final MovementSystem movement;
    private final MessageLog log = new MessageLog(50);
    private final Deque<GameMessage> messages = new ArrayDeque<>();
    private final List<Fighter> heroes = new ArrayList<>();
    private final Inventory inventory = new Inventory();
    private Battle battle;
    private boolean gameOver;
    private int gold;
    private int steps;

    /** A game with no project: the built-in classes and a starter party, and no enemies. */
    public Game(DungeonMap map) {
        this(map, GameContent.EMPTY, new Random());
    }

    /**
     * @param random the dice for encounters and battles; pass a seeded one for repeatable tests
     */
    public Game(DungeonMap map, GameContent content, Random random) {
        this(Floors.single(map), content, random);
    }

    /** A game of several floors, joined by stairs. */
    public Game(Floors floors, GameContent content, Random random) {
        this.floors = floors;
        this.map = floors.first();
        this.floor = new FloorState(map);
        states.put(floors.start(), floor);
        this.content = content;
        this.random = random;
        this.party = new Party(map.startX(), map.startY(), map.startFacing());
        this.movement = new MovementSystem(map, party);
        this.gold = content.settings().startingGold();
        give(content.settings().startingItems());
        replaceParty(content.startingParty());
        floor.exploration().visit(party.x(), party.y());
        movement.addListener(this::onMovement);
        log.add("You enter " + map.name() + ".");
    }

    private void onMovement(MoveResult result) {
        switch (result.outcome()) {
            case MOVED -> {
                steps++;
                floor.exploration().visit(result.toX(), result.toY());
                if (result.edge() == EdgeType.DOOR) {
                    log.add("You pass through a door.");
                }
                MapObject here = map.object(result.toX(), result.toY());
                if (here != null && here.type() == ObjectType.EVENT) {
                    trigger(here);
                }
                if (here != null && here.type() == ObjectType.STAIRS && travel(here)) {
                    return; // nothing jumps out on the stairs
                }
                if (battle == null && map.encounters().active() && random.nextInt(100) < map.encounters().rate()) {
                    startRandomBattle();
                }
            }
            case BLOCKED -> {
                MapObject ahead = map.object(result.fromX() + result.travel().dx, result.fromY() + result.travel().dy);
                if (result.edge().passable && ahead != null && ahead.solid()) {
                    use(ahead);
                } else {
                    log.add(result.edge() == EdgeType.WALL ? "Ouch! There's a wall there." : "You can't go that way.");
                }
            }
            case TURNED -> { }
        }
    }

    /** Opens or talks to whatever stands right in front of the party. False if there is nothing. */
    public boolean interact() {
        Direction facing = party.facing();
        MapObject ahead = map.object(party.x() + facing.dx, party.y() + facing.dy);
        MapObject here = map.object(party.x(), party.y());
        boolean nothingAhead = ahead == null || !ahead.solid() || !map.edge(party.x(), party.y(), facing).passable;
        if (nothingAhead && here != null && here.type() == ObjectType.STAIRS) {
            return travel(here); // having just arrived on them, say
        }
        if (ahead == null || !ahead.solid() || !map.edge(party.x(), party.y(), facing).passable) {
            log.add("There's nothing there.");
            return false;
        }
        use(ahead);
        return true;
    }

    private void use(MapObject object) {
        switch (object.type()) {
            case CHEST -> {
                if (floor.openedChests().add(index(object))) {
                    log.add("You open the " + object.displayName().toLowerCase() + ".");
                    List<String> found = new ArrayList<>();
                    if (!object.text().isEmpty()) {
                        found.add(object.text());
                    }
                    found.addAll(give(object.items()));
                    messages.add(new GameMessage(object.displayName(),
                            found.isEmpty() ? "It's empty." : "Inside you find:\n" + String.join("\n", found), object));
                } else {
                    messages.add(new GameMessage(object.displayName(), "It's empty now.", object));
                }
            }
            case NPC -> {
                log.add("You talk to " + object.displayName() + ".");
                messages.add(new GameMessage(object.displayName(),
                        object.text().isEmpty() ? "..." : object.text(), object));
            }
            case EVENT -> trigger(object);
            case STAIRS -> travel(object);
        }
    }

    /** Takes stairs to where they lead. False if that's nowhere. */
    private boolean travel(MapObject stairs) {
        Destination to = stairs.destination();
        DungeonMap next = to.isSet() ? floors.get(to.map()) : null;
        if (next == null || Floors.arrivalProblem(next, to) != null) {
            log.add("The way is blocked.");
            return false;
        }
        boolean sameFloor = next == map;
        map = next;
        floor = states.computeIfAbsent(to.map(), k -> new FloorState(next));
        movement.setMap(next);
        party.place(to.atStart() ? next.startX() : to.x(), to.atStart() ? next.startY() : to.y(),
                to.atStart() ? next.startFacing() : to.facing());
        floor.exploration().visit(party.x(), party.y());
        travels++;
        if (!sameFloor) {
            log.add((stairs.name().isEmpty() ? "You go " + (to.up() ? "up" : "down") : stairs.name())
                    + ": " + next.name() + ".");
        }
        if (!stairs.text().isEmpty()) {
            messages.add(new GameMessage(stairs.name(), stairs.text(), stairs));
        }
        return true;
    }

    /** Counts the times the party has taken stairs, so the screen knows when it's somewhere else. */
    public int travels() {
        return travels;
    }

    public Floors floors() {
        return floors;
    }

    private void trigger(MapObject event) {
        boolean first = floor.triggeredEvents().add(index(event));
        if (!first && event.once()) {
            return;
        }
        if (!event.name().isEmpty()) {
            log.add(event.name() + ".");
        }
        if (!event.text().isEmpty()) {
            messages.add(new GameMessage(event.name(), event.text(), event));
        }
        if (!event.battle().isEmpty()) {
            List<EnemyType> enemies = new ArrayList<>();
            for (String id : event.battle()) {
                List<EnemyType> choices = content.enemiesFor(id);
                if (!choices.isEmpty()) { // a tier means any one of its enemies
                    enemies.add(choices.size() == 1 ? choices.get(0) : pick(choices));
                }
            }
            startBattle(enemies, false);
        }
    }

    private int index(MapObject object) {
        return object.y() * map.width() + object.x();
    }

    // ------------------------------------------------------------ battles

    private void startRandomBattle() {
        Set<EnemyType> unique = new LinkedHashSet<>();
        for (String id : map.encounters().enemies()) {
            unique.addAll(content.enemiesFor(id));
        }
        List<EnemyType> table = List.copyOf(unique);
        if (table.isEmpty()) {
            return;
        }
        int min = map.encounters().minGroup();
        int count = min + random.nextInt(map.encounters().maxGroup() - min + 1);
        List<EnemyType> group = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            group.add(pick(table));
        }
        startBattle(group, true);
    }

    /** One of these enemies: the heavier its tier's weight, the likelier. */
    private EnemyType pick(List<EnemyType> from) {
        int total = from.stream().mapToInt(this::weight).sum();
        int roll = random.nextInt(total);
        for (EnemyType type : from) {
            roll -= weight(type);
            if (roll < 0) {
                return type;
            }
        }
        throw new AssertionError();
    }

    private int weight(EnemyType type) {
        Tier tier = content.tier(type.tier());
        return tier == null ? Tier.DEFAULT_WEIGHT : tier.weight();
    }

    /**
     * Starts a battle against these enemies. Several of the same kind are told apart by
     * letters: "Blue Slime A", "Blue Slime B". Does nothing for an empty list.
     */
    public void startBattle(List<EnemyType> enemies, boolean canFlee) {
        if (enemies.isEmpty() || battle != null || gameOver) {
            return;
        }
        Map<String, Integer> total = new HashMap<>();
        for (EnemyType type : enemies) {
            total.merge(type.id(), 1, Integer::sum);
        }
        Map<String, Integer> seen = new HashMap<>();
        List<Fighter> fighters = new ArrayList<>();
        for (EnemyType type : enemies) {
            String name = type.name().isEmpty() ? type.id() : type.name();
            if (total.get(type.id()) > 1) {
                int n = seen.merge(type.id(), 1, Integer::sum);
                name += " " + (char) ('A' + n - 1);
            }
            Tier tier = content.tier(type.tier());
            fighters.add(Fighter.enemy(type, name, tier, content.tierRank(type.tier())));
            canFlee &= tier == null || tier.canFlee();
        }
        battle = new Battle(heroes, fighters, canFlee, random, inventory);
        log.add(fighters.size() == 1 ? fighters.get(0).name() + " attacks!" : "Monsters attack!");
    }

    /** The battle being fought, or null. */
    public Battle battle() {
        return battle;
    }

    /** Called once the battle screen has shown how the battle ended. */
    public void endBattle() {
        if (battle == null || battle.outcome() == Battle.Outcome.ONGOING) {
            return;
        }
        gold += battle.goldFromItems();
        switch (battle.outcome()) {
            case VICTORY -> {
                gold += battle.goldWon();
                log.add("Victory! You find " + battle.goldWon() + " gold.");
            }
            case FLED -> log.add("You got away.");
            case DEFEAT -> {
                gameOver = true;
                log.add("The party has fallen.");
            }
            case ONGOING -> { }
        }
        battle = null;
    }

    /** True once the whole party has fallen. */
    public boolean isGameOver() {
        return gameOver;
    }

    // ------------------------------------------------------------ the party

    /** The party members, in marching order, with their current HP and experience. */
    public List<Fighter> heroes() {
        return List.copyOf(heroes);
    }

    /** Starts over with a new party at full health (after the player creates their own). */
    public void replaceParty(List<PartyMember> members) {
        if (members.isEmpty()) {
            throw new IllegalArgumentException("A party needs at least one member");
        }
        heroes.clear();
        for (PartyMember member : members) {
            Fighter hero = Fighter.hero(member, content.classOf(member));
            for (String id : member.equipment()) {
                Item item = content.item(id);
                if (item != null && hero.canEquip(item)) {
                    hero.equip(item);
                }
            }
            hero.restore();
            heroes.add(hero);
        }
    }

    // ------------------------------------------------------------ items

    /** What the party carries. */
    public Inventory inventory() {
        return inventory;
    }

    /** Adds items to what the party carries; returns a line for each, such as "Potion x2". */
    private List<String> give(List<ItemStack> stacks) {
        List<String> lines = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Item item = content.item(stack.id());
            if (item != null) {
                int kept = inventory.add(item, stack.count());
                String line = item.displayName() + (stack.count() > 1 ? " x" + stack.count() : "");
                lines.add(kept < stack.count() ? line + " (you can't carry them all)" : line);
            }
        }
        return lines;
    }

    /** Puts an item from the inventory on a party member; what they wore goes back. False if they can't. */
    public boolean equip(Fighter hero, Item item) {
        Item worn = hero.equipped(item.kind());
        if (battle != null || !hero.canEquip(item)
                || (worn != null && !worn.id().equals(item.id()) && inventory.count(worn) >= ItemStack.MAX)
                || !inventory.remove(item)) {
            return false;
        }
        hero.equip(item);
        if (worn != null) {
            inventory.add(worn, 1);
        }
        return true;
    }

    /** Takes off what a party member wears in a slot. False if there's nothing, or no room for it. */
    public boolean unequip(Fighter hero, ItemKind slot) {
        Item worn = hero.equipped(slot);
        if (battle != null || worn == null || inventory.add(worn, 1) == 0) {
            return false;
        }
        hero.unequip(slot);
        return true;
    }

    /**
     * Uses an item outside battle.
     *
     * @param target the party member it's for (or, for items that act on their user, who uses it);
     *               ignored if it's for the whole party
     * @return what happened, to show the player
     */
    public String useItem(Item item, Fighter target) {
        if (battle != null || gameOver || inventory.count(item) == 0) {
            return "You can't use that now.";
        }
        if (!item.usable()) {
            return "There's no way to use that.";
        }
        if (item.target().battleOnly()) {
            return "That can only be used in battle.";
        }
        boolean party = item.target() == ItemTarget.PARTY;
        if (!party && target.alive() == (item.target() == ItemTarget.FALLEN)) {
            return "It would have no effect.";
        }
        // Whoever is first in line and able uses it, unless it's for its user.
        Fighter user = item.target() == ItemTarget.SELF ? target
                : heroes.stream().filter(Fighter::alive).findFirst().orElse(heroes.get(0));
        List<BattleEvent> events = new ArrayList<>();
        Effects effects = new Effects(item, user, party ? null : target, heroes, List.of(), random, events, 0, 0).run("use");
        gold += effects.gold();
        if (!effects.changed() && events.isEmpty()) {
            return "It would have no effect.";
        }
        if (item.consumed() && effects.changed()) { // one that only says something isn't used up
            inventory.remove(item);
        }
        List<String> lines = new ArrayList<>();
        for (BattleEvent event : events) {
            if (event instanceof BattleEvent.Heal h) {
                lines.add(h.target().name() + " recovers " + h.amount() + (h.mana() ? " MP." : " HP."));
            } else if (event instanceof BattleEvent.Hurt h) {
                lines.add(h.target().name() + " loses " + h.amount() + " HP.");
            } else if (event instanceof BattleEvent.Revived r) {
                lines.add(r.target().name() + " is back on their feet.");
            } else if (event instanceof BattleEvent.Note n) {
                lines.add(n.text());
            }
        }
        if (effects.gold() > 0) {
            lines.add("You gain " + effects.gold() + " gold.");
        }
        return String.join(" ", lines);
    }

    public int gold() {
        return gold;
    }

    public GameContent content() {
        return content;
    }

    // ------------------------------------------------------------ messages

    /** The message the player is reading, or null. */
    public GameMessage message() {
        return messages.peekFirst();
    }

    /** Done reading the current message; the next one (if any) shows. */
    public void dismissMessage() {
        messages.pollFirst();
    }

    /** Whether the party has opened this chest. */
    public boolean isOpened(MapObject object) {
        return object.type() == ObjectType.CHEST && floor.openedChests().contains(index(object));
    }

    public DungeonMap map() {
        return map;
    }

    public Party party() {
        return party;
    }

    public MovementSystem movement() {
        return movement;
    }

    public Exploration exploration() {
        return floor.exploration();
    }

    public MessageLog log() {
        return log;
    }

    public int steps() {
        return steps;
    }
}
