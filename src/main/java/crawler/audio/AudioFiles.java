package crawler.audio;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * The sound files the game plays: WAV (and the other formats Java reads without help, AIFF
 * and AU) for music and effects, and MIDI for music. Reading a file's header needs no sound
 * card, so the editor can check files on any computer.
 */
public final class AudioFiles {
    private AudioFiles() {
    }

    /** MIDI files start with "MThd". */
    public static boolean isMidi(byte[] data) {
        return data != null && data.length >= 4 && data[0] == 'M' && data[1] == 'T' && data[2] == 'h' && data[3] == 'd';
    }

    /**
     * A short description such as "WAV, 44100 Hz stereo, 0:42" or "MIDI, 2:31".
     *
     * @throws IOException with a message for the user if Java can't play it
     */
    public static String describe(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("The file is empty.");
        }
        if (isMidi(data)) {
            try {
                Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(data));
                return "MIDI, " + time(sequence.getMicrosecondLength() / 1_000_000.0);
            } catch (InvalidMidiDataException e) {
                throw new IOException("It looks like a MIDI file but can't be read: " + e.getMessage());
            }
        }
        try {
            AudioFileFormat file = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(data));
            AudioFormat format = file.getFormat();
            String channels = format.getChannels() == 1 ? "mono" : format.getChannels() == 2 ? "stereo"
                    : format.getChannels() + " channels";
            double seconds = file.getFrameLength() > 0 && format.getFrameRate() > 0
                    ? file.getFrameLength() / format.getFrameRate() : 0;
            return file.getType() + ", " + Math.round(format.getSampleRate()) + " Hz " + channels
                    + (seconds > 0 ? ", " + time(seconds) : "");
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("The game can play WAV and MIDI files (and AIFF or AU). Convert MP3 or OGG files to WAV first.");
        }
    }

    /** Whether the game can play this file. */
    public static boolean isPlayable(byte[] data) {
        try {
            describe(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String time(double seconds) {
        long whole = Math.round(seconds);
        return String.format(Locale.ROOT, "%d:%02d", whole / 60, whole % 60);
    }
}
