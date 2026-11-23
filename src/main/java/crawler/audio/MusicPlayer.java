package crawler.audio;

import crawler.render.AssetSource;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;
import java.util.Objects;

/**
 * Plays background music: one track at a time, looping (or once, for a victory fanfare).
 * WAV files play through a {@link Clip}, MIDI files through the {@link Sequencer}.
 *
 * <p>Sound must never stop the game: if a file is missing or bad, or the computer has no
 * sound device, the music just stays quiet and a note goes to the console.
 */
public final class MusicPlayer {
    private final AssetSource assets;
    private String current;
    private Clip clip;
    private Sequencer sequencer;
    private boolean muted;

    public MusicPlayer(AssetSource assets) {
        this.assets = assets;
    }

    /** Plays a track on a loop; carries on if it is already playing. Null stops the music. */
    public void play(String reference) {
        play(reference, true);
    }

    /** Plays a track once (for a fanfare). */
    public void playOnce(String reference) {
        play(reference, false);
    }

    private void play(String reference, boolean loop) {
        if (Objects.equals(reference, current)) {
            return; // already playing it (or it's silent because it couldn't be played: don't retry)
        }
        stop();
        current = reference;
        start(loop);
    }

    private void start(boolean loop) {
        String reference = current;
        if (reference == null || muted) {
            return;
        }
        byte[] data = assets.data(reference);
        if (data == null) {
            System.err.println("Music not found: " + reference);
            return;
        }
        try {
            if (AudioFiles.isMidi(data)) {
                Sequencer s = MidiSystem.getSequencer();
                s.open();
                s.setSequence(new ByteArrayInputStream(data));
                s.setLoopCount(loop ? Sequencer.LOOP_CONTINUOUSLY : 0);
                s.start();
                sequencer = s;
            } else {
                AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
                Clip c = AudioSystem.getClip();
                c.open(in);
                if (loop) {
                    c.loop(Clip.LOOP_CONTINUOUSLY);
                } else {
                    c.start();
                }
                clip = c;
            }
        } catch (Exception | LinkageError e) {
            System.err.println("Can't play " + reference + ": " + e);
            closeLines();
        }
    }

    /** The track asked for most recently (playing, or silent because muted or broken), or null. */
    public String current() {
        return current;
    }

    public void stop() {
        closeLines();
        current = null;
    }

    private void closeLines() {
        if (clip != null) {
            clip.close();
            clip = null;
        }
        if (sequencer != null) {
            sequencer.close();
            sequencer = null;
        }
    }

    public boolean isMuted() {
        return muted;
    }

    /** Silences the music; unmuting starts the current track again, on a loop. */
    public void setMuted(boolean muted) {
        if (this.muted == muted) {
            return;
        }
        this.muted = muted;
        closeLines();
        if (!muted) {
            start(true);
        }
    }

    /**
     * Plays a short sound once (an attack landing). Overlapping sounds are fine: each gets
     * its own clip, closed when it finishes.
     */
    public void playEffect(String reference) {
        if (reference == null || muted) {
            return;
        }
        byte[] data = assets.data(reference);
        if (data == null || AudioFiles.isMidi(data)) {
            return;
        }
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
            Clip c = AudioSystem.getClip();
            c.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    c.close();
                }
            });
            c.open(in);
            c.start();
        } catch (Exception | LinkageError e) {
            System.err.println("Can't play " + reference + ": " + e);
        }
    }
}
