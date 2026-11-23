package crawler.editor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
 * map's images. Chosen files are imported into the {@link AssetStore}.
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
    private String reference;

    /**
     * @param reference      the image chosen now, or null for the built-in look
     * @param defaultImage   the built-in look, or null if there is none to show
     * @param defaultCaption describes the built-in look
     */
    ImagePicker(AssetStore assets, String reference, BufferedImage defaultImage, String defaultCaption) {
        super(new BorderLayout(10, 0));
        this.assets = assets;
        this.defaultImage = defaultImage;
        this.defaultCaption = defaultCaption;

        thumbnail.setPreferredSize(new Dimension(THUMB + 4, THUMB + 4));
        thumbnail.setBorder(BorderFactory.createLineBorder(new Color(90, 96, 112)));
        add(thumbnail, BorderLayout.WEST);

        JButton choose = new JButton("Choose image...");
        choose.addActionListener(e -> choose());
        if (defaultImage == null) {
            useDefault.setText("Remove image"); // there's no built-in picture to go back to
        }
        useDefault.addActionListener(e -> set(null));
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(choose);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(useDefault);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        caption.setAlignmentX(LEFT_ALIGNMENT);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(Box.createVerticalGlue());
        right.add(caption);
        right.add(Box.createVerticalStrut(6));
        right.add(buttons);
        right.add(Box.createVerticalGlue());
        add(right, BorderLayout.CENTER);
        set(reference);
    }

    /** The chosen image's reference, or null for the built-in look. */
    String reference() {
        return reference;
    }

    /** Shows another image (null = the built-in look), for forms that edit one thing after another. */
    void set(String newReference) {
        reference = newReference;
        BufferedImage image = reference == null ? defaultImage : assets.image(reference);
        thumbnail.setIcon(new ImageIcon(thumbnail(image)));
        if (reference == null) {
            caption.setText(defaultCaption);
        } else if (image == null) {
            caption.setText("<html>" + reference + "<br><font color='#c03030'>missing: the built-in look is used</font></html>");
        } else {
            caption.setText(reference + "   (" + image.getWidth() + " x " + image.getHeight() + ")");
        }
        useDefault.setEnabled(reference != null);
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
