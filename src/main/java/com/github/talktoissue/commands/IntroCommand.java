package com.github.talktoissue.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "intro", description = "Show an introduction to the codebase.")
public class IntroCommand implements Callable<Integer> {
    @Option(names = {"--src-dir"}, description = "Source directory to scan.")
    private String srcDir = "src/main/java";

    @Override
    public Integer call() throws Exception {
        Path srcDirPath = Paths.get(srcDir);
        StringBuilder sb = new StringBuilder();
        Files.walk(srcDirPath)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> {
                String relativePath = srcDirPath.relativize(p).toString();
                sb.append("- ").append(relativePath).append("\n");
                // Include first ~30 lines (class declaration, fields, key info)
                try {
                    var lines = Files.readAllLines(p);
                    int limit = Math.min(lines.size(), 30);
                    for (int i = 0; i < limit; i++) {
                        sb.append("  ").append(lines.get(i)).append("\n");
                    }
                    sb.append("  ...\n\n");
                } catch (Exception e) {
                    System.err.println("[IntroCommand] Failed to read file: " + p);
                    e.printStackTrace();
                }
            });
        System.out.println(sb.toString());
        return 0;
    }
}
