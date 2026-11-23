package crawler.ui;

import crawler.core.MoveAction;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Turns key presses into movement actions.
 *
 * <p>A tap queues exactly one action, so quick taps are never lost while a step is
 * still animating. Holding a key keeps repeating its action (after a short delay),
 * which lets you walk down a corridor by holding forward. The operating system's
 * own key repeat is ignored.
 */
public final class InputHandler extends KeyAdapter implements FocusListener {
    /** How long a key must be held before it starts repeating. */
    private static final double REPEAT_DELAY = 0.2;
    /** Minimum time between repeated actions (matters when animation is off). */
    private static final double REPEAT_INTERVAL = 0.14;
    private static final int MAX_QUEUED = 2;

    private final Map<Integer, MoveAction> moveKeys = new HashMap<>();
    private final Map<Integer, Runnable> commandKeys = new HashMap<>();
    private final Set<Integer> held = new LinkedHashSet<>();
    private final ArrayDeque<MoveAction> queue = new ArrayDeque<>();
    private int repeatKey = KeyEvent.VK_UNDEFINED;
    private double heldFor;
    private double sinceLastAction = 1e9;

    public InputHandler() {
        bind(MoveAction.FORWARD, KeyEvent.VK_W, KeyEvent.VK_UP, KeyEvent.VK_NUMPAD8);
        bind(MoveAction.BACKWARD, KeyEvent.VK_S, KeyEvent.VK_DOWN, KeyEvent.VK_NUMPAD2);
        bind(MoveAction.STRAFE_LEFT, KeyEvent.VK_A, KeyEvent.VK_NUMPAD1);
        bind(MoveAction.STRAFE_RIGHT, KeyEvent.VK_D, KeyEvent.VK_NUMPAD3);
        bind(MoveAction.TURN_LEFT, KeyEvent.VK_Q, KeyEvent.VK_LEFT, KeyEvent.VK_NUMPAD4);
        bind(MoveAction.TURN_RIGHT, KeyEvent.VK_E, KeyEvent.VK_RIGHT, KeyEvent.VK_NUMPAD6);
        bind(MoveAction.TURN_AROUND, KeyEvent.VK_X, KeyEvent.VK_NUMPAD5);
    }

    /** Binds keys (KeyEvent.VK_* codes) to a movement action, replacing any previous binding. */
    public void bind(MoveAction action, int... keyCodes) {
        for (int code : keyCodes) {
            commandKeys.remove(code);
            moveKeys.put(code, action);
        }
    }

    /** Binds keys to a one-shot command such as toggling a setting. */
    public void bindCommand(Runnable command, int... keyCodes) {
        for (int code : keyCodes) {
            moveKeys.remove(code);
            commandKeys.put(code, command);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (!held.add(code)) {
            return; // OS auto-repeat: we handle repeating ourselves
        }
        MoveAction action = moveKeys.get(code);
        if (action != null) {
            if (queue.size() < MAX_QUEUED) {
                queue.addLast(action);
            }
            repeatKey = code;
            heldFor = 0;
            return;
        }
        Runnable command = commandKeys.get(code);
        if (command != null) {
            command.run();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        held.remove(code);
        if (code == repeatKey) {
            // If another movement key is still down, carry on with that one.
            repeatKey = KeyEvent.VK_UNDEFINED;
            for (int other : held) {
                if (moveKeys.containsKey(other)) {
                    repeatKey = other;
                    heldFor = REPEAT_DELAY;
                }
            }
        }
    }

    @Override
    public void focusGained(FocusEvent e) {
    }

    @Override
    public void focusLost(FocusEvent e) {
        // We will never see the key-up events, so forget everything that was held.
        held.clear();
        repeatKey = KeyEvent.VK_UNDEFINED;
    }

    /** Stops the held key from repeating until it is pressed again (e.g. after walking into a wall). */
    public void cancelRepeat() {
        repeatKey = KeyEvent.VK_UNDEFINED;
    }

    public void update(double dt) {
        heldFor += dt;
        sinceLastAction += dt;
    }

    /** The next action to perform, or {@code null}. Call only when ready to act on it. */
    public MoveAction poll() {
        MoveAction action = queue.pollFirst();
        if (action == null && repeatKey != KeyEvent.VK_UNDEFINED
                && heldFor >= REPEAT_DELAY && sinceLastAction >= REPEAT_INTERVAL) {
            action = moveKeys.get(repeatKey);
            if (action == MoveAction.TURN_AROUND) {
                action = null; // spinning on the spot forever is never what you want
            }
        }
        if (action != null) {
            sinceLastAction = 0;
        }
        return action;
    }
}
