package com.github.talktoissue.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GitWorktreeManagerTest {

    @TempDir
    Path tempDir;

    private GitWorktreeManager manager;

    @AfterEach
    void tearDown() {
        if (manager != null) {
            manager.cleanupAll();
        }
    }

    @Test
    void createWorktreeRequiresGitRepo() {
        // Non-git directory should fail
        manager = new GitWorktreeManager(tempDir.toFile());
        assertThrows(Exception.class, () -> manager.createWorktree("test"));
    }

    @Test
    void cleanupAllOnEmptyManagerDoesNotThrow() {
        manager = new GitWorktreeManager(tempDir.toFile());
        assertDoesNotThrow(() -> manager.cleanupAll());
    }

    @Test
    void removeWorktreeOnNonexistentDirDoesNotThrow() {
        manager = new GitWorktreeManager(tempDir.toFile());
        assertDoesNotThrow(() -> manager.removeWorktree(new File("/nonexistent/path")));
    }

    @Test
    void createAndRemoveWorktreeInGitRepo() throws Exception {
        // Initialize a real git repo in tempDir
        initGitRepo(tempDir.toFile());

        manager = new GitWorktreeManager(tempDir.toFile());
        File worktree = manager.createWorktree("issue-1");

        assertNotNull(worktree);
        assertTrue(worktree.isDirectory());
        // The worktree should contain a .git file (not a directory, since worktrees use .git file pointing to main repo)
        assertTrue(new File(worktree, ".git").exists());

        manager.removeWorktree(worktree);
        // After removal, directory should no longer exist
        assertFalse(worktree.isDirectory());
    }

    @Test
    void createMultipleWorktrees() throws Exception {
        initGitRepo(tempDir.toFile());

        manager = new GitWorktreeManager(tempDir.toFile());
        File wt1 = manager.createWorktree("issue-1");
        File wt2 = manager.createWorktree("issue-2");

        assertNotNull(wt1);
        assertNotNull(wt2);
        assertNotEquals(wt1.getAbsolutePath(), wt2.getAbsolutePath());
        assertTrue(wt1.isDirectory());
        assertTrue(wt2.isDirectory());
    }

    @Test
    void cleanupAllRemovesAllWorktrees() throws Exception {
        initGitRepo(tempDir.toFile());

        manager = new GitWorktreeManager(tempDir.toFile());
        File wt1 = manager.createWorktree("issue-10");
        File wt2 = manager.createWorktree("issue-20");

        assertTrue(wt1.isDirectory());
        assertTrue(wt2.isDirectory());

        manager.cleanupAll();

        assertFalse(wt1.isDirectory());
        assertFalse(wt2.isDirectory());
    }

    private void initGitRepo(File dir) throws Exception {
        run(dir, "git", "init");
        run(dir, "git", "config", "user.email", "test@test.com");
        run(dir, "git", "config", "user.name", "Test");
        // Create an initial commit so main branch exists
        new File(dir, "README.md").createNewFile();
        run(dir, "git", "add", ".");
        run(dir, "git", "commit", "-m", "init");
        // Ensure branch is named "main"
        run(dir, "git", "branch", "-M", "main");
    }

    private void run(File dir, String... cmd) throws Exception {
        var process = new ProcessBuilder(cmd)
            .directory(dir)
            .redirectErrorStream(true)
            .start();
        process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", cmd));
        }
    }
}
