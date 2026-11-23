package crawler.publish;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Publishes a project from the command line (publish.ps1 runs this):
 *
 * <pre>
 *   java crawler.publish.PublishTool "C:\Games\My Game" [output folder]
 * </pre>
 *
 * The output folder defaults to {@code dist}. The game ends up in {@code <output>\<game name>\}.
 */
public final class PublishTool {
    private PublishTool() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: PublishTool <project folder> [output folder]");
            System.exit(2);
        }
        Path project = Path.of(args[0]).toAbsolutePath();
        Path output = Path.of(args.length > 1 ? args[1] : "dist").toAbsolutePath();
        Path work = null;
        int status = 0;
        try {
            Publisher.Template template = Publisher.bundledTemplate();
            if (template == null) {
                work = Files.createTempDirectory("crawler-publish");
                template = Publisher.buildTemplate(work, System.out::println);
            }
            Path program = Publisher.publish(project, output, template, System.out::println);
            System.out.println();
            System.out.println("Players run " + program.getFileName() + ". Zip the whole '"
                    + program.getParent().getFileName() + "' folder to share it.");
        } catch (IOException e) {
            System.err.println(e.getMessage());
            status = 1;
        } finally {
            if (work != null) {
                try {
                    Publisher.deleteTree(work);
                } catch (IOException e) {
                    System.err.println("Couldn't tidy up " + work + ": " + e.getMessage());
                }
            }
        }
        System.exit(status);
    }
}
