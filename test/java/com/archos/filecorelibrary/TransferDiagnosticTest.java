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
import androidx.test.core.app.ApplicationProvider;
import com.archos.environment.ArchosUtils;
import java.nio.file.Files;
import java.io.File;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TransferDiagnosticTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void completeLocalTransferPassesAndTruncationExpectationFails() throws Exception {
        ArchosUtils.setGlobalContext(ApplicationProvider.getApplicationContext());
        File media = temp.newFile("fixture.bin"), csv = temp.newFile("servers.csv");
        Files.write(media.toPath(), new byte[131072]);
        Files.writeString(csv.toPath(), Uri.fromFile(media) + ",,,131072");
        TransferDiagnostic.run(csv.toString(), key -> "speedtestStressIterations".equals(key) ? "3" : null);
        TransferDiagnostic.run(csv.toString(), key -> "speedtestSampleBytes".equals(key) ? "4096" : null);
        Files.writeString(csv.toPath(), Uri.fromFile(media) + ",,,131073");
        assertThrows(AssertionError.class, () -> TransferDiagnostic.run(csv.toString(), key -> null));
    }
    @Test public void malformedHashFailsBeforeNetworkIo() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new TransferDiagnostic.Row("file:///fixture,,,10,not-a-hash"));
    }
}
