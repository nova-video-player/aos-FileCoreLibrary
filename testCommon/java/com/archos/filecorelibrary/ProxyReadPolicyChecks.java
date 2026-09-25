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
import android.net.Uri;
import java.io.*;
import java.net.*;
import org.junit.Test;

public abstract class ProxyReadPolicyChecks {
    private static class Proxy extends StreamOverHttp {
        volatile ReadOptions openedWith;
        final byte[] bytes = new byte[64];
        Proxy(ReadMode mode) throws IOException { super(Uri.parse("file:///fixture.mp4"), "video/mp4", mode); }
        @Override MetaFile2 getMetaFile(Uri uri) { return null; }
        @Override FileEditor getFileEditor(Uri uri) {
            return new FileEditor(uri) {
                @Override public boolean exists() { return true; }
                @Override public long length() { return bytes.length; }
                @Override public InputStream getInputStream() { return getInputStream(0); }
                @Override public InputStream getInputStream(long from) {
                    return new ByteArrayInputStream(bytes, (int) from, bytes.length - (int) from);
                }
                @Override public InputStream getInputStream(long from, ReadOptions options) throws Exception {
                    openedWith = options;
                    return super.getInputStream(from, options);
                }
            };
        }
    }
    private void request(Proxy proxy, String range, int expected) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(proxy.getUri("fixture.mp4").toString()).openConnection();
        conn.setConnectTimeout(2000); conn.setReadTimeout(2000);
        conn.setRequestProperty("Range", range);
        try {
            assertEquals(206, conn.getResponseCode());
            assertEquals(expected, conn.getContentLengthLong());
            int bytes = 0;
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[128];
                for (int n; (n = in.read(buffer)) > 0;) bytes += n;
            }
            assertEquals(expected, bytes);
            assertTrue(proxy.awaitIdle(2000));
        } finally { conn.disconnect(); }
    }
    @Test public void finitePlaybackRangeBecomesConservativeAtBackend() throws Exception {
        Proxy proxy = new Proxy(StreamOverHttp.ReadMode.PLAYBACK);
        try {
            request(proxy, "bytes=16-31", 16);
            assertEquals(16, proxy.openedWith.maxBytes);
            assertTrue(proxy.openedWith.conservative());
            request(proxy, "bytes=16-", 48);
            assertEquals(48, proxy.openedWith.maxBytes);
            assertFalse(proxy.openedWith.conservative());
        } finally { proxy.close(); }
    }
    @Test public void metadataTailUsesConservativeBackendReads() throws Exception {
        Proxy proxy = new Proxy(StreamOverHttp.ReadMode.DEFAULT);
        try {
            request(proxy, "bytes=16-", 48);
            assertEquals(ReadOptions.Purpose.METADATA, proxy.openedWith.purpose);
            assertTrue(proxy.openedWith.conservative());
        } finally { proxy.close(); }
    }
}
