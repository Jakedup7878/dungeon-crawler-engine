package crawler.editor;

import crawler.audio.AudioFiles;
import crawler.audio.MusicPlayer;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Picks a sound file (music, or an attack's sound effect): its name and length, "Choose
 * sound...", "Remove", and Play / Stop to listen. Chosen files are imported into the
 * {@link AssetStore}, like pictures.
 */
final class SoundPicker extends JPanel {
    private static File lastFolder;

    private final AssetStore assets;
    private final String emptyCaption;
    private final JLabel caption = new JLabel();
    private final JButton remove = new JButton("Remove");
    private final JButton play = new JButton("Play");
    private final MusicPlayer preview;
    private String reference;

    /**
     * @param reference    the sound chosen now, or null
     * @param emptyCaption what having no sound means here, such as "No music"
     */
    SoundPicker(AssetStore assets, String reference, String emptyCaption) {
        this.assets = assets;
        this.emptyCaption = emptyCaption;
        this.preview = new MusicPlayer(assets);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        JButton choose = new JButton("Choose sound...");
        choose.addActionListener(e -> choose());
        remove.addActionListener(e -> {
            stopPreview();
            set(null);
        });
        play.addActionListener(e -> togglePreview());
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(choose);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(play);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(remove);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        add(caption);
        add(Box.createVerticalStrut(4));
        add(buttons);
        // Stop the preview when the dialog closes.
        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                stopPreview();
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
        set(reference);
    }

    /** The chosen sound's reference, or null. */
    String reference() {
        return reference;
    }

    /** Shows another sound (null for none), for forms that edit one thing after another. */
    void set(String newReference) {
        stopPreview();
        reference = newReference;
        if (reference == null) {
            caption.setText(emptyCaption);
        } else {
            byte[] data = assets.data(reference);
            String description;
            try {
                description = AudioFiles.describe(data);
            } catch (IOException e) {
                description = data == null ? "missing" : "can't be played";
            }
            caption.setText(reference + "   (" + description + ")");
        }
        remove.setEnabled(reference != null);
        play.setEnabled(reference != null);
    }

    private void togglePreview() {
        if (preview.current() != null) {
            stopPreview();
        } else {
            preview.playOnce(reference);
            play.setText("Stop");
        }
    }

    private void stopPreview() {
        preview.stop();
        play.setText("Play");
    }

    private void choose() {
        JFileChooser chooser = new JFileChooser(lastFolder);
        chooser.setDialogTitle("Choose a sound");
        chooser.setFileFilter(new FileNameExtensionFilter("Sounds (WAV, MIDI, AIFF, AU)",
                "wav", "mid", "midi", "aif", "aiff", "au"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        lastFolder = file.getParentFile();
        try {
            set(assets.importSound(Path.of(file.getPath())));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Can't use " + file.getName() + ".\n" + e.getMessage(),
                    "Choose a sound", JOptionPane.WARNING_MESSAGE);
        }
    }
}
