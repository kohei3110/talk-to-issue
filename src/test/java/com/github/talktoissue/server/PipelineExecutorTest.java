package com.github.talktoissue.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorTest {

    private PipelineExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown(5);
        }
    }

    @Test
    void runAllAndCollectReturnsResults() {
        executor = new PipelineExecutor(3);
        var tasks = List.<Callable<Integer>>of(
            () -> 1,
            () -> 2,
            () -> 3
        );

        var results = executor.runAllAndCollect(tasks);
        assertEquals(3, results.size());
        assertTrue(results.containsAll(List.of(1, 2, 3)));
    }

    @Test
    void runAllAndCollectSkipsFailures() {
        executor = new PipelineExecutor(3);
        var tasks = List.<Callable<String>>of(
            () -> "ok",
            () -> { throw new RuntimeException("boom"); },
            () -> "also ok"
        );

        var results = executor.runAllAndCollect(tasks);
        assertEquals(2, results.size());
        assertTrue(results.contains("ok"));
        assertTrue(results.contains("also ok"));
    }

    @Test
    void runAllAndCollectExecutesInParallel() throws Exception {
        executor = new PipelineExecutor(5);
        var startedLatch = new CountDownLatch(3);
        var releaseLatch = new CountDownLatch(1);

        var tasks = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 3; i++) {
            final int id = i;
            tasks.add(() -> {
                startedLatch.countDown();
                releaseLatch.await(5, TimeUnit.SECONDS);
                return id;
            });
        }

        // Run in a separate thread so we can check latch
        var resultHolder = new CopyOnWriteArrayList<Integer>();
        var doneLatch = new CountDownLatch(1);
        new Thread(() -> {
            resultHolder.addAll(executor.runAllAndCollect(tasks));
            doneLatch.countDown();
        }).start();

        // All 3 tasks should start concurrently
        assertTrue(startedLatch.await(5, TimeUnit.SECONDS), "All tasks should start in parallel");

        releaseLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertEquals(3, resultHolder.size());
    }

    @Test
    void runAllExecutesTasks() throws Exception {
        executor = new PipelineExecutor(2);
        var counter = new AtomicInteger(0);

        var tasks = new ArrayList<Runnable>();
        for (int i = 0; i < 5; i++) {
            tasks.add(counter::incrementAndGet);
        }

        executor.runAll(tasks);
        assertEquals(5, counter.get());
    }

    @Test
    void runAllIsolatesFailures() {
        executor = new PipelineExecutor(3);
        var counter = new AtomicInteger(0);

        var tasks = List.<Runnable>of(
            counter::incrementAndGet,
            () -> { throw new RuntimeException("fail"); },
            counter::incrementAndGet
        );

        assertDoesNotThrow(() -> executor.runAll(tasks));
        assertEquals(2, counter.get());
    }

    @Test
    void getConcurrencyReturnsConfiguredValue() {
        executor = new PipelineExecutor(7);
        assertEquals(7, executor.getConcurrency());
    }

    @Test
    void emptyTaskListReturnsEmptyResults() {
        executor = new PipelineExecutor(2);
        var results = executor.runAllAndCollect(List.of());
        assertTrue(results.isEmpty());
    }

    @Test
    void shutdownIsIdempotent() {
        executor = new PipelineExecutor(1);
        assertDoesNotThrow(() -> {
            executor.shutdown(1);
            executor.shutdown(1);
        });
        executor = null; // prevent @AfterEach double shutdown
    }
}
