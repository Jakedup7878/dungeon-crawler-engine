package crawler.game;

import crawler.core.Direction;
import crawler.core.DungeonMap;
import crawler.core.EdgeType;
import crawler.core.Exploration;
import crawler.core.MapObject;
import crawler.core.MoveResult;
import crawler.core.MovementSystem;
import crawler.core.ObjectType;
import crawler.core.Party;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The running game: the current floor, the party, and the systems that act on them.
 * Contains no Swing code, so it can be driven by tests or a different front end.
 *
 * <p>Objects on the map come alive here: walking into a chest opens it, walking into an
 * NPC talks to it, and stepping onto an event's cell sets it off. Each produces a
 * {@link GameMessage} the player reads before carrying on.
 */
public final class Game {
    private final DungeonMap map;
    private final Party party;
    private final MovementSystem movement;
    private final Exploration exploration;
    private final MessageLog log = new MessageLog(50);
    private final Deque<GameMessage> messages = new ArrayDeque<>();
    private final Set<Integer> openedChests = new HashSet<>();
    private final Set<Integer> triggeredEvents = new HashSet<>();
    private int steps;

    public Game(DungeonMap map) {
        this.map = map;
        this.party = new Party(map.startX(), map.startY(), map.startFacing());
        this.movement = new MovementSystem(map, party);
        this.exploration = new Exploration(map);
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
    }

    private int index(MapObject object) {
        return object.y() * map.width() + object.x();
    }

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
