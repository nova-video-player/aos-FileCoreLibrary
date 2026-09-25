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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class OwnedStreamsTest {
    @Test public void closesHandleEvenWhenInputCloseFailsAndPreservesBothErrors() throws Exception {
        AtomicInteger handles = new AtomicInteger();
        InputStream stream = OwnedStreams.input(new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException { throw new IOException("stream"); }
        }, () -> { handles.incrementAndGet(); throw new IOException("handle"); });
        IOException failure = assertThrows(IOException.class, stream::close);
        assertEquals("stream", failure.getMessage());
        assertEquals("handle", failure.getSuppressed()[0].getMessage());
        stream.close();
        assertEquals(1, handles.get());
        assertThrows(IOException.class, () -> stream.read(new byte[4]));
    }

    @Test public void outputFailureStillClosesHandleExactlyOnce() throws Exception {
        AtomicInteger handles = new AtomicInteger();
        OutputStream stream = OwnedStreams.output(new ByteArrayOutputStream() {
            @Override public void close() throws IOException { throw new IOException("flush failed"); }
        }, handles::incrementAndGet);
        assertThrows(IOException.class, stream::close);
        stream.close();
        assertEquals(1, handles.get());
        assertThrows(IOException.class, () -> stream.write(new byte[4]));
    }

    @Test public void closeDoesNotMutateHandleDuringAnActiveRead() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        AtomicInteger handles = new AtomicInteger();
        InputStream stream = OwnedStreams.input(new InputStream() {
            @Override public int read() throws IOException {
                reading.countDown();
                try {
                    if (!finishRead.await(5, TimeUnit.SECONDS)) throw new IOException("test timeout");
                } catch (InterruptedException e) { throw new IOException(e); }
                assertEquals(0, handles.get());
                return 42;
            }
        }, handles::incrementAndGet);
        FutureTask<Integer> reader = new FutureTask<>(stream::read);
        FutureTask<Void> closer = new FutureTask<>(() -> { closing.countDown(); stream.close(); return null; });
        new Thread(reader).start();
        try {
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            new Thread(closer).start();
            assertTrue(closing.await(5, TimeUnit.SECONDS));
        } finally { finishRead.countDown(); }
        assertEquals(Integer.valueOf(42), reader.get(5, TimeUnit.SECONDS));
        closer.get(5, TimeUnit.SECONDS);
        assertEquals(1, handles.get());
    }
}
