package crawler.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Some of one item, by id: written {@code potion x3}, or just {@code potion} for one. */
public record ItemStack(String id, int count) {
    public static final int MAX = 99;

    public ItemStack {
        id = id.strip();
        count = Math.max(1, Math.min(MAX, count));
    }

    /** Reads {@code potion x3, iron-sword}. */
    public static List<ItemStack> parse(String text) {
        List<ItemStack> stacks = new ArrayList<>();
        for (String part : Encounters.parseIds(text)) {
            String[] words = part.split("\\s+");
            if (words.length == 1) {
                stacks.add(new ItemStack(words[0], 1));
            } else if (words.length == 2 && words[1].matches("[xX]?0*[1-9]\\d?")) {
                stacks.add(new ItemStack(words[0], Integer.parseInt(words[1].replaceAll("[xX]", ""))));
            } else {
                throw new IllegalArgumentException("'" + part
                        + "' should be an item's id and how many, like potion x3 (up to x" + MAX + ")");
            }
        }
        return stacks;
    }

    public static String text(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::toString).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return count == 1 ? id : id + " x" + count;
    }
}
