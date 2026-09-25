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

import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.archos.filecorelibrary.webdav.WebdavUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

/**
 * Real-network speed test comparing the throughput of Nova's local httpproxy ({@link StreamOverHttp},
 * the same class {@code SmbProxy} uses to feed the player) across the remote-file implementations
 * Nova ships: jcifs-ng, smbj, sftp (jsch), sshj, webdav and webdavs.
 *
 * This is an opt-in diagnostic, not a real unit test: it talks to real servers and is skipped
 * unless a CSV of server URLs/credentials is supplied via a system property, since that data is
 * sensitive and must never be committed to the repository.
 *
 * CSV format: one {@code url,user,password[,expectedBytes[,sha256]]} row per line (comma-separated, blank lines and lines
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

    @Test
    public void compareTransferRates() throws Exception {
        java.util.function.Function<String, String> args = key -> System.getProperty("nova.test." + key);
        String path = args.apply("speedtestCsv");
        assumeTrue("Supply speedtestCsv to run real-server diagnostics", path != null);
        if (!new File(path).isFile()) throw new AssertionError("Configured CSV does not exist");
        Application app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        JcifsUtils.getInstance(app);
        SmbjUtils.getInstance(app);
        SshjUtils.getInstance(app);
        WebdavUtils.getInstance(app);
        TransferDiagnostic.run(path, args);
    }
}
