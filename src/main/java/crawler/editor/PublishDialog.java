package crawler.editor;

import crawler.publish.Publisher;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Publishes the project in the background, with a progress window, then says where the game is. */
final class PublishDialog {
    private PublishDialog() {
    }

    static void run(Frame owner, Path projectRoot, Path output) {
        JDialog dialog = new JDialog(owner, "Publishing", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JLabel status = new JLabel("Starting...");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        status.setAlignmentX(0);
        bar.setAlignmentX(0);
        panel.add(status);
        panel.add(javax.swing.Box.createVerticalStrut(10));
        panel.add(bar);
        panel.setPreferredSize(new Dimension(460, 90));
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);

        SwingWorker<Path, String> worker = new SwingWorker<>() {
            private Path work;

            @Override
            protected Path doInBackground() throws Exception {
                Publisher.Template template = Publisher.bundledTemplate();
                if (template == null) {
                    work = Files.createTempDirectory("crawler-publish");
                    template = Publisher.buildTemplate(work, this::publish);
                }
                return Publisher.publish(projectRoot, output, template, this::publish);
            }

            @Override
            protected void process(List<String> messages) {
                status.setText(messages.get(messages.size() - 1));
            }

            @Override
            protected void done() {
                if (work != null) {
                    try {
                        Publisher.deleteTree(work);
                    } catch (IOException e) {
                        System.err.println("Couldn't tidy up " + work + ": " + e.getMessage());
                    }
                }
                dialog.dispose();
            }
        };
        worker.execute();
        dialog.setVisible(true); // waits here until done() closes it

        try {
            Path program = worker.get();
            String[] options = {"Open Folder", "OK"};
            int answer = JOptionPane.showOptionDialog(owner,
                    "The game is ready:\n" + program + "\n\nIts files are encrypted inside game.pak. "
                            + "Zip the whole '" + program.getParent().getFileName() + "' folder to share it.",
                    "Published", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (answer == 0) {
                Desktop.getDesktop().open(program.getParent().toFile());
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            JOptionPane.showMessageDialog(owner, "Couldn't publish the game:\n" + cause.getMessage(),
                    "Publishing", JOptionPane.ERROR_MESSAGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | UnsupportedOperationException e) {
            // Opening the folder isn't possible here; the message already said where it is.
        }
    }
}
