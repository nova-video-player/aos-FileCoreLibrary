// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.filecorelibrary;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One shared initialization attempt. A failed attempt can be retried by the next request. */
final class BackgroundInitialization {
    private final Callable<Void> initializer;
    private final Executor executor;
    private FutureTask<Void> task;
    private volatile boolean ready;

    BackgroundInitialization(Callable<Void> initializer, Executor executor) {
        this.initializer = initializer;
        this.executor = executor;
    }

    private synchronized FutureTask<Void> getTask() {
        if (task == null || (task.isDone() && !ready)) {
            task = new FutureTask<>(() -> {
                initializer.call();
                ready = true;
                return null;
            });
            executor.execute(task);
        }
        return task;
    }

    void start() {
        getTask();
    }

    boolean isReady() {
        return ready;
    }

    void await(long timeout, TimeUnit unit) throws IOException {
        try {
            getTask().get(timeout, unit);
        } catch (InterruptedException e) {
            // The caller owns its wait, never the shared initializer.
            Thread.currentThread().interrupt();
            throw new IOException("Network initialization wait interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Network initialization failed", e.getCause());
        } catch (TimeoutException e) {
            // A timeout must not start another initializer while this one is still running.
            throw new IOException("Network initialization timed out", e);
        }
    }
}
