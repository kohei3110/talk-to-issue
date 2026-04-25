package com.github.talktoissue.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Async work queue for processing webhook-triggered pipeline runs.
 * Supports per-repository locking to prevent concurrent runs and
 * exponential backoff retry on failure.
 */
public class WorkQueue {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 2000;

    private final ExecutorService executor;
    private final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();

    public WorkQueue(int concurrency) {
        this.executor = Executors.newFixedThreadPool(concurrency);
    }

    /**
     * Submit a task for asynchronous execution with per-repository locking and retry.
     *
     * @param repoKey  repository key for locking (e.g. "owner/repo")
     * @param taskName human-readable task description for logging
     * @param task     the task to execute
     */
    public void submit(String repoKey, String taskName, Runnable task) {
        executor.submit(() -> {
            var lock = repoLocks.computeIfAbsent(repoKey, k -> new ReentrantLock());
            lock.lock();
            try {
                executeWithRetry(taskName, task);
            } finally {
                lock.unlock();
            }
        });
    }

    private void executeWithRetry(String taskName, Runnable task) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("[WorkQueue] Running: " + taskName + " (attempt " + attempt + ")");
                task.run();
                System.out.println("[WorkQueue] Completed: " + taskName);
                return;
            } catch (Exception e) {
                System.err.println("[WorkQueue] Failed: " + taskName + " — " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                    System.err.println("[WorkQueue] Retrying in " + delay + "ms...");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    System.err.println("[WorkQueue] Exhausted retries for: " + taskName);
                }
            }
        }
    }

    /**
     * Graceful shutdown: wait for in-progress tasks to complete.
     *
     * @param timeoutSeconds max time to wait for completion
     */
    public void shutdown(int timeoutSeconds) {
        System.out.println("[WorkQueue] Shutting down...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                System.err.println("[WorkQueue] Forcing shutdown after timeout.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int pendingTasks() {
        if (executor instanceof java.util.concurrent.ThreadPoolExecutor tpe) {
            return (int) (tpe.getTaskCount() - tpe.getCompletedTaskCount());
        }
        return -1;
    }
}
