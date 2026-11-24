package crawler.editor;

import crawler.core.DungeonMap;
import crawler.render.AssetSource;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * The map being edited: the map itself, the file it came from, undo/redo history and
 * whether there are unsaved changes. Views register a listener and redraw when it changes.
 *
 * <p>Undo works on whole-map snapshots. An edit is everything between
 * {@link #beginEdit()} and {@link #endEdit()}, typically one mouse drag.
 */
final class MapDocument {
    private static final int UNDO_LIMIT = 200;

    private final List<Runnable> listeners = new ArrayList<>();
    private final AssetSource assets;
    private final Deque<DungeonMap> undo = new ArrayDeque<>();
    private final Deque<DungeonMap> redo = new ArrayDeque<>();
    private DungeonMap map;
    private Path file;
    /** The map as it was last saved or opened. */
    private DungeonMap saved;
    /** The map before the edit in progress, or null. */
    private DungeonMap before;
    private MapCheck check;
    /** Ids of the project's enemies, for checking encounters; null when there is no project. */
    private Set<String> knownEnemies;
    /** The same for items. */
    private Set<String> knownItems;

    /** @param assets where the map's images are looked up, for the checks */
    MapDocument(DungeonMap map, Path file, AssetSource assets) {
        this.assets = assets;
        reset(map, file);
    }

    /** Starts afresh on another map (New, Open), forgetting the undo history. */
    void reset(DungeonMap map, Path file) {
        map.sealEdges();
        this.map = map;
        this.file = file;
        this.saved = map.copy();
        this.before = null;
        undo.clear();
        redo.clear();
        changed();
    }

    DungeonMap map() {
        return map;
    }

    /** The file this map is saved in, or null if it has never been saved. */
    Path file() {
        return file;
    }

    boolean isDirty() {
        return !map.contentEquals(saved);
    }

    MapCheck check() {
        if (check == null) {
            check = MapCheck.of(map, assets, knownEnemies, knownItems);
        }
        return check;
    }

    void addListener(Runnable listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------ editing

    void beginEdit() {
        if (before != null) {
            endEdit();
        }
        before = map.copy();
    }

    boolean isEditing() {
        return before != null;
    }

    /** Call after changing the map during an edit: walls off rock and refreshes the views. */
    void edited() {
        map.sealEdges();
        changed();
    }

    /** Finishes the edit; it becomes one undo step if anything actually changed. */
    void endEdit() {
        if (before == null) {
            return;
        }
        map.sealEdges();
        if (!map.contentEquals(before)) {
            push(undo, before);
            redo.clear();
        }
        before = null;
        changed();
    }

    /** Swaps in a whole new map (resize, rename) as one undo step. */
    void replace(DungeonMap next) {
        if (before != null) {
            endEdit();
        }
        next.sealEdges();
        if (next.contentEquals(map)) {
            return;
        }
        push(undo, map);
        redo.clear();
        map = next;
        changed();
    }

    boolean canUndo() {
        return before == null && !undo.isEmpty();
    }

    boolean canRedo() {
        return before == null && !redo.isEmpty();
    }

    void undo() {
        if (canUndo()) {
            push(redo, map);
            map = undo.pop();
            changed();
        }
    }

    void redo() {
        if (canRedo()) {
            push(undo, map);
            map = redo.pop();
            changed();
        }
    }

    /** The project's enemy ids (null for none known), so the checks can spot typos in encounters. */
    void setKnownEnemies(Set<String> ids) {
        knownEnemies = ids == null ? null : Set.copyOf(ids);
        changed();
    }

    /** The project's item ids (null for none known), so the checks can spot typos in chests. */
    void setKnownItems(Set<String> ids) {
        knownItems = ids == null ? null : Set.copyOf(ids);
        changed();
    }

    /** Re-runs the checks (after images were imported or the asset folder changed). */
    void refresh() {
        changed();
    }

    /** Records that the map is now saved in {@code file}. */
    void markSaved(Path file) {
        this.file = file;
        this.saved = map.copy();
        changed();
    }

    private static void push(Deque<DungeonMap> stack, DungeonMap state) {
        stack.push(state);
        while (stack.size() > UNDO_LIMIT) {
            stack.removeLast();
        }
    }

    private void changed() {
        check = null;
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
