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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FileUtilsUriTest {

    @Test
    public void testUriWithHashInPathAndFilename() {
        // Test parent URL and child URI building with '#' in folder and filename
        Uri videoUri = Uri.parse("smb://server/download/%23/%23Alive.mkv");
        Uri parentUri = FileUtils.getParentUrl(videoUri);
        assertEquals("smb://server/download/%23/", parentUri.toString());

        Uri nfoUri = FileUtils.buildChildUri(parentUri, "#Alive.archos.nfo");
        assertEquals("smb://server/download/%23/%23Alive.archos.nfo", nfoUri.toString());

        Uri posterUri = FileUtils.buildChildUri(parentUri, "#Alive-poster.archos.jpg");
        assertEquals("smb://server/download/%23/%23Alive-poster.archos.jpg", posterUri.toString());

        // Test decoding for jcifs SmbFile
        String decoded = FileUtils.decodeUri(nfoUri);
        assertEquals("smb://server/download/#/#Alive.archos.nfo", decoded);
    }

    @Test
    public void testUriWithHashInFilenameOnly() {
        Uri videoUri = Uri.parse("smb://server/download/agent%23823.mkv");
        Uri parentUri = FileUtils.getParentUrl(videoUri);
        assertEquals("smb://server/download/", parentUri.toString());

        Uri nfoUri = FileUtils.buildChildUri(parentUri, "agent#823.archos.nfo");
        assertEquals("smb://server/download/agent%23823.archos.nfo", nfoUri.toString());

        String decoded = FileUtils.decodeUri(nfoUri);
        assertEquals("smb://server/download/agent#823.archos.nfo", decoded);
    }

    @Test
    public void testRemoveLastSegmentRootAndAncestors() {
        // Server root has no parent segment
        assertNull(FileUtils.removeLastSegment(Uri.parse("smb://10.0.2.3/")));
        assertNull(FileUtils.removeLastSegment(Uri.parse("smb://10.0.2.3")));
        assertNull(FileUtils.removeLastSegment(Uri.parse("smb:///")));
        assertNull(FileUtils.removeLastSegment(Uri.parse("file:///")));

        // Share root returns server root
        assertEquals("smb://10.0.2.3/", FileUtils.removeLastSegment(Uri.parse("smb://10.0.2.3/share/")).toString());
        assertEquals("smb://10.0.2.3/", FileUtils.removeLastSegment(Uri.parse("smb://10.0.2.3/share")).toString());

        // Subdirectory returns parent directory
        assertEquals("smb://10.0.2.3/share/", FileUtils.removeLastSegment(Uri.parse("smb://10.0.2.3/share/sub/")).toString());
        assertEquals("smb://10.0.2.3/share/sub/", FileUtils.removeLastSegment(Uri.parse("smb://10.0.2.3/share/sub/file.mkv")).toString());
    }

    @Test
    public void testEncodeAndDecodeEmptyAndRootUris() {
        assertEquals("smb://10.0.2.3/", FileUtils.encodeUri(Uri.parse("smb://10.0.2.3/")).toString());
        assertEquals("smb://10.0.2.3", FileUtils.encodeUri(Uri.parse("smb://10.0.2.3")).toString());
        assertEquals("smb://", FileUtils.encodeUri(Uri.parse("smb://")).toString());

        assertEquals("smb://10.0.2.3/", FileUtils.decodeUri(Uri.parse("smb://10.0.2.3/")));
        assertEquals("smb://10.0.2.3", FileUtils.decodeUri(Uri.parse("smb://10.0.2.3")));
        assertEquals("smb://", FileUtils.decodeUri(Uri.parse("smb://")));
    }

    @Test
    public void testUriWithCustomPort() {
        Uri uri = Uri.parse("smb://10.0.2.3:4455/share/dir/%23Alive.mkv");
        assertEquals("smb://10.0.2.3:4455/share/dir/", FileUtils.getParentUrl(uri).toString());
        assertEquals("smb://10.0.2.3:4455/share/", FileUtils.getParentUrl(FileUtils.getParentUrl(uri)).toString());
        assertEquals("smb://10.0.2.3:4455/", FileUtils.getParentUrl(FileUtils.getParentUrl(FileUtils.getParentUrl(uri))).toString());
        assertNull(FileUtils.getParentUrl(FileUtils.getParentUrl(FileUtils.getParentUrl(FileUtils.getParentUrl(uri)))));

        Uri parent = Uri.parse("smb://10.0.2.3:4455/share/dir/");
        Uri child = FileUtils.buildChildUri(parent, "#Alive.archos.nfo");
        assertEquals("smb://10.0.2.3:4455/share/dir/%23Alive.archos.nfo", child.toString());

        String decoded = FileUtils.decodeUri(child);
        assertEquals("smb://10.0.2.3:4455/share/dir/#Alive.archos.nfo", decoded);
    }
}
