package com.github.talktoissue.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages git worktrees for parallel implementation sessions.
 * Each worktree provides an isolated working directory on the main branch,
 * allowing multiple issues to be implemented concurrently without filesystem conflicts.
 */
public class GitWorktreeManager {

    private final File mainWorkingDir;
    private final ConcurrentLinkedQueue<File> activeWorktrees = new ConcurrentLinkedQueue<>();

    public GitWorktreeManager(File mainWorkingDir) {
        this.mainWorkingDir = mainWorkingDir;
    }

    /**
     * Create a new git worktree branching from main.
     * The worktree is placed in a temp directory with a unique name.
     *
     * @param suffix a human-readable suffix for the worktree directory name (e.g. "issue-42")
     * @return the worktree directory (a new, independent working directory)
     * @throws IOException if worktree creation fails
     */
    public File createWorktree(String suffix) throws IOException {
        Path tempDir = Files.createTempDirectory("talk-to-issue-wt-" + suffix + "-");
        // git worktree add requires the target directory to not exist, so delete the empty temp dir
        Files.delete(tempDir);

        String worktreePath = tempDir.toAbsolutePath().toString();
        int exitCode = runGit("worktree", "add", "--detach", worktreePath, "main");
        if (exitCode != 0) {
            throw new IOException("Failed to create git worktree at " + worktreePath);
        }

        File worktreeDir = tempDir.toFile();
        activeWorktrees.add(worktreeDir);
        System.out.println("[GitWorktreeManager] Created worktree: " + worktreePath);
        return worktreeDir;
    }

    /**
     * Remove a single worktree and prune stale entries.
     *
     * @param worktreeDir the worktree directory to remove
     */
    public void removeWorktree(File worktreeDir) {
        try {
            runGit("worktree", "remove", "--force", worktreeDir.getAbsolutePath());
            activeWorktrees.remove(worktreeDir);
            System.out.println("[GitWorktreeManager] Removed worktree: " + worktreeDir.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[GitWorktreeManager] Failed to remove worktree " + worktreeDir + ": " + e.getMessage());
            // Fall back to prune
            try {
                runGit("worktree", "prune");
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Remove all active worktrees. Call on shutdown.
     */
    public void cleanupAll() {
        System.out.println("[GitWorktreeManager] Cleaning up " + activeWorktrees.size() + " worktree(s)...");
        File wt;
        while ((wt = activeWorktrees.poll()) != null) {
            try {
                runGit("worktree", "remove", "--force", wt.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("[GitWorktreeManager] Cleanup failed for " + wt + ": " + e.getMessage());
            }
        }
        try {
            runGit("worktree", "prune");
        } catch (Exception ignored) {
        }
    }

    private int runGit(String... args) throws IOException {
        var command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        try {
            var process = new ProcessBuilder(command)
                .directory(mainWorkingDir)
                .redirectErrorStream(true)
                .start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }
    }
}
