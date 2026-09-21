// Copyright 2026 Nova Video Player
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

package com.archos.filecorelibrary.ftp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FtpUtilsTest {

    @Test
    public void testContainsGlobChars() {
        assertTrue(FtpUtils.containsGlobChars("/download/serie/Murderbot.S01E09[EZTVx.to].mkv"));
        assertTrue(FtpUtils.containsGlobChars("/download/serie/file[1].mkv"));
        assertTrue(FtpUtils.containsGlobChars("/download/serie/file].mkv"));
        assertTrue(FtpUtils.containsGlobChars("/download/serie/*.mkv"));
        assertTrue(FtpUtils.containsGlobChars("/download/serie/file?.mkv"));

        assertFalse(FtpUtils.containsGlobChars("/download/serie/normal_file.mkv"));
        assertFalse(FtpUtils.containsGlobChars("/download/serie/"));
        assertFalse(FtpUtils.containsGlobChars("/"));
        assertFalse(FtpUtils.containsGlobChars(null));
        assertFalse(FtpUtils.containsGlobChars(""));
    }

    @Test
    public void testGetParentPath() {
        assertEquals("/download/serie/",
                FtpUtils.getParentPath("/download/serie/Murderbot.S01E09[EZTVx.to].mkv"));
        assertEquals("/",
                FtpUtils.getParentPath("/movie[2024].mkv"));
        assertEquals("/download/serie/",
                FtpUtils.getParentPath("/download/serie/subfolder[1]/"));
        assertEquals("/",
                FtpUtils.getParentPath("/"));
        assertNull(FtpUtils.getParentPath(null));
        assertNull(FtpUtils.getParentPath(""));
    }

    @Test
    public void testGetBaseName() {
        assertEquals("Murderbot.S01E09[EZTVx.to].mkv",
                FtpUtils.getBaseName("/download/serie/Murderbot.S01E09[EZTVx.to].mkv"));
        assertEquals("movie[2024].mkv",
                FtpUtils.getBaseName("/movie[2024].mkv"));
        assertEquals("subfolder[1]",
                FtpUtils.getBaseName("/download/serie/subfolder[1]/"));
        assertEquals("/",
                FtpUtils.getBaseName("/"));
        assertNull(FtpUtils.getBaseName(null));
        assertNull(FtpUtils.getBaseName(""));
    }

    @Test
    public void testFindFTPFileByListingWithGlobChars() throws Exception {
        FTPFile targetFile = new FTPFile();
        targetFile.setName("Murderbot.S01E09[EZTVx.to].mkv");
        targetFile.setSize(374700068L);
        targetFile.setType(FTPFile.FILE_TYPE);

        FTPFile otherFile = new FTPFile();
        otherFile.setName("other_file.mkv");
        otherFile.setSize(1000L);
        otherFile.setType(FTPFile.FILE_TYPE);

        FTPClient mockFtp = new FTPClient() {
            @Override
            public FTPFile[] listFiles(String pathname) throws IOException {
                // Should be called with parent path "/download/serie/"
                if ("/download/serie/".equals(pathname)) {
                    return new FTPFile[]{otherFile, targetFile};
                }
                return new FTPFile[0];
            }
        };

        FTPFile resolved = FtpUtils.findFTPFileByListing(mockFtp, "/download/serie/Murderbot.S01E09[EZTVx.to].mkv");
        assertNotNull(resolved);
        assertEquals("Murderbot.S01E09[EZTVx.to].mkv", resolved.getName());
        assertEquals(374700068L, resolved.getSize());
    }

    @Test
    public void testResolveFTPFileFallsBackToSizeAndMdtm() throws Exception {
        FTPClient mockFtp = new FTPClient() {
            @Override
            public String featureValue(String feature) {
                return null; // No MLST
            }

            @Override
            public String getSize(String pathname) throws IOException {
                if ("/download/serie/Murderbot.S01E09[EZTVx.to].mkv".equals(pathname)) {
                    return "374700068";
                }
                return null;
            }

            @Override
            public String getModificationTime(String pathname) throws IOException {
                if ("/download/serie/Murderbot.S01E09[EZTVx.to].mkv".equals(pathname)) {
                    return "20260921124700";
                }
                return null;
            }
        };

        Uri uri = Uri.parse("ftp://192.168.0.10:21/download/serie/Murderbot.S01E09%5BEZTVx.to%5D.mkv");
        FTPFile resolved = FtpUtils.resolveFTPFile(mockFtp, uri);

        assertNotNull(resolved);
        assertEquals("Murderbot.S01E09[EZTVx.to].mkv", resolved.getName());
        assertEquals(374700068L, resolved.getSize());
        assertTrue(resolved.isFile());
        assertNotNull(resolved.getTimestamp());
    }

    @Test
    public void testGetFileSizeParentListingFallback() {
        FTPFile targetFile = new FTPFile();
        targetFile.setName("Murderbot.S01E09[EZTVx.to].mkv");
        targetFile.setSize(374700068L);

        FTPClient mockFtp = new FTPClient() {
            @Override
            public String getSize(String pathname) throws IOException {
                return null; // SIZE not supported
            }

            @Override
            public FTPFile mlistFile(String pathname) throws IOException {
                return null; // MLST fails
            }

            @Override
            public FTPFile[] listFiles(String pathname) throws IOException {
                if ("/download/serie/".equals(pathname)) {
                    return new FTPFile[]{targetFile};
                }
                return new FTPFile[0];
            }
        };

        long size = FtpUtils.getFileSize(mockFtp, "/download/serie/Murderbot.S01E09[EZTVx.to].mkv");
        assertEquals(374700068L, size);
    }
}
