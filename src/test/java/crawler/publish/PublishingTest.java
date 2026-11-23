package crawler.publish;

import crawler.Main;
import crawler.core.GamePack;
import crawler.core.ProjectFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Encrypted game packs, loading games from them, and publishing. */
class PublishingTest {
    private static final List<String> SAMPLE_FILES =
            List.of("project.txt", "maps/b1f.txt", "party.txt", "classes.txt", "enemies.txt");

    @TempDir
    Path folder;

    /** A copy of the sample project in a folder, plus a hidden file that must not be packed. */
    private Path sampleProject() throws IOException {
        Path project = folder.resolve("Sample Quest");
        ProjectFiles sample = ProjectFiles.classpath("/sample/");
        for (String file : SAMPLE_FILES) {
            Path target = project.resolve(file);
            Files.createDirectories(target.getParent());
            Files.write(target, sample.read(file));
        }
        Files.createDirectories(project.resolve(".git"));
        Files.writeString(project.resolve(".git/HEAD"), "secret");
        return project;
    }

    /** A stand-in for the packaged editor's "Sample Quest" program. */
    private Publisher.Template fakeTemplate() throws IOException {
        Path root = folder.resolve("template");
        Files.createDirectories(root.resolve("app"));
        Files.createDirectories(root.resolve("runtime/bin"));
        Files.writeString(root.resolve("Sample Quest.exe"), "launcher");
        Files.writeString(root.resolve("app/Sample Quest.cfg"), "[Application]\napp.mainclass=crawler.Main\n");
        Files.writeString(root.resolve("app/crawler.jar"), "code");
        Files.writeString(root.resolve("runtime/bin/java.dll"), "runtime");
        Publisher.Template template = new Publisher.Template(root);
        assertTrue(template.isValid());
        return template;
    }

    @Test
    void packsRoundTripAndHideTheirContents() throws IOException {
        Map<String, byte[]> files = Map.of("project.txt", "name = Secret Quest\nstart = maps/a.txt\n".getBytes(),
                "assets/a.png", new byte[]{1, 2, 3});
        byte[] pack = GamePack.pack(files);
        Map<String, byte[]> back = GamePack.unpack(pack);
        assertEquals(files.keySet(), back.keySet());
        assertArrayEquals(new byte[]{1, 2, 3}, back.get("assets/a.png"));
        assertFalse(new String(pack, StandardCharsets.ISO_8859_1).contains("Secret Quest"), "the text is encrypted");
        assertFalse(java.util.Arrays.equals(pack, GamePack.pack(files)), "every pack gets its own key");
    }

    @Test
    void changedOrForeignFilesAreRefused() throws IOException {
        byte[] pack = GamePack.pack(Map.of("project.txt", "name = X".getBytes()));
        byte[] tampered = pack.clone();
        tampered[tampered.length - 5] ^= 1;
        IOException changed = assertThrows(IOException.class, () -> GamePack.unpack(tampered));
        assertTrue(changed.getMessage().contains("damaged or has been changed"), changed.getMessage());

        assertTrue(assertThrows(IOException.class, () -> GamePack.unpack("PK just a zip".getBytes()))
                .getMessage().contains("isn't a game data file"));
        byte[] newer = pack.clone();
        newer[4] = 99;
        assertTrue(assertThrows(IOException.class, () -> GamePack.unpack(newer)).getMessage().contains("newer version"));
    }

    @Test
    void aPackedProjectPlaysAndLeavesHiddenFilesOut() throws IOException {
        Path project = sampleProject();
        Path pak = folder.resolve("game.pak");
        Files.write(pak, GamePack.pack(project));
        Main.Loaded game = Main.load(pak);
        assertEquals("Sample Quest - B1F - The Old Cellars", game.title());
        assertEquals(3, game.content().enemies().size());
        assertNull(GamePack.read(pak).read(".git/HEAD"));
        assertNotNull(GamePack.read(pak).read("maps/b1f.txt"));
    }

    @Test
    void publishingCopiesTheProgramAndEncryptsTheProject() throws IOException {
        Path project = sampleProject();
        Path output = folder.resolve("out");
        List<String> progress = new ArrayList<>();
        Path program = Publisher.publish(project, output, fakeTemplate(), progress::add);

        Path game = output.resolve("Sample Quest");
        assertEquals(game.resolve("Sample Quest.exe"), program);
        assertEquals("launcher", Files.readString(program));
        assertTrue(Files.isRegularFile(game.resolve("app/Sample Quest.cfg")), "settings named after the program");
        assertTrue(Files.isRegularFile(game.resolve("app/crawler.jar")));
        assertTrue(Files.isRegularFile(game.resolve("runtime/bin/java.dll")));
        assertFalse(Files.exists(game.resolve("app/maps")), "no loose project files");
        assertFalse(Files.exists(game.resolve("app/game")));
        assertEquals(game.resolve("app/game.pak"), Main.bundledGame(game.resolve("app")));
        assertEquals("Sample Quest - B1F - The Old Cellars", Main.load(Main.bundledGame(game.resolve("app"))).title());
        assertFalse(progress.isEmpty());

        // Publishing again replaces the earlier copy.
        Files.writeString(game.resolve("app/extra.txt"), "left over");
        Publisher.publish(project, output, fakeTemplate(), m -> { });
        assertTrue(Files.isRegularFile(program));
        assertFalse(Files.exists(game.resolve("app/extra.txt")));
    }

    @Test
    void aProjectWithAProblemIsNotPublished() throws IOException {
        Path project = sampleProject();
        Files.delete(project.resolve("maps/b1f.txt"));
        Path output = folder.resolve("out");
        IOException e = assertThrows(IOException.class, () -> Publisher.publish(project, output, fakeTemplate(), m -> { }));
        assertTrue(e.getMessage().contains("can't be published yet") && e.getMessage().contains("maps/b1f.txt"), e.getMessage());
        assertFalse(Files.exists(output.resolve("Sample Quest")), "nothing was written");
    }

    @Test
    void anotherFolderInTheWayIsNotDeleted() throws IOException {
        Path project = sampleProject();
        Path output = folder.resolve("out");
        Files.createDirectories(output.resolve("Sample Quest"));
        Files.writeString(output.resolve("Sample Quest/notes.txt"), "mine");
        IOException e = assertThrows(IOException.class, () -> Publisher.publish(project, output, fakeTemplate(), m -> { }));
        assertTrue(e.getMessage().contains("isn't a published game"), e.getMessage());
        assertEquals("mine", Files.readString(output.resolve("Sample Quest/notes.txt")));
    }

    @Test
    void programNamesAreSafeFileNames() {
        assertEquals("Test Quest", Publisher.programName("Test Quest"));
        assertEquals("Rats Revenge", Publisher.programName("Rat's Revenge!"));
        assertEquals("Game", Publisher.programName("???"));
    }

    @Test
    void aFolderOfClassesBecomesARunnableJar() throws IOException {
        Path classes = folder.resolve("classes");
        Files.createDirectories(classes.resolve("crawler"));
        Files.writeString(classes.resolve("crawler/Main.class"), "bytes");
        Files.createDirectories(classes.resolve("sample"));
        Files.writeString(classes.resolve("sample/project.txt"), "name = S");
        Path jar = folder.resolve("crawler.jar");
        Publisher.writeJar(classes, jar);
        try (JarFile file = new JarFile(jar.toFile())) {
            assertNotNull(file.getEntry("crawler/Main.class"));
            assertNotNull(file.getEntry("sample/project.txt"));
            assertEquals("crawler.Main", file.getManifest().getMainAttributes().getValue("Main-Class"));
        }
    }
}
