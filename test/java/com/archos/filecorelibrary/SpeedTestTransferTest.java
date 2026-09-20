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

import static org.junit.Assume.assumeTrue;

import android.app.Application;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.archos.filecorelibrary.webdav.WebdavUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Real-network speed test comparing the throughput of Nova's local httpproxy ({@link StreamOverHttp},
 * the same class {@code SmbProxy} uses to feed the player) across the remote-file implementations
 * Nova ships: jcifs-ng, smbj, sftp (jsch), sshj, webdav and webdavs.
 *
 * This is an opt-in diagnostic, not a real unit test: it talks to real servers and is skipped
 * unless a CSV of server URLs/credentials is supplied via a system property, since that data is
 * sensitive and must never be committed to the repository.
 *
 * CSV format: one {@code url,user,password} row per line (comma-separated, blank lines and lines
 * starting with '#' are ignored). The scheme of each url selects the implementation under test:
 * <pre>
 *   smb://host/share/path/file      -> jcifs-ng
 *   smbj://host/share/path/file     -> smbj
 *   sftp://host/path/file           -> sftp (jsch)
 *   sshj://host/path/file           -> sshj
 *   webdav://host/path/file         -> webdav (http)
 *   webdavs://host/path/file        -> webdav (https)
 * </pre>
 * List the same file twice, once under {@code smb://} and once under {@code smbj://} (or
 * {@code sftp://} / {@code sshj://}), to compare the two implementations of a given protocol
 * head to head against the same server.
 *
 * Usage:
 * <pre>
 *   ./gradlew :FileCoreLibrary:testDebugUnitTest --tests "*SpeedTestTransferTest" \
 *       -Dnova.test.speedtestCsv=/absolute/path/to/servers.csv \
 *       -Dnova.test.speedtestUpstreamBufferBytes=1048576
 * </pre>
 * The upstream buffer defaults to the general-purpose value (81920 bytes), independently
 * of the playback policy (1 MiB for jcifs). Changing this
 * property varies the proxy's reads from the remote backend; the HTTP client buffer and
 * the proxy's socket-write buffer remain fixed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SpeedTestTransferTest {

    private static final String CSV_PATH_PROPERTY = "nova.test.speedtestCsv";
    private static final String UPSTREAM_BUFFER_PROPERTY = "nova.test.speedtestUpstreamBufferBytes";
    private static final int BUFFER_SIZE = 256 * 1024;
    private int upstreamBufferSize;

    private static final class Row {
        final Uri uri;
        final String user;
        final String password;
        Row(Uri uri, String user, String password) {
            this.uri = uri;
            this.user = user;
            this.password = password;
        }
    }

    private static final class Result {
        final String protocol;
        final Uri uri;
        final long bytes;
        final double seconds;
        final String error;
        Result(String protocol, Uri uri, long bytes, double seconds, String error) {
            this.protocol = protocol;
            this.uri = uri;
            this.bytes = bytes;
            this.seconds = seconds;
            this.error = error;
        }
    }

    @Before
    public void setUp() {
        String csvPath = System.getProperty(CSV_PATH_PROPERTY);
        assumeTrue("Set -D" + CSV_PATH_PROPERTY + "=/absolute/path/to/servers.csv to run this test",
                csvPath != null && new File(csvPath).isFile());

        upstreamBufferSize = Integer.parseInt(System.getProperty(UPSTREAM_BUFFER_PROPERTY,
                Integer.toString(StreamOverHttp.DEFAULT_UPSTREAM_BUFFER_SIZE)));
        if (upstreamBufferSize <= 0) throw new IllegalArgumentException(UPSTREAM_BUFFER_PROPERTY + " must be positive");

        Application app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        JcifsUtils.getInstance(app);
        SmbjUtils.getInstance(app);
        SshjUtils.getInstance(app);
        WebdavUtils.getInstance(app);
    }

    @Test
    public void compareTransferRates() throws Exception {
        String csvPath = System.getProperty(CSV_PATH_PROPERTY);
        List<Row> rows = parseCsv(csvPath);
        assumeTrue("CSV file has no usable rows: " + csvPath, !rows.isEmpty());

        System.out.println(String.format(Locale.US, "HTTP upstream buffer: %d bytes; HTTP client buffer: %d bytes",
                upstreamBufferSize, BUFFER_SIZE));

        List<Result> results = new ArrayList<>();
        for (Row row : rows) {
            results.add(runOne(row));
        }

        printResults(results);
    }

    private Result runOne(Row row) {
        String scheme = row.uri.getScheme();
        String key = row.uri.toString();
        if (row.user != null && !row.user.isEmpty()) {
            NetworkCredentialsDatabase.getInstance().addCredential(
                    new NetworkCredentialsDatabase.Credential(row.user, row.password, key, "", true));
        }

        StreamOverHttp stream = null;
        try {
            String mimeType = MimeUtils.guessMimeTypeFromExtension(row.uri.getLastPathSegment());
            stream = new StreamOverHttp(row.uri, mimeType, upstreamBufferSize);
            Uri localUri = stream.getEncodedUri(FileUtils.getName(row.uri));

            HttpURLConnection conn = (HttpURLConnection) new URL(localUri.toString()).openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(30_000);
            conn.setRequestMethod("GET");

            long start = System.nanoTime();
            long total = 0;
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                while ((n = is.read(buf)) != -1) total += n;
            }
            double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
            conn.disconnect();
            return new Result(scheme, row.uri, total, seconds, null);
        } catch (Exception e) {
            return new Result(scheme, row.uri, 0, 0, e.toString());
        } finally {
            if (stream != null) stream.close();
        }
    }

    private void printResults(List<Result> results) {
        System.out.println();
        System.out.println(String.format(Locale.US, "%-10s %-55s %14s %10s %12s",
                "PROTOCOL", "URL", "BYTES", "SECONDS", "MiB/s"));
        for (Result r : results) {
            if (r.error != null) {
                System.out.println(String.format(Locale.US, "%-10s %-55s %s",
                        r.protocol, r.uri, "FAILED: " + r.error));
            } else {
                double mbPerSec = r.seconds > 0 ? (r.bytes / (1024.0 * 1024.0)) / r.seconds : 0;
                System.out.println(String.format(Locale.US, "%-10s %-55s %14d %10.2f %12.2f",
                        r.protocol, r.uri, r.bytes, r.seconds, mbPerSec));
            }
        }
        System.out.println();
    }

    private List<Row> parseCsv(String path) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",", -1);
                if (parts.length < 1 || parts[0].trim().isEmpty()) continue;
                Uri uri = Uri.parse(parts[0].trim());
                String user = parts.length > 1 ? parts[1].trim() : "";
                String password = parts.length > 2 ? parts[2].trim() : "";
                rows.add(new Row(uri, user, password));
            }
        }
        return rows;
    }
}
