package crawler.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where a map's own images come from. A map refers to images by asset references,
 * paths relative to the map file such as {@code assets/mossy-wall.png}.
 */
public interface AssetSource {
    /** No custom images: everything uses the built-in look. */
    AssetSource NONE = reference -> null;

    /** The image for a reference, or null if it is missing or isn't a readable image. */
    BufferedImage image(String reference);

    /** Images from files, relative to a folder (the one the map file is in). */
    static AssetSource directory(Path folder) {
        return cached(reference -> {
            Path file = folder.resolve(reference);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return ImageIO.read(file.toFile());
        });
    }

    /** Images on the classpath, relative to a resource folder such as {@code "/maps/"}. */
    static AssetSource classpath(String folder) {
        String base = folder.endsWith("/") ? folder : folder + "/";
        return cached(reference -> {
            try (InputStream in = AssetSource.class.getResourceAsStream(base + reference)) {
                return in == null ? null : ImageIO.read(in);
            }
        });
    }

    /** Reads each reference once and remembers the result, including failures. */
    private static AssetSource cached(ImageReader reader) {
        Map<String, Optional<BufferedImage>> cache = new HashMap<>();
        return reference -> cache.computeIfAbsent(reference, r -> {
            try {
                return Optional.ofNullable(reader.read(r));
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not read image " + r + ": " + e.getMessage());
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Reads one image; may throw. */
    interface ImageReader {
        BufferedImage read(String reference) throws IOException;
    }
}
