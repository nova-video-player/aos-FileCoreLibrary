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
import java.util.Objects;

/** Per-open policy. Legacy editor APIs and production playback defaults are unchanged. */
public final class ReadOptions {
    public enum Purpose { DEFAULT, METADATA, PLAYBACK }
    public enum SmbjAccess { RANDOM, SEQUENTIAL, UNSPECIFIED }
    public static final ReadOptions DEFAULT = new ReadOptions(Purpose.DEFAULT, -1, false);
    public static final ReadOptions METADATA = new ReadOptions(Purpose.METADATA, -1, false);
    public final Purpose purpose;
    public final long maxBytes;
    public final boolean boundedRange;
    /** Zero means library/application default. Overrides are for controlled diagnostics. */
    public final int sftpDepth, requestBytes;
    public final SmbjAccess smbjAccess;

    public ReadOptions(Purpose purpose, long maxBytes, boolean boundedRange) {
        this(purpose, maxBytes, boundedRange, 0, 0, SmbjAccess.RANDOM);
    }

    public ReadOptions(Purpose purpose, long maxBytes, boolean boundedRange,
                       int sftpDepth, int requestBytes, SmbjAccess smbjAccess) {
        if (maxBytes < -1 || sftpDepth < 0 || sftpDepth > 64 || requestBytes < 0
                || requestBytes > 1024 * 1024) throw new IllegalArgumentException("Invalid read policy");
        this.purpose = Objects.requireNonNull(purpose);
        this.maxBytes = maxBytes;
        this.boundedRange = boundedRange;
        this.sftpDepth = sftpDepth;
        this.requestBytes = requestBytes;
        this.smbjAccess = Objects.requireNonNull(smbjAccess);
    }

    public boolean conservative() { return purpose == Purpose.METADATA || boundedRange; }
    public int pipelineDepth() { return conservative() ? 1 : sftpDepth == 0 ? 16 : sftpDepth; }

    /** Clamp before reaching the backend, including skip; close still owns the backend. */
    public InputStream wrap(InputStream input) {
        Objects.requireNonNull(input);
        if (maxBytes < 0 && requestBytes == 0) return input;
        return new FilterInputStream(input) {
            private long remaining = maxBytes;
            private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
            private void checkOpen() throws IOException {
                if (closed.get()) throw new IOException("Stream closed");
            }
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                checkOpen();
                if (len == 0) return 0;
                if (remaining == 0) return -1;
                if (remaining > 0) len = (int) Math.min(remaining, len);
                if (requestBytes > 0) len = Math.min(requestBytes, len);
                int n = in.read(b, off, len);
                if (n > 0 && remaining > 0) remaining -= n;
                return n;
            }
            @Override public long skip(long n) throws IOException {
                checkOpen();
                if (n <= 0 || remaining == 0) return 0;
                long skipped = in.skip(remaining < 0 ? n : Math.min(n, remaining));
                if (remaining > 0) remaining -= skipped;
                return skipped;
            }
            @Override public int available() throws IOException {
                checkOpen();
                int n = in.available();
                return remaining < 0 ? n : (int) Math.min(n, remaining);
            }
            @Override public boolean markSupported() { return false; }
            @Override public void mark(int limit) { }
            @Override public void reset() throws IOException { throw new IOException("mark/reset unsupported"); }
            @Override public void close() throws IOException {
                if (closed.compareAndSet(false, true)) in.close();
            }
        };
    }
}
