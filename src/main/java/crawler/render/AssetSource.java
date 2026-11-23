package crawler.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where a game's own files (pictures, music) come from. A map refers to them by asset
 * references: paths relative to the project folder, such as {@code assets/mossy-wall.png}.
 */
public interface AssetSource {
    /** No files of its own: everything uses the built-in look, and there is no music. */
    AssetSource NONE = reference -> null;

    /** The image for a reference, or null if it is missing or isn't a readable image. */
    BufferedImage image(String reference);

    /** The raw bytes of a file (for music and sounds), or null if it is missing. */
    default byte[] data(String reference) {
        return null;
    }

    /** Files relative to a folder (the project folder, or the folder a lone map is in). */
    static AssetSource directory(Path folder) {
        return cached(reference -> {
            Path file = folder.resolve(reference);
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        });
    }

    /** Files on the classpath, relative to a resource folder such as {@code "/sample/"}. */
    static AssetSource classpath(String folder) {
        String base = folder.endsWith("/") ? folder : folder + "/";
        return cached(reference -> {
            try (InputStream in = AssetSource.class.getResourceAsStream(base + reference)) {
                return in == null ? null : in.readAllBytes();
            }
        });
    }

    /** Reads each file once and remembers the result, including failures. */
    private static AssetSource cached(FileReader reader) {
        Map<String, Optional<byte[]>> files = new HashMap<>();
        Map<String, Optional<BufferedImage>> images = new HashMap<>();
        return new AssetSource() {
            @Override
            public byte[] data(String reference) {
                if (reference == null) {
                    return null;
                }
                return files.computeIfAbsent(reference, r -> {
                    try {
                        return Optional.ofNullable(reader.read(r));
                    } catch (IOException | RuntimeException e) {
                        System.err.println("Could not read " + r + ": " + e.getMessage());
                        return Optional.empty();
                    }
                }).orElse(null);
            }

            @Override
            public BufferedImage image(String reference) {
                if (reference == null) {
                    return null;
                }
                return images.computeIfAbsent(reference, r -> Optional.ofNullable(decode(data(r)))).orElse(null);
            }
        };
    }

    /** An image from file contents, or null if they aren't an image. */
    static BufferedImage decode(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Reads one file; may throw. */
    interface FileReader {
        byte[] read(String reference) throws IOException;
    }
}
