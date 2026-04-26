package com.github.talktoissue.commands;

import com.github.copilot.sdk.CopilotClient;
import com.github.talktoissue.App;
import com.github.talktoissue.SelfIntroSession;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "intro",
    description = "Generate a self-introduction PowerPoint slide deck that explains this tool's role."
)
public class IntroCommand implements Callable<Integer> {

    @ParentCommand
    private App parent;

    @Option(names = {"-f", "--file"},
            description = "Path to a file containing project context (e.g., README.md)")
    private Path contextFile;

    @Option(names = {"-o", "--output-dir"}, defaultValue = ".",
            description = "Directory to write the generated PPTX (default: current directory)")
    private File outputDir;

    @Override
    public Integer call() throws Exception {
        String model = parent.getModel();
        boolean dryRun = parent.isDryRun();

        // Gather context: use provided file or fall back to README.md / pom.xml
        String context;
        if (contextFile != null) {
            if (!Files.exists(contextFile)) {
                System.err.println("Error: Context file not found: " + contextFile);
                return 1;
            }
            context = Files.readString(contextFile);
        } else {
            // Auto-detect project context from working directory — gather comprehensive info
            File workingDir = parent.getWorkingDir();
            if (workingDir == null) {
                workingDir = new File(".");
            }
            StringBuilder sb = new StringBuilder();

            // README
            Path readme = workingDir.toPath().resolve("README.md");
            if (Files.exists(readme)) {
                sb.append("=== README.md ===\n").append(Files.readString(readme)).append("\n\n");
            }

            // pom.xml
            Path pom = workingDir.toPath().resolve("pom.xml");
            if (Files.exists(pom)) {
                sb.append("=== pom.xml (dependencies & build) ===\n").append(Files.readString(pom)).append("\n\n");
            }

            // Scan Java source files to extract class/session/tool structure
            Path srcDir = workingDir.toPath().resolve("src/main/java");
            if (Files.isDirectory(srcDir)) {
                sb.append("=== Java Source Files ===\n");
                try (var walk = Files.walk(srcDir)) {
                    walk.filter(p -> p.toString().endsWith(".java"))
                        .sorted()
                        .forEach(p -> {
                            String relativePath = srcDir.relativize(p).toString();
                            sb.append("- ").append(relativePath).append("\n");
                            // Include first ~30 lines (class declaration, fields, key info)
                            try {
                                var lines = Files.readAllLines(p);
                                int limit = Math.min(lines.size(), 30);
                                for (int i = 0; i < limit; i++) {
                                    sb.append("  ").append(lines.get(i)).append("\n");
                                }
                                sb.append("  ...\n\n");
                            } catch (Exception ignored) {}
                        });
                }
            }

            if (sb.isEmpty()) {
                System.err.println("Error: No context found. Provide --file or ensure README.md exists in --working-dir.");
                return 1;
            }
            context = sb.toString();
        }

        if (dryRun) {
            System.out.println("=== DRY-RUN MODE ===");
        }

        try (var client = new CopilotClient()) {
            client.start().get();
            System.out.println("Copilot SDK started.");

            System.out.println("\n=== Generating self-introduction slides ===");
            var session = new SelfIntroSession(client, model, outputDir, dryRun);
            String outputPath = session.run(context);

            if (outputPath != null) {
                System.out.println("\nSlide deck generated: " + outputPath);
            } else {
                System.out.println("\nSlide generation completed.");
            }
        }

        return 0;
    }
}
