// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.filecorelibrary;

import static org.junit.Assert.*;
import android.content.Context;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StreamOverHttpCancellationTest {
    private enum Stage { METADATA, OPEN, READ, CLOSE }
    private Context context;

    @Before public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        // The singleton outlives Robolectric's application between test methods.
        Field smbContext = SmbjUtils.class.getDeclaredField("mContext");
        smbContext.setAccessible(true);
        smbContext.set(null, context);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("pref_smbj", false).commit();
        assertFalse(SmbjUtils.isSMBjEnabled());
    }

    private static final class Proxy extends StreamOverHttp {
        final Stage stage;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        volatile Thread owner;
        volatile Thread closer;
        volatile boolean interrupted;

        Proxy(String scheme, Stage stage) throws IOException {
            this(scheme, stage, ReadMode.DEFAULT);
        }

        Proxy(String scheme, Stage stage, ReadMode mode) throws IOException {
            super(Uri.parse(scheme + "://server/share/video.mkv"), "video/mp4", mode);
            this.stage = stage;
        }

        void block(Stage current) throws IOException {
            if (stage != current) return;
            owner = Thread.currentThread();
            entered.countDown();
            try {
                if (!proceed.await(5, TimeUnit.SECONDS)) throw new IOException("Test backend timed out");
            } catch (InterruptedException e) {
                interrupted = true;
                throw new IOException("Backend interrupted", e);
            }
        }

        @Override MetaFile2 getMetaFile(Uri uri) throws Exception {
            block(Stage.METADATA);
            return null;
        }

        @Override FileEditor getFileEditor(Uri uri) {
            return new FileEditor(uri) {
                @Override public boolean exists() { return true; }
                @Override public long length() { return 64; }
                @Override public InputStream getInputStream() throws Exception { return getInputStream(0); }
                @Override public InputStream getInputStream(long from) throws Exception {
                    if (opens.incrementAndGet() > 1) return new ByteArrayInputStream(new byte[(int)(64 - from)]);
                    block(Stage.OPEN);
                    return new InputStream() {
                        @Override public int read() throws IOException { throw new IOException("Unexpected single-byte read"); }
                        @Override public int read(byte[] b, int off, int len) throws IOException {
                            block(Stage.READ);
                            return Math.min(64, len);
                        }
                        @Override public void close() throws IOException {
                            closes.incrementAndGet();
                            closer = Thread.currentThread();
                            try { block(Stage.CLOSE); } finally { closed.countDown(); }
                        }
                    };
                }
            };
        }
    }

    private static Socket request(Proxy proxy, String range) throws Exception {
        Uri uri = proxy.getUri("video.mkv");
        Socket socket = new Socket("127.0.0.1", uri.getPort());
        socket.setSoTimeout(2000);
        String headers = "GET /video.mkv HTTP/1.0\r\n" + (range == null ? "" : "Range: " + range + "\r\n") + "\r\n";
        socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
        return socket;
    }

    private static int sessions(Proxy proxy) throws Exception {
        Field lock = StreamOverHttp.class.getDeclaredField("mSessionLock");
        Field sessions = StreamOverHttp.class.getDeclaredField("mSessions");
        lock.setAccessible(true);
        sessions.setAccessible(true);
        synchronized (lock.get(proxy)) { return ((Set<?>)sessions.get(proxy)).size(); }
    }

    private static void finish(Proxy proxy) throws Exception {
        proxy.proceed.countDown();
        if (proxy.owner != null) {
            proxy.owner.join(2000);
            assertFalse("Worker must finish cleanup", proxy.owner.isAlive());
        }
    }

    private void assertCooperative(Stage stage) throws Exception {
        assertCooperative(stage, StreamOverHttp.ReadMode.DEFAULT);
    }

    private void assertCooperative(Stage stage, StreamOverHttp.ReadMode mode) throws Exception {
        Proxy proxy = new Proxy("smb", stage, mode);
        try (Socket socket = request(proxy, null)) {
            assertTrue(proxy.entered.await(2, TimeUnit.SECONDS));
            proxy.close();
            socket.getInputStream().readAllBytes();
            assertFalse(proxy.interrupted);
            assertTrue(proxy.owner.isAlive());
            assertEquals("Pending backend operation retains its admission slot", 1, sessions(proxy));
            if (stage != Stage.CLOSE) assertEquals(0, proxy.closes.get());
            finish(proxy);
            assertFalse(proxy.interrupted);
            assertEquals(0, sessions(proxy));
            if (stage == Stage.METADATA) {
                assertEquals("Cancellation must prevent opening after metadata returns", 0, proxy.opens.get());
            } else {
                assertEquals(1, proxy.closes.get());
                assertSame("The I/O owner must close the stream", proxy.owner, proxy.closer);
            }
        } finally { proxy.proceed.countDown(); proxy.close(); }
    }

    @Test public void cancelDuringMetadataDoesNotInterruptJcifs() throws Exception { assertCooperative(Stage.METADATA); }
    @Test public void cancelDuringOpenClosesLateInputOnOwner() throws Exception { assertCooperative(Stage.OPEN); }
    @Test public void cancelDuringReadDefersCloseToOwner() throws Exception { assertCooperative(Stage.READ); }
    @Test public void cancelDuringCleanupDoesNotInterruptClose() throws Exception { assertCooperative(Stage.CLOSE); }

    @Test public void playbackCancellationDefersCloseToOwner() throws Exception {
        assertCooperative(Stage.READ, StreamOverHttp.ReadMode.PLAYBACK);
    }

    @Test public void replacementRangeCompletesWhileOldReadDrains() throws Exception {
        assertReplacement(StreamOverHttp.ReadMode.DEFAULT);
    }

    @Test public void playbackReplacementCompletesWhileOldReadDrains() throws Exception {
        assertReplacement(StreamOverHttp.ReadMode.PLAYBACK);
    }

    private void assertReplacement(StreamOverHttp.ReadMode mode) throws Exception {
        Proxy proxy = new Proxy("smb", Stage.READ, mode);
        try (Socket first = request(proxy, null)) {
            assertTrue(proxy.entered.await(2, TimeUnit.SECONDS));
            try (Socket second = request(proxy, "bytes=16-31")) {
                String response = new String(second.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(response, response.startsWith("HTTP/1.0 206"));
                assertTrue(response, response.contains("Content-Range: bytes 16-31/64"));
                assertEquals(16, response.substring(response.indexOf("\r\n\r\n") + 4).length());
            }
            String firstResponse = new String(first.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(firstResponse, firstResponse.endsWith("\r\n\r\n"));
            assertFalse(proxy.interrupted);
            assertEquals(0, proxy.closes.get());
            finish(proxy);
            assertEquals(1, proxy.closes.get());
            assertSame(proxy.owner, proxy.closer);
        } finally { proxy.proceed.countDown(); proxy.close(); }
    }

    private void assertInterruptible(String scheme) throws Exception {
        Proxy proxy = new Proxy(scheme, Stage.READ);
        try (Socket socket = request(proxy, null)) {
            assertTrue(proxy.entered.await(2, TimeUnit.SECONDS));
            proxy.close();
            assertTrue(proxy.closed.await(2, TimeUnit.SECONDS));
            proxy.owner.join(2000);
            assertTrue("Other backends retain interrupt-based cancellation", proxy.interrupted);
            assertEquals(1, proxy.closes.get());
        } finally { proxy.proceed.countDown(); proxy.close(); }
    }

    @Test public void otherBackendsStillInterrupt() throws Exception { assertInterruptible("sftp"); }
    @Test public void alternateSmbBackendStillInterrupts() throws Exception {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("pref_smbj", true).commit();
        assertInterruptible("smb");
    }
}
