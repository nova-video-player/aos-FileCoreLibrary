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

package com.hierynomus.msfscc.fileinformation;

import static org.junit.Assert.*;
import android.net.Uri;
import com.archos.filecorelibrary.smbj.SmbjFile2;
import com.hierynomus.msdtyp.FileTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SmbjFileLengthTest {
    @Test public void fileLengthExcludesFilesystemAllocationPadding() {
        FileTime time = new FileTime(0);
        FileBasicInformation basic = new FileBasicInformation(time, time, time, time, 0);
        FileStandardInformation standard = new FileStandardInformation(736112640, 736111567, 1, false, false);
        FileAllInformation info = new FileAllInformation(basic, standard, null, null, null, null, null, null, "movie.mkv");
        assertEquals(736111567, new SmbjFile2(info, Uri.parse("smbj://server/share/movie.mkv")).length());
    }
}
