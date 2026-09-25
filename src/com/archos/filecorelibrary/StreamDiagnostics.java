// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.filecorelibrary;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in API-level counters. These do not measure protocol packets or library read-ahead. */
final class StreamDiagnostics {
    final java.util.Set<String> backends = java.util.concurrent.ConcurrentHashMap.newKeySet();
    final AtomicLong returnedAfterCancellation = new AtomicLong();
    final AtomicLong readCalls = new AtomicLong(), requestedBytes = new AtomicLong();
    final AtomicLong returnedBytes = new AtomicLong(), deliveredBytes = new AtomicLong();
    final AtomicLong opened = new AtomicLong(), closed = new AtomicLong(), closeFailures = new AtomicLong();
    final AtomicLong cancelled = new AtomicLong(), maxCancellationNanos = new AtomicLong();

    InputStream track(InputStream input, java.util.function.BooleanSupplier cancelled) {
        opened.incrementAndGet();
        return new FilterInputStream(input) {
            final AtomicBoolean closing = new AtomicBoolean();
            @Override public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) < 0 ? -1 : b[0] & 255;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                readCalls.incrementAndGet();
                requestedBytes.addAndGet(len);
                int n = in.read(b, off, len);
                if (n > 0) {
                    returnedBytes.addAndGet(n);
                    if (cancelled.getAsBoolean()) returnedAfterCancellation.addAndGet(n);
                }
                return n;
            }
            @Override public void close() throws IOException {
                if (!closing.compareAndSet(false, true)) return;
                try { in.close(); }
                catch (IOException | RuntimeException e) { closeFailures.incrementAndGet(); throw e; }
                finally { closed.incrementAndGet(); }
            }
        };
    }

    @Override public String toString() {
        return "backends=" + backends + " backendReads=" + readCalls + " requested=" + requestedBytes
                + " returned=" + returnedBytes + " delivered=" + deliveredBytes
                + " returnedAfterCancel=" + returnedAfterCancellation
                + " active=" + (opened.get() - closed.get()) + " opened=" + opened + " closed=" + closed + " closeFailures=" + closeFailures
                + " cancelled=" + cancelled + " maxCancelMs=" + maxCancellationNanos.get() / 1_000_000;
    }
}
