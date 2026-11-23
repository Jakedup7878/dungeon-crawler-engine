package crawler.editor;

import crawler.core.AttackAnimation;
import crawler.core.SpriteRef;
import crawler.render.ObjectLooks;
import crawler.render.Sprite;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The fields for one attack animation, for {@link RosterDialog}, with a live preview that
 * plays the animation over a target the way the battle screen will.
 */
final class AnimationForm implements RosterDialog.Form<AttackAnimation> {
    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(18);
    private final ImagePicker image;
    private final JSpinner size = StatFields.spinner(AttackAnimation.MIN_SIZE, AttackAnimation.MAX_SIZE);
    private final SoundPicker sound;
    private final ObjectLooks looks;
    private final JComponent component;

    AnimationForm(AssetStore assets) {
        this.looks = new ObjectLooks(assets);
        id.setToolTipText("How classes and enemies refer to this animation. Leave empty to make one from the name.");
        image = new ImagePicker(assets, null, ObjectLooks.defaultAttack(false).image(0.15), "Built-in slash", true);
        sound = new SoundPicker(assets, null, "No sound");
        size.setToolTipText("How big it is drawn, in percent of the target's height");
        List<JComponent> rows = new ArrayList<>(List.of(
                new JLabel("Name"), name,
                new JLabel("Id"), id,
                new JLabel("Animation"), image,
                new JLabel(), ObjectDialog.hint("An animation is one image with the frames side by side, left to right. "
                        + "Set Frames to how many there are; it plays once per attack."),
                new JLabel("Size (%)"), size,
                new JLabel("Sound"), sound,
                new JLabel("Preview"), new Preview()));
        component = MapDialogs.form(rows.toArray(new JComponent[0]));
    }

    /** Plays the animation over and over on a stand-in enemy. */
    private final class Preview extends JComponent {
        private final long start = System.nanoTime();

        Preview() {
            setPreferredSize(new Dimension(220, 160));
            setBorder(BorderFactory.createLineBorder(new Color(90, 96, 112)));
            Timer timer = new Timer(40, e -> repaint());
            addHierarchyListener(e -> {
                if (isShowing()) {
                    timer.start();
                } else {
                    timer.stop();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setColor(new Color(24, 26, 34));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                double seconds = (System.nanoTime() - start) / 1e9;
                int targetH = getHeight() - 40;
                BufferedImage target = ObjectLooks.defaultEnemy("preview").image(seconds);
                int targetW = target.getWidth() * targetH / target.getHeight();
                int tx = (getWidth() - targetW) / 2;
                int ty = 20;
                g.drawImage(target, tx, ty, targetW, targetH, null);

                SpriteRef ref = image.sprite();
                Sprite animation = ref == null ? null : looks.custom(ref);
                if (animation == null) {
                    animation = ObjectLooks.defaultAttack(false);
                }
                double length = Math.max(0.3, animation.duration());
                double t = seconds % (length + 0.5);
                if (t <= length) {
                    BufferedImage frame = animation.imageOnce(t * animation.duration() / length);
                    int box = targetH * StatFields.value(size) / 100;
                    int w = frame.getWidth() * box / Math.max(frame.getWidth(), frame.getHeight());
                    int h = frame.getHeight() * box / Math.max(frame.getWidth(), frame.getHeight());
                    g.drawImage(frame, getWidth() / 2 - w / 2, ty + targetH / 2 - h / 2, w, h, null);
                }
            } finally {
                g.dispose();
            }
        }
    }

    @Override
    public JComponent component() {
        return component;
    }

    @Override
    public void show(AttackAnimation a) {
        id.setText(a.id());
        name.setText(a.name());
        image.setSprite(a.image());
        size.setValue(a.size());
        sound.set(a.sound());
    }

    @Override
    public AttackAnimation read() {
        String typedId = id.getText().strip();
        if (typedId.isEmpty()) {
            typedId = crawler.core.EnemyType.idFor(name.getText());
        }
        return new AttackAnimation(typedId, name.getText(), image.sprite(), sound.reference(), StatFields.value(size));
    }

    @Override
    public AttackAnimation create(List<AttackAnimation> animations) {
        return AttackAnimation.create(EnemyForm.freeId(animations.stream().map(AttackAnimation::id).toList(), "attack"),
                "New animation");
    }

    @Override
    public String label(AttackAnimation a) {
        return (a.name().isEmpty() ? "(no name)" : a.name()) + "  [" + a.id() + "]";
    }

    @Override
    public String problem(List<AttackAnimation> animations) {
        return EnemyForm.idProblem(animations.stream().map(AttackAnimation::id).toList(),
                animations.stream().map(AttackAnimation::name).toList(), "animation");
    }
}
