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

import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Streams that own a protocol handle as well as the library's stream adapter. */
public final class OwnedStreams {
    private OwnedStreams() { }

    public static InputStream input(InputStream stream, Closeable owner) {
        return new FilterInputStream(stream) {
            private boolean closed;

            private void checkOpen() throws IOException {
                if (closed) throw new IOException("Stream closed");
            }

            // Serialize close with reads: SFTP stream adapters mutate their request queues.
            @Override public synchronized int read() throws IOException {
                checkOpen();
                return in.read();
            }
            @Override public synchronized int read(byte[] b, int off, int len) throws IOException {
                checkOpen();
                return in.read(b, off, len);
            }
            @Override public synchronized long skip(long n) throws IOException {
                checkOpen();
                return in.skip(n);
            }
            @Override public synchronized int available() throws IOException {
                checkOpen();
                return in.available();
            }
            @Override public synchronized void mark(int limit) {
                if (!closed) in.mark(limit);
            }
            @Override public synchronized void reset() throws IOException {
                checkOpen();
                in.reset();
            }
            @Override public synchronized void close() throws IOException {
                if (closed) return;
                closed = true;
                // Try-with-resources preserves the stream exception and suppresses owner failures.
                try (Closeable handle = owner) {
                    in.close();
                }
            }
        };
    }

    /** Abort a thread-safe request before waiting for its non-thread-safe stream adapter. */
    public static InputStream cancellableInput(InputStream stream, Closeable owner, Runnable cancel) {
        return new FilterInputStream(input(stream, owner)) {
            private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
            @Override public void close() throws IOException {
                if (!closed.compareAndSet(false, true)) return;
                try { cancel.run(); }
                finally { in.close(); }
            }
        };
    }

    public static OutputStream output(OutputStream stream, Closeable owner) {
        return new FilterOutputStream(stream) {
            private boolean closed;

            private void checkOpen() throws IOException {
                if (closed) throw new IOException("Stream closed");
            }
            @Override public synchronized void write(int b) throws IOException {
                checkOpen();
                out.write(b);
            }
            @Override public synchronized void write(byte[] b, int off, int len) throws IOException {
                checkOpen();
                out.write(b, off, len);
            }
            @Override public synchronized void flush() throws IOException {
                checkOpen();
                out.flush();
            }
            @Override public synchronized void close() throws IOException {
                if (closed) return;
                closed = true;
                try (Closeable handle = owner) {
                    out.close();
                }
            }
        };
    }
}
