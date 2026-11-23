package crawler.editor;

import crawler.audio.AudioFiles;
import crawler.core.DungeonMap;
import crawler.render.AssetSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The pictures and sounds the game being edited uses.
 *
 * <p>When you import an image it is kept in memory and the map refers to it as
 * {@code assets/<name>}. Saving the map writes it into an {@code assets} folder next to
 * the map file, so the map and its pictures can be copied around together. Images the
 * map already had are read from that folder.
 */
final class AssetStore implements AssetSource {
    static final String FOLDER = "assets";
    private static final long MAX_BYTES = 16L * 1024 * 1024;

    /** Imported images, by reference, that may not be on disk yet. */
    private final Map<String, byte[]> imported = new HashMap<>();
    private final Map<String, Optional<BufferedImage>> decoded = new HashMap<>();
    /** The folder the map file is in, or null if it has never been saved. */
    private Path folder;

    AssetStore(Path folder) {
        this.folder = folder;
    }

    /** Starts afresh for another map (New, Open). */
    void reset(Path folder) {
        this.folder = folder;
        imported.clear();
        decoded.clear();
    }

    /** After saving into a (possibly different) folder, images are looked up there. */
    void setFolder(Path folder) {
        if (!folder.equals(this.folder)) {
            this.folder = folder;
            decoded.clear();
        }
    }

    @Override
    public BufferedImage image(String reference) {
        if (reference == null) {
            return null;
        }
        return decoded.computeIfAbsent(reference, r -> Optional.ofNullable(decode(bytes(r)))).orElse(null);
    }

    /** Raw file contents, for music and sounds. */
    @Override
    public byte[] data(String reference) {
        return reference == null ? null : bytes(reference);
    }

    /** The file contents for a reference: imported, or read from the map's folder. Null if missing. */
    byte[] bytes(String reference) {
        byte[] bytes = imported.get(reference);
        if (bytes != null || folder == null) {
            return bytes;
        }
        Path file = folder.resolve(reference);
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Brings an image file into the map and returns the reference to use for it. Importing
     * the same picture twice gives the same reference; a different picture with the same
     * file name gets a numbered name.
     *
     * @throws IOException with a message for the user if the file can't be used
     */
    String importImage(Path file) throws IOException {
        byte[] bytes = readLimited(file);
        BufferedImage image = decode(bytes);
        if (image == null) {
            throw new IOException("It isn't an image the editor can read. Use PNG, JPEG, GIF or BMP.");
        }
        String reference = store(file, bytes);
        decoded.put(reference, Optional.of(image));
        return reference;
    }

    /**
     * Brings a sound file (WAV, MIDI, AIFF or AU) into the game, the same way as an image.
     *
     * @throws IOException with a message for the user if the game couldn't play it
     */
    String importSound(Path file) throws IOException {
        byte[] bytes = readLimited(file);
        AudioFiles.describe(bytes); // throws if Java can't play it
        return store(file, bytes);
    }

    private static byte[] readLimited(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_BYTES) {
            throw new IOException("It is " + size / (1024 * 1024) + " MB; files can be at most 16 MB.");
        }
        return Files.readAllBytes(file);
    }

    /** Keeps the bytes under assets/name, numbering the name if a different file already has it. */
    private String store(Path file, byte[] bytes) {
        String reference = FOLDER + "/" + cleanName(file.getFileName().toString());
        for (int n = 2; ; n++) {
            byte[] existing = bytes(reference);
            if (existing == null) {
                imported.put(reference, bytes);
                return reference;
            }
            if (Arrays.equals(existing, bytes)) {
                return reference;
            }
            reference = numbered(FOLDER + "/" + cleanName(file.getFileName().toString()), n);
        }
    }

    /**
     * Writes every image the map uses into {@code target}, the folder the map is being saved
     * in. Files already there with the same contents are left alone. If a different file
     * already has an image's name, the image is saved under a new name, and the returned map
     * says which references must change (usually it is empty).
     */
    Map<String, String> saveImages(DungeonMap map, Path target) throws IOException {
        return saveImages(map.assetReferences(), target);
    }

    /** The same for any list of image references, such as party portraits and enemy images. */
    Map<String, String> saveImages(Collection<String> references, Path target) throws IOException {
        Map<String, String> renamed = new LinkedHashMap<>();
        for (String reference : references) {
            byte[] bytes = bytes(reference);
            if (bytes == null) {
                continue; // missing: the map checks already point this out
            }
            String chosen = reference;
            for (int n = 2; ; n++) {
                Path file = target.resolve(chosen).normalize();
                if (!Files.exists(file)) {
                    if (file.getParent() != null) {
                        Files.createDirectories(file.getParent());
                    }
                    Files.write(file, bytes);
                    break;
                }
                if (Arrays.equals(Files.readAllBytes(file), bytes)) {
                    break;
                }
                chosen = numbered(reference, n);
            }
            if (!chosen.equals(reference)) {
                renamed.put(reference, chosen);
                imported.put(chosen, bytes);
            }
        }
        return renamed;
    }

    /** A safe, lower-case file name that keeps its extension. */
    static String cleanName(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+[.]", ".")
                .replaceAll("^[-.]+|-+$", "");
        return name.isEmpty() || name.startsWith(".") ? "image" + name : name;
    }

    /** "assets/wall.png", 2 gives "assets/wall-2.png". */
    static String numbered(String reference, int n) {
        int slash = reference.lastIndexOf('/');
        int dot = reference.lastIndexOf('.');
        if (dot <= slash) {
            return reference + "-" + n;
        }
        return reference.substring(0, dot) + "-" + n + reference.substring(dot);
    }

    private static BufferedImage decode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
