package crawler.publish;

import crawler.Main;
import crawler.core.GamePack;
import crawler.core.GameProject;
import crawler.core.ProjectFiles;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Turns a project into a program players can run: a folder holding {@code <Game>.exe}, a
 * private copy of Java, the game's code, and the project encrypted into {@code game.pak}.
 *
 * <p>The program files come from a <em>player template</em>: a ready-made game program
 * ({@value #TEMPLATE_NAME}.exe with its {@code app} and {@code runtime} folders). The packaged
 * editor carries one; when running from source, one is built first with the JDK's jpackage.
 * Publishing then copies it under the game's name and adds the pack, so it needs no JDK.
 */
public final class Publisher {
    /** The name of the game program in a template (the packaged editor's "play the sample" program). */
    public static final String TEMPLATE_NAME = "Sample Quest";
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private Publisher() {
    }

    /** A ready-made game program to copy. */
    public record Template(Path root) {
        Path launcher() {
            return root.resolve(TEMPLATE_NAME + ".exe");
        }

        Path config() {
            return root.resolve("app").resolve(TEMPLATE_NAME + ".cfg");
        }

        Path jar() {
            return root.resolve("app").resolve("crawler.jar");
        }

        Path runtime() {
            return root.resolve("runtime");
        }

        public boolean isValid() {
            return Files.isRegularFile(launcher()) && Files.isRegularFile(config())
                    && Files.isRegularFile(jar()) && Files.isDirectory(runtime());
        }
    }

    /** The template inside the running program when it is the packaged editor; otherwise null. */
    public static Template bundledTemplate() {
        Path app = Main.programFolder();
        if (app == null || app.getParent() == null) {
            return null;
        }
        Template template = new Template(app.getParent());
        return template.isValid() ? template : null;
    }

    /**
     * Builds a template with the JDK this program runs on (when running from source rather
     * than from the packaged editor). Takes some seconds.
     *
     * @param work an empty folder to build in
     */
    public static Template buildTemplate(Path work, Consumer<String> progress) throws IOException {
        if (!WINDOWS) {
            throw new IOException("Publishing makes Windows programs, so it has to run on Windows.");
        }
        Path jpackage = Path.of(System.getProperty("java.home"), "bin", "jpackage.exe");
        if (!Files.isRegularFile(jpackage)) {
            throw new IOException("Publishing from here needs a JDK with jpackage, and this Java ("
                    + System.getProperty("java.home") + ") doesn't have it. Use the packaged editor, or publish.cmd.");
        }
        Path input = Files.createDirectories(work.resolve("input"));
        progress.accept("Packing the game's code...");
        writeJar(codeLocation(), input.resolve("crawler.jar"));
        progress.accept("Building the game program with jpackage...");
        Path image = work.resolve("image");
        run(List.of(jpackage.toString(), "--type", "app-image", "--name", TEMPLATE_NAME,
                "--input", input.toString(), "--main-jar", "crawler.jar", "--main-class", "crawler.Main",
                "--add-modules", "java.desktop", "--dest", image.toString()));
        Template template = new Template(image.resolve(TEMPLATE_NAME));
        if (!template.isValid()) {
            throw new IOException("jpackage didn't make the program it should have, in " + template.root());
        }
        return template;
    }

    /**
     * Publishes a project into {@code outputFolder/<game name>/}. Before writing anything, the
     * game is packed and loaded back from the pack, so a project with a problem is reported
     * rather than published. An earlier copy published to the same place is replaced.
     *
     * @return the game's program (.exe)
     */
    public static Path publish(Path projectRoot, Path outputFolder, Template template, Consumer<String> progress)
            throws IOException {
        GameProject project = GameProject.open(projectRoot);
        progress.accept("Encrypting the game's files...");
        byte[] pack = GamePack.pack(projectRoot);
        progress.accept("Checking that the packed game loads...");
        try {
            Main.loadProject(ProjectFiles.of(GamePack.unpack(pack)), projectRoot);
        } catch (IOException | IllegalArgumentException e) {
            throw new IOException("The game can't be published yet: " + e.getMessage(), e);
        }

        String name = programName(project.name());
        Path target = outputFolder.toAbsolutePath().resolve(name);
        if (Files.exists(target)) {
            if (!isPublishedGame(target)) {
                throw new IOException(target + " already exists and isn't a published game. "
                        + "Choose another folder, or move it out of the way.");
            }
            progress.accept("Replacing the earlier copy...");
            deleteTree(target);
        }
        progress.accept("Copying the game program...");
        Path app = Files.createDirectories(target.resolve("app"));
        copyTree(template.runtime(), target.resolve("runtime"));
        Files.copy(template.jar(), app.resolve("crawler.jar"));
        Files.copy(template.config(), app.resolve(name + ".cfg")); // the program finds its settings by its own name
        Path program = target.resolve(name + ".exe");
        Files.copy(template.launcher(), program);
        Files.write(app.resolve(GamePack.FILE_NAME), pack);
        progress.accept("Published " + program);
        return program;
    }

    /** A program name from the game's name: letters, digits, spaces, dashes and underscores. */
    public static String programName(String gameName) {
        String name = gameName.replaceAll("[^A-Za-z0-9 _-]", "").strip();
        return name.isEmpty() ? "Game" : name;
    }

    /** Whether a folder holds a game this publisher made (and so may be replaced). */
    static boolean isPublishedGame(Path folder) {
        return Files.isRegularFile(folder.resolve("app").resolve(GamePack.FILE_NAME))
                && Files.isDirectory(folder.resolve("runtime"));
    }

    // ------------------------------------------------------------ files

    private static Path codeLocation() throws IOException {
        try {
            return Path.of(Publisher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            throw new IOException("Can't find the game's own code to publish it: " + e.getMessage());
        }
    }

    /** The game's code as a jar: copied if it already is one, packed if it is a folder of classes. */
    static void writeJar(Path code, Path jar) throws IOException {
        if (Files.isRegularFile(code)) {
            Files.copy(code, jar);
            return;
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "crawler.Main");
        try (OutputStream file = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(file, manifest);
             Stream<Path> walk = Files.walk(code)) {
            for (Path each : walk.filter(Files::isRegularFile).toList()) {
                String entry = code.relativize(each).toString().replace('\\', '/');
                if (!entry.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    out.putNextEntry(new JarEntry(entry));
                    Files.copy(each, out);
                    out.closeEntry();
                }
            }
        }
    }

    private static void run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) {
                throw new IOException("jpackage failed:\n" + output.strip());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Publishing was interrupted.");
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path each : walk.toList()) {
                Path target = to.resolve(from.relativize(each).toString());
                if (Files.isDirectory(each)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(each, target);
                }
            }
        }
    }

    /** Deletes a folder and everything in it. */
    public static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        List<Path> all;
        try (Stream<Path> walk = Files.walk(folder)) {
            all = new ArrayList<>(walk.toList());
        }
        all.sort(Comparator.reverseOrder());
        for (Path each : all) {
            Files.delete(each);
        }
    }
}
