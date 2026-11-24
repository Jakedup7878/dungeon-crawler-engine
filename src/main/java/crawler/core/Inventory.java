package crawler.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the party is carrying (not counting what its members wear), in the order it was found. */
public final class Inventory {
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    /** @return how many fitted; the party carries at most {@link ItemStack#MAX} of each */
    public int add(Item item, int count) {
        int had = count(item);
        int total = Math.min(ItemStack.MAX, had + count);
        if (total > 0) {
            items.put(item.id(), item);
            counts.put(item.id(), total);
        }
        return total - had;
    }

    /** Takes one; false if there is none. */
    public boolean remove(Item item) {
        int count = count(item);
        if (count == 0) {
            return false;
        }
        if (count == 1) {
            items.remove(item.id());
            counts.remove(item.id());
        } else {
            counts.put(item.id(), count - 1);
        }
        return true;
    }

    public int count(Item item) {
        return counts.getOrDefault(item.id(), 0);
    }

    public List<Item> items() {
        return new ArrayList<>(items.values());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
