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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.archos.filecorelibrary.webdav.WebdavUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * On-device counterpart of {@code com.archos.filecorelibrary.SpeedTestTransferTest} (the
 * {@code test/} Robolectric host test). Robolectric runs on the host JVM, so it cannot be used to
 * measure real device behavior (e.g. whether Conscrypt/ARM crypto extensions actually speed up
 * SFTP/SMB transfers on a given SoC). This instrumented version runs on the real device/emulator
 * under ART, with the device's real JCE provider stack, network stack and CPU, without requiring
 * a full Nova app build.
 *
 * Same CSV format and scheme-to-implementation mapping as the host test; see
 * {@code Video/doc/TEST.md}. Since the CSV lives outside the repo and JVM system properties
 * (-D) do not propagate into the instrumented app process on-device, the on-device CSV path is
 * supplied via an instrumentation runner argument instead:
 * <pre>
 *   adb shell am instrument -w -r \
 *       -e class com.archos.filecorelibrary.SpeedTestTransferTest \
 *       -e speedtestCsv /data/user/0/com.archos.filecorelibrary.test/files/servers.csv \
 *       -e speedtestUpstreamBufferBytes 1048576 \
 *       com.archos.filecorelibrary.test/androidx.test.runner.AndroidJUnitRunner
 * </pre>
 * See TEST.md for APK installation and CSV setup. The upstream buffer defaults to
 * 81920 bytes; this explicit benchmark setting is independent of playback mode.
 */
@RunWith(AndroidJUnit4.class)
public class SpeedTestTransferTest {

    @Test
    public void compareTransferRates() throws Exception {
        java.util.function.Function<String, String> args = key ->
                InstrumentationRegistry.getArguments().getString(key);
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
