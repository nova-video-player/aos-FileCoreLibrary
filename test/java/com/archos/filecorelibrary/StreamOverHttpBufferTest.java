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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.jcifs.JcifsFileEditor;
import com.archos.filecorelibrary.localstorage.JavaFile2;
import com.archos.filecorelibrary.localstorage.LocalStorageFileEditor;
import com.archos.filecorelibrary.sftp.SftpFileEditor;
import com.archos.filecorelibrary.smbj.SmbjFileEditor;
import com.archos.filecorelibrary.sshj.SshjFileEditor;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TemporaryFolder;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StreamOverHttpBufferTest {
    private static final int MIB = 1024 * 1024;
    private static final Uri SMB_URI = Uri.parse("smb://server/share/video.mkv");

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void playbackBatchesJcifsButMetadataAndSidecarsStaySmall() throws Exception {
        StreamOverHttp playback = new StreamOverHttp(SMB_URI, null, StreamOverHttp.ReadMode.PLAYBACK);
        StreamOverHttp metadata = new StreamOverHttp(SMB_URI, null);
        try {
            FileEditor editor = new JcifsFileEditor(SMB_URI);
            assertFirstReadSize(MIB, playback.upstreamBufferSize(editor, true));
            assertFirstReadSize(81920, playback.upstreamBufferSize(editor, false));
            assertFirstReadSize(81920, metadata.upstreamBufferSize(editor, true));
        } finally {
            playback.close();
            metadata.close();
        }
    }

    @Test
    public void playbackRespectsActualBackendIncludingSmbjWithSmbScheme() throws Exception {
        StreamOverHttp proxy = new StreamOverHttp(SMB_URI, null, StreamOverHttp.ReadMode.PLAYBACK);
        try {
            FileEditor[] editors = {
                    new SmbjFileEditor(SMB_URI),
                    new SftpFileEditor(Uri.parse("sftp://server/video.mkv")),
                    new SshjFileEditor(Uri.parse("sshj://server/video.mkv")),
                    new LocalStorageFileEditor(Uri.parse("file:///video.mkv"), null)
            };
            for (FileEditor editor : editors) {
                assertFirstReadSize(81920, proxy.upstreamBufferSize(editor, true));
            }
        } finally {
            proxy.close();
        }
    }

    @Test
    public void metaFileConstructorPreservesPlaybackIntent() throws Exception {
        MetaFile2 file = new JavaFile2(temporaryFolder.newFile("video.mkv"));
        StreamOverHttp playback = new StreamOverHttp(file, null, StreamOverHttp.ReadMode.PLAYBACK);
        StreamOverHttp metadata = new StreamOverHttp(file, null);
        try {
            FileEditor editor = new JcifsFileEditor(SMB_URI);
            assertFirstReadSize(MIB, playback.upstreamBufferSize(editor, true));
            assertFirstReadSize(81920, metadata.upstreamBufferSize(editor, true));
        } finally {
            playback.close();
            metadata.close();
        }
    }

    @Test
    public void explicitBenchmarkSizeStillAppliesToOtherBackends() throws Exception {
        StreamOverHttp proxy = new StreamOverHttp(SMB_URI, null, 512 * 1024);
        try {
            assertFirstReadSize(512 * 1024, proxy.upstreamBufferSize(new SmbjFileEditor(SMB_URI), true));
        } finally {
            proxy.close();
        }
    }

    @Test
    public void shortRangeDoesNotFetchFollowingFileBytes() throws Exception {
        RecordingInputStream source = new RecordingInputStream(2 * MIB);
        InputStream input = StreamOverHttp.bufferResponseInput(source, 317, MIB);
        assertArrayEquals(expected(317), readAll(input));
        assertEquals(Arrays.asList(317), source.requests);
        assertEquals(317, source.position);
    }

    @Test
    public void finalRefillIsLimitedAfterFullPlaybackBlock() throws Exception {
        RecordingInputStream source = new RecordingInputStream(3 * MIB);
        InputStream input = StreamOverHttp.bufferResponseInput(source, MIB + 317, MIB);
        assertArrayEquals(expected(MIB + 317), readAll(input));
        assertEquals(Arrays.asList(MIB, 317), source.requests);
        assertEquals(MIB + 317, source.position);
    }

    @Test
    public void shortBackendReadsRemainBoundedAndPreserveContent() throws Exception {
        RecordingInputStream source = new RecordingInputStream(MIB);
        source.maxResult = 37;
        InputStream input = StreamOverHttp.bufferResponseInput(source, 317, MIB);
        assertArrayEquals(expected(317), readAll(input));
        assertEquals(317, source.position);
        assertEquals(Integer.valueOf(21), source.requests.get(source.requests.size() - 1));
    }

    @Test
    public void emptyResponseDoesNotTouchBackend() throws Exception {
        RecordingInputStream source = new RecordingInputStream(MIB);
        InputStream input = StreamOverHttp.bufferResponseInput(source, 0, MIB);
        assertEquals(0, input.read(new byte[1], 0, 0));
        assertEquals(-1, input.read());
        assertTrue(source.requests.isEmpty());
    }

    @Test
    public void unknownLengthReadsUntilBackendEof() throws Exception {
        RecordingInputStream source = new RecordingInputStream(MIB + 317);
        InputStream input = StreamOverHttp.bufferResponseInput(source, -1, MIB);
        assertArrayEquals(expected(MIB + 317), readAll(input));
        assertEquals(Integer.valueOf(MIB), source.requests.get(0));
    }

    @Test
    public void earlyBackendEofIsNotPaddedToAdvertisedLength() throws Exception {
        RecordingInputStream source = new RecordingInputStream(317);
        InputStream input = StreamOverHttp.bufferResponseInput(source, 500, MIB);
        assertArrayEquals(expected(317), readAll(input));
        assertEquals(Arrays.asList(500, 183), source.requests);
    }

    @Test
    public void realHttpRangeAndFullResponseHaveExactContent() throws Exception {
        ArchosUtils.setGlobalContext(ApplicationProvider.getApplicationContext());
        File file = temporaryFolder.newFile("video.mkv");
        byte[] content = expected(100000);
        Files.write(file.toPath(), content);
        StreamOverHttp proxy = new StreamOverHttp(new JavaFile2(file), "video/x-matroska",
                StreamOverHttp.ReadMode.PLAYBACK);
        try {
            assertHttpResponse(proxy, file.getName(), null, 200, content, null);
            assertHttpResponse(proxy, file.getName(), "bytes=123-439", 206,
                    Arrays.copyOfRange(content, 123, 440), "bytes 123-439/100000");
            assertHttpResponse(proxy, file.getName(), "bytes=-17", 206,
                    Arrays.copyOfRange(content, content.length - 17, content.length), "bytes 99983-99999/100000");
        } finally {
            proxy.close();
        }
    }

    private static void assertHttpResponse(StreamOverHttp proxy, String name, String range,
            int status, byte[] expected, String contentRange) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(proxy.getEncodedUri(name).toString()).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        if (range != null) connection.setRequestProperty("Range", range);
        try {
            assertEquals(status, connection.getResponseCode());
            assertEquals(expected.length, connection.getContentLength());
            assertEquals(contentRange, connection.getHeaderField("Content-Range"));
            try (InputStream input = connection.getInputStream()) {
                assertArrayEquals(expected, readAll(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void assertFirstReadSize(int expectedSize, int bufferSize) throws Exception {
        RecordingInputStream source = new RecordingInputStream(2 * MIB);
        InputStream input = StreamOverHttp.bufferResponseInput(source, 2 * MIB, bufferSize);
        assertEquals(8192, input.read(new byte[8192]));
        assertEquals(Arrays.asList(expectedSize), source.requests);
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static byte[] expected(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte)(i * 31 + 7);
        return bytes;
    }

    private static final class RecordingInputStream extends InputStream {
        final List<Integer> requests = new ArrayList<>();
        final int length;
        int position;
        int maxResult = Integer.MAX_VALUE;

        RecordingInputStream(int length) { this.length = length; }

        @Override public int read() {
            requests.add(1);
            return position == length ? -1 : (position++ * 31 + 7) & 255;
        }

        @Override public int read(byte[] bytes, int off, int len) {
            requests.add(len);
            if (position == length) return -1;
            int count = Math.min(Math.min(len, maxResult), length - position);
            for (int i = 0; i < count; i++) bytes[off + i] = (byte)(position++ * 31 + 7);
            return count;
        }
    }
}
