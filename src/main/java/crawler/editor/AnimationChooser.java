package crawler.editor;

import crawler.core.AttackAnimation;

import javax.swing.JComboBox;
import java.util.ArrayList;
import java.util.List;

/**
 * A drop-down of the project's attack animations, plus "Built-in" (the slash, or the claw
 * for enemies). An id that no longer exists stays visible as "(missing)" instead of quietly
 * turning into the built-in one.
 */
final class AnimationChooser extends JComboBox<String> {
    private static final String BUILT_IN = "Built-in";
    private final List<AttackAnimation> animations;
    private final List<String> ids = new ArrayList<>();

    AnimationChooser(List<AttackAnimation> animations) {
        this.animations = List.copyOf(animations);
        setToolTipText("Make animations in Project > Attack Animations");
        fill(null);
    }

    private void fill(String extraId) {
        removeAllItems();
        ids.clear();
        addItem(BUILT_IN);
        ids.add(null);
        for (AttackAnimation a : animations) {
            addItem(a.name().isEmpty() ? a.id() : a.name() + "  [" + a.id() + "]");
            ids.add(a.id());
        }
        if (extraId != null) {
            addItem(extraId + "  (missing)");
            ids.add(extraId);
        }
    }

    /** Selects an animation by id (null for built-in). */
    void setAnimation(String id) {
        int index = ids.indexOf(id);
        if (index < 0) {
            fill(id);
            index = ids.size() - 1;
        }
        setSelectedIndex(index);
    }

    /** The chosen animation's id, or null for built-in. */
    String animation() {
        int index = getSelectedIndex();
        return index < 0 ? null : ids.get(index);
    }
}
