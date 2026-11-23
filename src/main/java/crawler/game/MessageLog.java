package crawler.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** A short scrolling log of game messages. */
public final class MessageLog {
    private final int capacity;
    private final Deque<String> lines = new ArrayDeque<>();

    public MessageLog(int capacity) {
        this.capacity = capacity;
    }

    public void add(String message) {
        lines.addLast(message);
        while (lines.size() > capacity) {
            lines.removeFirst();
        }
    }

    /** Up to {@code count} of the newest messages, oldest first. */
    public List<String> recent(int count) {
        List<String> out = new ArrayList<>(count);
        Iterator<String> it = lines.descendingIterator();
        while (it.hasNext() && out.size() < count) {
            out.add(0, it.next());
        }
        return out;
    }
}
