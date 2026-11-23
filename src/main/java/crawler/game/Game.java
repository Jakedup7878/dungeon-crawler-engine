package crawler.game;

import crawler.battle.Battle;
import crawler.battle.Fighter;
import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.EnemyType;
import crawler.core.Exploration;
import crawler.core.GameContent;
import crawler.core.MapObject;
import crawler.core.MoveResult;
import crawler.core.MovementSystem;
import crawler.core.ObjectType;
import crawler.core.Party;
import crawler.core.PartyMember;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
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
    private final DungeonMap map;
    private final GameContent content;
    private final Random random;
    private final Party party;
    private final MovementSystem movement;
    private final Exploration exploration;
    private final MessageLog log = new MessageLog(50);
    private final Deque<GameMessage> messages = new ArrayDeque<>();
    private final Set<Integer> openedChests = new HashSet<>();
    private final Set<Integer> triggeredEvents = new HashSet<>();
    private final List<Fighter> heroes = new ArrayList<>();
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
        this.map = map;
        this.content = content;
        this.random = random;
        this.party = new Party(map.startX(), map.startY(), map.startFacing());
        this.movement = new MovementSystem(map, party);
        this.exploration = new Exploration(map);
        this.gold = content.settings().startingGold();
        replaceParty(content.startingParty());
        exploration.visit(party.x(), party.y());
        movement.addListener(this::onMovement);
        log.add("You enter " + map.name() + ".");
    }

    private void onMovement(MoveResult result) {
        switch (result.outcome()) {
            case MOVED -> {
                steps++;
                exploration.visit(result.toX(), result.toY());
                if (result.edge() == EdgeType.DOOR) {
                    log.add("You pass through a door.");
                }
                MapObject here = map.object(result.toX(), result.toY());
                if (here != null && here.type() == ObjectType.EVENT) {
                    trigger(here);
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
                if (openedChests.add(index(object))) {
                    log.add("You open the " + object.displayName().toLowerCase() + ".");
                    messages.add(new GameMessage(object.displayName(),
                            object.text().isEmpty() ? "It's empty." : "Inside you find:\n" + object.text(), object));
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
        }
    }

    private void trigger(MapObject event) {
        boolean first = triggeredEvents.add(index(event));
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
                EnemyType type = content.enemy(id);
                if (type != null) {
                    enemies.add(type);
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
        List<EnemyType> table = new ArrayList<>();
        for (String id : map.encounters().enemies()) {
            EnemyType type = content.enemy(id);
            if (type != null) {
                table.add(type);
            }
        }
        if (table.isEmpty()) {
            return;
        }
        int min = map.encounters().minGroup();
        int count = min + random.nextInt(map.encounters().maxGroup() - min + 1);
        List<EnemyType> group = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            group.add(table.get(random.nextInt(table.size())));
        }
        startBattle(group, true);
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
            fighters.add(Fighter.enemy(type, name));
        }
        battle = new Battle(heroes, fighters, canFlee, random);
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
            heroes.add(Fighter.hero(member, content.classOf(member)));
        }
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
        return object.type() == ObjectType.CHEST && openedChests.contains(index(object));
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
        return exploration;
    }

    public MessageLog log() {
        return log;
    }

    public int steps() {
        return steps;
    }
}
