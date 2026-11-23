package crawler.editor;

import crawler.core.SpriteRef;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A thumbnail with "Choose image..." and "Use built-in" buttons, for picking one of the
 * game's pictures. Chosen files are imported into the {@link AssetStore}.
 *
 * <p>An animatable picker also has Frames and Speed boxes, for animation strips: the frames
 * side by side in one image, left to right (see {@link SpriteRef}).
 */
final class ImagePicker extends JPanel {
    private static final int THUMB = 72;
    /** Where the last image came from, so the next file dialog starts there. */
    private static File lastFolder;

    private final AssetStore assets;
    private final BufferedImage defaultImage;
    private final String defaultCaption;
    private final JLabel thumbnail = new JLabel();
    private final JLabel caption = new JLabel();
    private final JButton useDefault = new JButton("Use built-in");
    private final JSpinner frames = new JSpinner(new SpinnerNumberModel(1, 1, SpriteRef.MAX_FRAMES, 1));
    private final JSpinner fps = new JSpinner(new SpinnerNumberModel(SpriteRef.DEFAULT_FPS, 1.0, 60.0, 1.0));
    private final boolean animatable;
    private String reference;

    /** A picker for a still picture, such as a texture. */
    ImagePicker(AssetStore assets, String reference, BufferedImage defaultImage, String defaultCaption) {
        this(assets, reference, defaultImage, defaultCaption, false);
    }

    /**
     * @param reference      the image chosen now, or null for the built-in look
     * @param defaultImage   the built-in look, or null if there is none to show
     * @param defaultCaption describes the built-in look
     * @param animatable     whether to offer Frames and Speed for animation strips
     */
    ImagePicker(AssetStore assets, String reference, BufferedImage defaultImage, String defaultCaption, boolean animatable) {
        super(new BorderLayout(10, 0));
        this.assets = assets;
        this.defaultImage = defaultImage;
        this.defaultCaption = defaultCaption;
        this.animatable = animatable;

        thumbnail.setPreferredSize(new Dimension(THUMB + 4, THUMB + 4));
        thumbnail.setBorder(BorderFactory.createLineBorder(new Color(90, 96, 112)));
        add(thumbnail, BorderLayout.WEST);

        JButton choose = new JButton("Choose image...");
        choose.addActionListener(e -> choose());
        if (defaultImage == null) {
            useDefault.setText("Remove image"); // there's no built-in picture to go back to
        }
        useDefault.addActionListener(e -> set(null));
        JPanel buttons = row(choose, Box.createHorizontalStrut(6), useDefault);
        caption.setAlignmentX(LEFT_ALIGNMENT);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(Box.createVerticalGlue());
        right.add(caption);
        right.add(Box.createVerticalStrut(6));
        right.add(buttons);
        if (animatable) {
            ((JSpinner.DefaultEditor) frames.getEditor()).getTextField().setColumns(3);
            ((JSpinner.DefaultEditor) fps.getEditor()).getTextField().setColumns(3);
            frames.setToolTipText("How many frames the image holds, side by side, left to right. 1 = a still picture.");
            fps.setToolTipText("Frames per second");
            frames.addChangeListener(e -> refresh());
            right.add(Box.createVerticalStrut(6));
            right.add(row(new JLabel("Frames "), frames, Box.createHorizontalStrut(10), new JLabel("Speed "), fps,
                    new JLabel(" fps")));
        }
        right.add(Box.createVerticalGlue());
        add(right, BorderLayout.CENTER);
        set(reference);
    }

    private static JPanel row(java.awt.Component... parts) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (java.awt.Component part : parts) {
            if (part instanceof JSpinner) {
                part.setMaximumSize(part.getPreferredSize()); // don't stretch across a wide dialog
            }
            row.add(part);
        }
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /** The chosen image's reference, or null for the built-in look. */
    String reference() {
        return reference;
    }

    /** The chosen picture with its frames and speed, or null for the built-in look. */
    SpriteRef sprite() {
        if (reference == null) {
            return null;
        }
        return new SpriteRef(reference, StatFields.value(frames), ((Number) fps.getValue()).doubleValue());
    }

    /** Shows another picture (null = the built-in look), for forms that edit one thing after another. */
    void setSprite(SpriteRef sprite) {
        frames.setValue(sprite == null ? 1 : sprite.frames());
        fps.setValue(sprite == null ? SpriteRef.DEFAULT_FPS : sprite.fps());
        set(sprite == null ? null : sprite.image());
    }

    /** Shows another image (null = the built-in look). */
    void set(String newReference) {
        reference = newReference;
        refresh();
    }

    private void refresh() {
        BufferedImage image = reference == null ? defaultImage : assets.image(reference);
        int frameCount = animatable && reference != null ? (Integer) frames.getValue() : 1;
        thumbnail.setIcon(new ImageIcon(thumbnail(firstFrame(image, frameCount))));
        if (reference == null) {
            caption.setText(defaultCaption);
        } else if (image == null) {
            caption.setText("<html>" + reference + "<br><font color='#c03030'>missing: the built-in look is used</font></html>");
        } else {
            caption.setText(reference + "   (" + image.getWidth() + " x " + image.getHeight()
                    + (frameCount > 1 ? ", " + frameCount + " frames" : "") + ")");
        }
        useDefault.setEnabled(reference != null);
        frames.setEnabled(reference != null);
        fps.setEnabled(reference != null);
    }

    private static BufferedImage firstFrame(BufferedImage image, int frameCount) {
        if (image == null || frameCount <= 1 || image.getWidth() < frameCount) {
            return image;
        }
        return image.getSubimage(0, 0, image.getWidth() / frameCount, image.getHeight());
    }

    private void choose() {
        JFileChooser chooser = new JFileChooser(lastFolder);
        chooser.setDialogTitle("Choose an image");
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPEG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        lastFolder = file.getParentFile();
        try {
            set(assets.importImage(Path.of(file.getPath())));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Can't use " + file.getName() + ".\n" + e.getMessage(),
                    "Choose an image", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** The image fitted into a square on a checkerboard, so transparent parts show as such. */
    private static BufferedImage thumbnail(BufferedImage image) {
        BufferedImage out = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            for (int y = 0; y < THUMB; y += 8) {
                for (int x = 0; x < THUMB; x += 8) {
                    g.setColor(((x + y) / 8) % 2 == 0 ? new Color(52, 54, 62) : new Color(40, 42, 48));
                    g.fillRect(x, y, 8, 8);
                }
            }
            if (image != null) {
                double scale = Math.min(THUMB / (double) image.getWidth(), THUMB / (double) image.getHeight());
                int w = Math.max(1, (int) Math.round(image.getWidth() * scale));
                int h = Math.max(1, (int) Math.round(image.getHeight() * scale));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, scale >= 1
                        ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                        : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, (THUMB - w) / 2, (THUMB - h) / 2, w, h, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }
}
