package crawler.script;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small script an author writes for an item. It's split into blocks, each starting with
 * {@code on} and the moment it runs:
 *
 * <pre>
 * # A sword that sometimes burns
 * on hit
 *   if chance(30)
 *     say "{target} is wreathed in flame!"
 *     damage target 4 + user.level
 *   end
 * </pre>
 *
 * Scripts can only do what their commands do: they can't reach files or the network, and
 * a script that runs away is stopped ({@link #MAX_STEPS}). Mistakes are found when the script
 * is parsed, with the line they're on. The full language is in docs/item-scripts.md.
 */
public final class Script {
    /** The moments a block can be for. */
    public static final Set<String> TRIGGERS = ordered("use", "hit", "hurt", "battle", "round");
    /** Who a command can act on. */
    public static final Set<String> TARGETS =
            ordered("user", "target", "party", "enemies", "random_enemy", "random_ally");
    public static final Set<String> STATS = ordered("attack", "defense", "speed");
    /** What can be read of {@code user.} and {@code target.}. */
    public static final Set<String> PROPERTIES = ordered("hp", "maxhp", "mp", "maxmp", "attack", "defense", "speed",
            "level", "tier", "alive", "poisoned", "stunned");
    static final Set<String> NUMBERS = ordered("damage", "round", "battle");
    /** Most statements one run may execute. */
    public static final int MAX_STEPS = 2000;
    /** Numbers are kept within plus or minus this, so nothing overflows. */
    static final int LIMIT = 1_000_000;
    public static final Script EMPTY = new Script(Map.of());

    /** What a script sees of the game, and what it can do to it. */
    public interface Host {
        /** A number such as {@code user.hp}, {@code damage} or {@code round}. */
        int number(String name);

        /** What {@code {user}}, {@code {target}} or {@code {item}} stands for in text. */
        String name(String who);

        /** A whole number from 0 to bound - 1. */
        int random(int bound);

        /**
         * Carries out one command.
         *
         * @param who     the target ("" if the command takes none)
         * @param stat    for boost, the stat ("" otherwise)
         * @param numbers the command's numbers
         */
        void command(String command, String who, String stat, int[] numbers);

        void say(String text);
    }

    interface Expr {
        int eval(Run run);
    }

    interface Statement {
        void run(Run run);
    }

    /** One run of a block: its variables and how many steps it has taken. */
    static final class Run {
        final Host host;
        final Map<String, Integer> variables = new HashMap<>();
        int steps;
        boolean stopped;

        Run(Host host) {
            this.host = host;
        }

        void block(List<Statement> statements) {
            for (Statement s : statements) {
                step();
                if (stopped) {
                    return;
                }
                s.run(this);
            }
        }

        void step() {
            if (++steps > MAX_STEPS) {
                stopped = true;
            }
        }
    }

    private final Map<String, List<Statement>> blocks;

    private Script(Map<String, List<Statement>> blocks) {
        this.blocks = blocks;
    }

    /** Reads a script. Throws IllegalArgumentException, naming the line, for a mistake. */
    public static Script parse(String text) {
        return text == null || text.isBlank() ? EMPTY : new Script(new ScriptParser(text).parse());
    }

    public boolean has(String trigger) {
        return blocks.containsKey(trigger);
    }

    /** Runs the block for a trigger; does nothing if there is none. */
    public void run(String trigger, Host host) {
        List<Statement> block = blocks.get(trigger);
        if (block != null) {
            new Run(host).block(block);
        }
    }

    static int limit(long n) {
        return (int) Math.max(-LIMIT, Math.min(LIMIT, n));
    }

    private static Set<String> ordered(String... words) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(words)));
    }
}
