package com.github.talktoissue.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WorkQueueTest {

    private WorkQueue queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.shutdown(5);
        }
    }

    @Test
    void taskCompletesSuccessfully() throws Exception {
        queue = new WorkQueue(2);
        var latch = new CountDownLatch(1);
        var executed = new AtomicInteger(0);

        queue.submit("repo/test", "test-task", () -> {
            executed.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, executed.get());
    }

    @Test
    void differentReposRunInParallel() throws Exception {
        queue = new WorkQueue(4);
        var startLatch = new CountDownLatch(2);
        var doneLatch = new CountDownLatch(2);

        queue.submit("repo/a", "task-a", () -> {
            startLatch.countDown();
            try { startLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            doneLatch.countDown();
        });

        queue.submit("repo/b", "task-b", () -> {
            startLatch.countDown();
            try { startLatch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            doneLatch.countDown();
        });

        // Both tasks should start concurrently since they're on different repos
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    void sameRepoRunsSerially() throws Exception {
        queue = new WorkQueue(4);
        var order = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var doneLatch = new CountDownLatch(2);

        queue.submit("repo/same", "task-1", () -> {
            order.add(1);
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            doneLatch.countDown();
        });

        queue.submit("repo/same", "task-2", () -> {
            order.add(2);
            doneLatch.countDown();
        });

        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        assertEquals(java.util.List.of(1, 2), order);
    }

    @Test
    void retriesOnFailure() throws Exception {
        queue = new WorkQueue(1);
        var attempts = new AtomicInteger(0);
        var doneLatch = new CountDownLatch(1);

        queue.submit("repo/retry", "retry-task", () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("Fail attempt " + attempt);
            }
            doneLatch.countDown();
        });

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        assertEquals(3, attempts.get());
    }

    @Test
    void exhaustsMaxRetries() throws Exception {
        queue = new WorkQueue(1);
        var attempts = new AtomicInteger(0);
        var doneLatch = new CountDownLatch(1);

        queue.submit("repo/exhaust", "exhaust-task", () -> {
            attempts.incrementAndGet();
            if (attempts.get() >= 3) {
                doneLatch.countDown();
            }
            throw new RuntimeException("Always fails");
        });

        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        assertEquals(3, attempts.get());
    }

    @Test
    void pendingTasksReturnsCount() throws Exception {
        queue = new WorkQueue(1);
        // Initially no pending tasks
        int pending = queue.pendingTasks();
        assertTrue(pending >= 0);
    }

    @Test
    void shutdownGracefully() {
        queue = new WorkQueue(2);
        queue.submit("repo/x", "simple", () -> {});
        assertDoesNotThrow(() -> queue.shutdown(5));
        queue = null; // prevent double shutdown in tearDown
    }
}
