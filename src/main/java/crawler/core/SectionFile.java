package crawler.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The plain-text format for game data such as {@code party.txt} and {@code enemies.txt}:
 *
 * <pre>
 * ; comment
 * [enemy slime]
 * name = Blue Slime
 * hp = 12
 * </pre>
 *
 * Each section is a header in square brackets (a kind, then optionally an id) followed by
 * {@code key = value} lines. In values, {@code \n} is a line break.
 */
public final class SectionFile {
    private static final Pattern HEADER = Pattern.compile("\\[\\s*([A-Za-z]+)(?:\\s+([^\\]]*?))?\\s*]");

    private SectionFile() {
    }

    /** One [section] and its values. */
    public record Section(String kind, String id, String source, int line,
                          Map<String, String> values, Map<String, Integer> keyLines) {

        /** A text value, or "" if it isn't there. */
        public String text(String key) {
            return values.getOrDefault(key, "");
        }

        /** An image reference, or null if it isn't there. */
        public String image(String key) {
            String value = values.get(key);
            return value == null || value.isBlank() ? null : value.strip();
        }

        /** A whole number between min and max, or the fallback if it isn't there. */
        public int number(String key, int fallback, int min, int max) {
            String value = values.get(key);
            if (value == null) {
                return fallback;
            }
            try {
                int n = Integer.parseInt(value.strip());
                if (n >= min && n <= max) {
                    return n;
                }
            } catch (NumberFormatException e) {
                // reported below
            }
            throw error(key, key + " should be a whole number from " + min + " to " + max + ", not '" + value + "'");
        }

        /** Fails unless this is a [kind] section, with an id if one is needed. */
        public void expect(String expectedKind, boolean needsId) {
            if (!kind.equals(expectedKind)) {
                throw error(null, "expected [" + expectedKind + (needsId ? " id]" : "]") + ", not [" + kind + "]");
            }
            if (needsId && id == null) {
                throw error(null, "[" + kind + "] needs an id, like [" + kind + " slime]");
            }
            if (!needsId && id != null) {
                throw error(null, "[" + kind + "] doesn't take an id");
            }
        }

        /** Fails if the section has any key not in this list (usually a typo). */
        public void allow(String... keys) {
            Set<String> allowed = Set.of(keys);
            for (String key : values.keySet()) {
                if (!allowed.contains(key)) {
                    throw error(key, "unknown setting '" + key + "' (use " + String.join(", ", keys) + ")");
                }
            }
        }

        /** An error pointing at a key's line, or at the header if key is null. */
        public IllegalArgumentException error(String key, String message) {
            int at = key != null && keyLines.containsKey(key) ? keyLines.get(key) : line;
            return new IllegalArgumentException(source + ", line " + at + ": " + message);
        }
    }

    public static List<Section> read(Path file) throws IOException {
        return parse(file.getFileName().toString(), Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    public static List<Section> parse(String source, List<String> lines) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (i == 0 && line.startsWith("﻿")) {
                line = line.substring(1);
            }
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[")) {
                Matcher m = HEADER.matcher(line);
                if (!m.matches()) {
                    throw new IllegalArgumentException(source + ", line " + (i + 1) + ": expected a header like [enemy slime]");
                }
                String id = m.group(2) == null || m.group(2).isBlank() ? null : m.group(2).strip();
                current = new Section(m.group(1).toLowerCase(), id, source, i + 1, new LinkedHashMap<>(), new LinkedHashMap<>());
                sections.add(current);
                continue;
            }
            if (current == null) {
                throw new IllegalArgumentException(source + ", line " + (i + 1) + ": expected a header like [enemy slime] first");
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(source + ", line " + (i + 1) + ": expected 'key = value'");
            }
            String key = line.substring(0, eq).strip().toLowerCase();
            if (current.values().containsKey(key)) {
                throw new IllegalArgumentException(source + ", line " + (i + 1) + ": '" + key + "' is given twice");
            }
            current.values().put(key, MapLoader.unescape(line.substring(eq + 1).strip()));
            current.keyLines().put(key, i + 1);
        }
        return sections;
    }

    /** Builds the text of a section file. */
    public static final class Writer {
        private final StringBuilder out = new StringBuilder();

        /** Comment lines at the top of the file. */
        public Writer comment(String text) {
            for (String line : text.split("\n")) {
                out.append("; ").append(line).append('\n');
            }
            return this;
        }

        public Writer section(String kind, String id) {
            out.append('\n').append('[').append(kind);
            if (id != null) {
                out.append(' ').append(id);
            }
            out.append("]\n");
            return this;
        }

        /** Writes {@code key = value}; empty and null values are left out. */
        public Writer field(String key, Object value) {
            if (value != null && !value.toString().isBlank()) {
                out.append(key).append(" = ").append(MapWriter.escape(value.toString())).append('\n');
            }
            return this;
        }

        public void save(Path file) throws IOException {
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }
}
