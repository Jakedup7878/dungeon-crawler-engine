package crawler.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Read access to a project's files by their path inside the project, such as
 * {@code maps/b1f.txt} or {@code assets/wall.png}, wherever they are: a folder while you
 * work on the game, the encrypted pack a published game carries ({@link GamePack}), or
 * the classpath (the built-in sample).
 */
@FunctionalInterface
public interface ProjectFiles {

    /** The file's contents, or null if there is no such file. */
    byte[] read(String path) throws IOException;

    /** A text file's lines, or null if there is no such file. */
    default List<String> lines(String path) throws IOException {
        byte[] bytes = read(path);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8).lines().toList();
    }

    /** Files in a folder on disk. */
    static ProjectFiles folder(Path root) {
        return path -> {
            Path file = root.resolve(path).normalize();
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        };
    }

    /** Files already in memory, by path (with forward slashes). */
    static ProjectFiles of(Map<String, byte[]> files) {
        Map<String, byte[]> copy = Map.copyOf(files);
        return copy::get;
    }

    /** Files on the classpath under a resource folder, such as {@code "/sample/"}. */
    static ProjectFiles classpath(String folder) {
        String base = folder.endsWith("/") ? folder : folder + "/";
        return path -> {
            try (InputStream in = ProjectFiles.class.getResourceAsStream(base + path)) {
                return in == null ? null : in.readAllBytes();
            }
        };
    }
}
