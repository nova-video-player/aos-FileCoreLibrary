// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.filecorelibrary;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

public class BackgroundInitializationTest {
    private final ExecutorService workers = Executors.newCachedThreadPool();

    @After
    public void stopWorkers() throws Exception {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    public void concurrentCallersShareOneInitializationAndSeePublishedState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        int[] credentials = { 0 };
        BackgroundInitialization init = new BackgroundInitialization(() -> {
            calls.incrementAndGet();
            entered.countDown();
            release.await();
            credentials[0] = 42;
            return null;
        }, workers);
        init.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        List<Future<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            requests.add(workers.submit(() -> {
                init.await(5, TimeUnit.SECONDS);
                return credentials[0];
            }));
        }
        assertFalse(init.isReady());
        for (Future<Integer> request : requests) assertFalse(request.isDone());
        release.countDown();
        for (Future<Integer> request : requests) assertEquals(42, (int) request.get(5, TimeUnit.SECONDS));
        init.start();
        assertTrue(init.isReady());
        assertEquals(1, calls.get());
    }

    @Test
    public void timeoutDoesNotCancelOrDuplicateInitializer() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        BackgroundInitialization init = new BackgroundInitialization(() -> {
            calls.incrementAndGet();
            release.await();
            return null;
        }, workers);
        for (int i = 0; i < 2; i++) {
            try {
                init.await(10, TimeUnit.MILLISECONDS);
                fail("Expected timeout");
            } catch (IOException e) {
                assertTrue(e.getCause() instanceof java.util.concurrent.TimeoutException);
            }
        }
        release.countDown();
        init.await(5, TimeUnit.SECONDS);
        assertEquals(1, calls.get());
    }

    @Test
    public void interruptionOnlyCancelsTheCaller() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BackgroundInitialization init = new BackgroundInitialization(() -> {
            entered.countDown();
            release.await();
            return null;
        }, workers);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                init.await(5, TimeUnit.SECONDS);
                failure.set(new AssertionError("Wait should be interrupted"));
            } catch (IOException e) {
                if (!(e.getCause() instanceof InterruptedException) || !Thread.currentThread().isInterrupted()) {
                    failure.set(e);
                }
            }
        });
        caller.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        caller.interrupt();
        caller.join(5000);
        assertFalse(caller.isAlive());
        assertNull(failure.get());
        assertFalse(init.isReady());
        release.countDown();
        init.await(5, TimeUnit.SECONDS);
        assertTrue(init.isReady());
    }

    @Test
    public void failedAttemptPropagatesCauseAndNextRequestRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        IOException failure = new IOException("Cannot load credentials");
        BackgroundInitialization init = new BackgroundInitialization(() -> {
            if (calls.incrementAndGet() == 1) throw failure;
            return null;
        }, workers);
        try {
            init.await(5, TimeUnit.SECONDS);
            fail("Expected initialization failure");
        } catch (IOException e) {
            assertSame(failure, e.getCause());
        }
        assertFalse(init.isReady());
        init.await(5, TimeUnit.SECONDS);
        assertTrue(init.isReady());
        assertEquals(2, calls.get());
    }
}
