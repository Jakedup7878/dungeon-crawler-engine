package crawler.audio;

import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checking sound files needs no sound card, so these run anywhere. */
public class AudioFilesTest {

    /** One second of silence as a WAV file. */
    public static byte[] wav() throws IOException {
        AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
        byte[] silence = new byte[22050 * 2];
        AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(silence), format, 22050);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(in, AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    /** A few notes as a MIDI file. */
    static byte[] midi() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 24);
        Track track = sequence.createTrack();
        for (int i = 0; i < 4; i++) {
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60 + i, 90), i * 24L));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60 + i, 0), i * 24L + 20));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MidiSystem.write(sequence, 0, out);
        return out.toByteArray();
    }

    @Test
    void wavFilesAreDescribed() throws IOException {
        String description = AudioFiles.describe(wav());
        assertTrue(description.contains("22050 Hz") && description.contains("mono") && description.contains("0:01"),
                description);
        assertFalse(AudioFiles.isMidi(wav()));
    }

    @Test
    void midiFilesAreRecognised() throws Exception {
        byte[] midi = midi();
        assertTrue(AudioFiles.isMidi(midi));
        assertTrue(AudioFiles.describe(midi).startsWith("MIDI"), AudioFiles.describe(midi));
        assertTrue(AudioFiles.isPlayable(midi));
    }

    @Test
    void otherFilesAreRefusedWithAHelpfulMessage() {
        byte[] notSound = "ID3 pretend this is an MP3".getBytes();
        IOException e = assertThrows(IOException.class, () -> AudioFiles.describe(notSound));
        assertTrue(e.getMessage().contains("WAV"), e.getMessage());
        assertFalse(AudioFiles.isPlayable(new byte[0]));
        assertFalse(AudioFiles.isPlayable(null));
    }

    @Test
    void theMusicPlayerNeverThrows() {
        MusicPlayer player = new MusicPlayer(reference -> null);
        player.play("assets/missing.wav");
        player.playEffect("assets/missing.wav");
        player.setMuted(true);
        player.setMuted(false);
        player.stop();
        assertTrue(player.current() == null);
    }
}
