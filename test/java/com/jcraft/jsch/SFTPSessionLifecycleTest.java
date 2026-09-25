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

package com.jcraft.jsch;

import static org.junit.Assert.*;

import android.net.Uri;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.filecorelibrary.sftp.SftpFileEditor;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SFTPSessionLifecycleTest {
    private static final Uri URI = Uri.parse("sftp://example.test/movie.mkv");

    private static class TrackingSession extends Session {
        int disconnects;
        TrackingSession() throws JSchException { super(new JSch(), "test", "example.test", 22); }
        @Override public void disconnect() { disconnects++; }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(SFTPSession manager, String name) throws Exception {
        Field field = SFTPSession.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<K, V>) field.get(manager);
    }

    private static Channel channel(Session session) {
        Channel channel = new ChannelSftp();
        channel.setSession(session);
        return channel;
    }

    @Test public void retiringCachedIdleSessionDisconnectsIt() throws Exception {
        SFTPSession manager = new SFTPSession();
        TrackingSession session = new TrackingSession();
        Channel channel = channel(session);
        map(manager, "currentSessions").put(new Credential("test", "", "sftp://example.test", "", true), session);
        map(manager, "usedSessions").put(session, new HashSet<>(java.util.Collections.singleton(channel)));
        manager.releaseSession(channel);
        assertEquals(0, session.disconnects); // cached and reusable
        assertFalse(map(manager, "usedSessions").containsKey(session));
        manager.removeSession(URI);
        assertEquals(1, session.disconnects);
    }

    @Test public void retirementPreservesActiveChannelsUntilLastRelease() throws Exception {
        SFTPSession manager = new SFTPSession();
        TrackingSession session = new TrackingSession();
        Channel first = channel(session), second = channel(session);
        map(manager, "currentSessions").put(new Credential("test", "", "sftp://example.test", "", true), session);
        map(manager, "usedSessions").put(session, new HashSet<>(java.util.Arrays.asList(first, second)));
        manager.removeSession(URI);
        assertEquals(0, session.disconnects);
        manager.releaseSession(first);
        assertEquals(0, session.disconnects);
        manager.releaseSession(second);
        manager.releaseSession(second);
        assertEquals(1, session.disconnects);
        assertTrue(map(manager, "usedSessions").isEmpty());
    }

    @Test public void failedRemoteOpenReleasesAcquiredChannel() throws Exception {
        int[] cleanup = new int[2];
        ChannelSftp channel = new ChannelSftp() {
            @Override public java.io.InputStream get(String path, SftpProgressMonitor monitor, long offset)
                    throws SftpException { throw new SftpException(2, "missing"); }
            @Override public void disconnect() { cleanup[0]++; }
        };
        SFTPSession manager = new SFTPSession() {
            @Override public Channel getSFTPChannel(Uri uri) { return channel; }
            @Override public void releaseSession(Channel released) { assertSame(channel, released); cleanup[1]++; }
        };
        Field singleton = SFTPSession.class.getDeclaredField("sshSession");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        singleton.set(null, manager);
        try {
            assertThrows(SftpException.class, () -> new SftpFileEditor(URI).getInputStream(10));
            assertArrayEquals(new int[] {1, 1}, cleanup);
        } finally { singleton.set(null, previous); }
    }
}
