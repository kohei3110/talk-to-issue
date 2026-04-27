package com.github.talktoissue.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Executes pipeline tasks in parallel with bounded concurrency.
 * Individual task failures are isolated — one failing task does not cancel others.
 */
public class PipelineExecutor {

    private final ExecutorService executor;
    private final int concurrency;

    public PipelineExecutor(int concurrency) {
        this.concurrency = concurrency;
        this.executor = Executors.newFixedThreadPool(concurrency);
    }

    /**
     * Run all tasks in parallel and collect results, skipping failures.
     *
     * @param tasks  the tasks to execute concurrently
     * @param <T>    the result type
     * @return results from all successful tasks (order not guaranteed)
     */
    public <T> List<T> runAllAndCollect(List<Callable<T>> tasks) {
        var futures = new ArrayList<CompletableFuture<T>>(tasks.size());
        for (var task : tasks) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }

        // Wait for all to complete (successful or not)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(ex -> null).join();

        var results = new ArrayList<T>();
        for (var future : futures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                // Individual failure already logged by caller's task; skip
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.err.println("[PipelineExecutor] Task failed: " + cause.getMessage());
            }
        }
        return results;
    }

    /**
     * Run all tasks in parallel, ignoring return values. Failures are logged.
     *
     * @param tasks the tasks to execute concurrently
     */
    public void runAll(List<Runnable> tasks) {
        var futures = new ArrayList<CompletableFuture<Void>>(tasks.size());
        for (var task : tasks) {
            futures.add(CompletableFuture.runAsync(task, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(ex -> null).join();

        for (var future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.err.println("[PipelineExecutor] Task failed: " + cause.getMessage());
            }
        }
    }

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Graceful shutdown. Waits for in-progress tasks, then forces shutdown.
     *
     * @param timeoutSeconds max time to wait for completion
     */
    public void shutdown(int timeoutSeconds) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
