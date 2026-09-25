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

package com.archos.filecorelibrary.sshj;

import static org.junit.Assert.*;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.LoggerFactory;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.SessionFactory;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPEngine;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SshjStreamLifecycleTest {
    private static final Uri URI = Uri.parse("sshj://example.test/movie.mkv");
    private TrackingSsh ssh;
    private TrackingSftp sftp;

    private static class TrackingSsh extends SSHClient {
        int closes;
        @Override public boolean isConnected() { return true; }
        @Override public void close() { closes++; }
    }

    private static class TrackingSftp extends SFTPClient {
        int closes, handles;
        boolean failClose;
        TrackingSftp(SFTPEngine engine) { super(engine); }
        @Override public RemoteFile open(String path) {
            return new RemoteFile(engine, path, new byte[0]) {
                @Override public void close() throws IOException {
                    handles++;
                    if (failClose) throw new IOException("remote close failed");
                }
            };
        }
        @Override public void close() { closes++; }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "startSession": return stub(Session.class);
                case "startSubsystem": return stub(Session.Subsystem.class);
                case "getLoggerFactory": return LoggerFactory.DEFAULT;
                case "getInputStream": return new ByteArrayInputStream(new byte[0]);
                case "getOutputStream": return new ByteArrayOutputStream();
                default: return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<Credential, Object> cache(String name) throws Exception {
        Field field = SshjUtils.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Credential, Object>) field.get(null);
    }

    @Before public void setUp() throws Exception {
        SshjUtils.getInstance(ApplicationProvider.getApplicationContext());
        Credential credential = new Credential("test", "", "sshj://example.test", "", true);
        NetworkCredentialsDatabase.getInstance().addCredential(credential);
        ssh = new TrackingSsh();
        sftp = new TrackingSftp(new SFTPEngine(stub(SessionFactory.class)));
        cache("sshClients").put(credential, ssh);
        cache("sftpClients").put(credential, sftp);
    }

    @After public void tearDown() throws Exception {
        cache("sshClients").clear();
        cache("sftpClients").clear();
    }

    @Test public void bothReadOverloadsCloseTheirRemoteHandlesExactlyOnce() throws Exception {
        SshjFileEditor editor = new SshjFileEditor(URI);
        InputStream first = editor.getInputStream();
        InputStream second = editor.getInputStream(123);
        first.close(); first.close(); second.close(); second.close();
        assertEquals(2, sftp.handles);
        assertEquals(0, ssh.closes);
        assertEquals(0, sftp.closes);
    }

    @Test public void handleCloseFailureDoesNotDisconnectOtherStreams() throws Exception {
        InputStream stream = new SshjFileEditor(URI).getInputStream();
        sftp.failClose = true;
        assertThrows(IOException.class, stream::close);
        stream.close();
        assertEquals(1, sftp.handles);
        assertEquals(0, ssh.closes);
        assertEquals(0, sftp.closes);
    }

    @Test public void disconnectInvalidatesTheSftpClientAsWellAsItsTransport() throws Exception {
        SshjUtils.disconnectSshClient(URI);
        assertTrue(cache("sshClients").isEmpty());
        assertTrue(cache("sftpClients").isEmpty());
        assertEquals(1, ssh.closes);
        assertEquals(1, sftp.closes);
    }
}
