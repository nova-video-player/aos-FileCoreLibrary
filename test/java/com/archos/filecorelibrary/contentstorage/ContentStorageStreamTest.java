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

package com.archos.filecorelibrary.contentstorage;

import static org.junit.Assert.*;

import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ContentStorageStreamTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private static class TrackedAsset extends AssetFileDescriptor {
        boolean closed;
        TrackedAsset(ParcelFileDescriptor fd, long start, long length) { super(fd, start, length); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private TrackedAsset asset(long start, long length) throws Exception {
        File file = temp.newFile();
        Files.write(file.toPath(), new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        return new TrackedAsset(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), start, length);
    }

    @Test public void offsetIsRelativeToAssetAndReadStopsAtItsEnd() throws Exception {
        TrackedAsset asset = asset(2, 5);
        try (InputStream stream = ContentStorageFileEditor.openAt(asset, 2)) {
            byte[] bytes = new byte[10];
            assertEquals(3, stream.read(bytes));
            assertArrayEquals(new byte[] {4, 5, 6}, java.util.Arrays.copyOf(bytes, 3));
            assertEquals(-1, stream.read());
            assertEquals(0, stream.read(bytes, 0, 0));
            assertEquals(0, stream.skip(10));
        }
        assertFalse(asset.getFileDescriptor().valid());
    }

    @Test public void failedOffsetValidationClosesProviderDescriptor() throws Exception {
        TrackedAsset asset = asset(2, 5);
        assertThrows(IOException.class, () -> ContentStorageFileEditor.openAt(asset, Long.MAX_VALUE));
        assertTrue(asset.closed);
        assertFalse(asset.getFileDescriptor().valid());
    }

    @Test public void unknownLengthStillSupportsOffsetAndOwnsDescriptor() throws Exception {
        TrackedAsset asset = asset(0, AssetFileDescriptor.UNKNOWN_LENGTH);
        try (InputStream stream = ContentStorageFileEditor.openAt(asset, 9)) {
            assertEquals(9, stream.read());
            assertEquals(-1, stream.read());
        }
        assertFalse(asset.getFileDescriptor().valid());
    }
}
