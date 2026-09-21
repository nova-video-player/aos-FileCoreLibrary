// Copyright 2024 Nova Video Player
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

import android.net.Uri;

import com.archos.filecorelibrary.FileUtils;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FtpUtils {
    private static final Logger log = LoggerFactory.getLogger(FtpUtils.class);

    private FtpUtils() {
    }

    /**
     * Resolves an FTPFile for the specified URI by attempting:
     * 1. MLST (if supported by the server)
     * 2. listFiles(path)
     * 3. SIZE command (constructs synthetic FTPFile if successful)
     *
     * @param ftp an active FTPClient
     * @param uri URI of the file
     * @return FTPFile or null if not found
     * @throws IOException on network/protocol errors
     */
    public static FTPFile resolveFTPFile(FTPClient ftp, Uri uri) throws IOException {
        if (ftp == null || uri == null) return null;
        String path = uri.getPath();
        FTPFile ftpFile = null;

        // 1. Try MLST if server reports support
        try {
            if (ftp.featureValue("MLST") != null) {
                ftpFile = ftp.mlistFile(path);
            } else {
                if (log.isDebugEnabled()) log.debug("resolveFTPFile: ftp server does not report MLST feature for {}", uri);
            }
        } catch (IOException e) {
            log.warn("resolveFTPFile: mlistFile threw exception for {}", path, e);
        }

        // 2. Fallback to listFiles(path)
        if (ftpFile == null) {
            try {
                FTPFile[] files = ftp.listFiles(path);
                if (files != null && files.length == 1) {
                    ftpFile = files[0];
                }
            } catch (IOException e) {
                log.warn("resolveFTPFile: listFiles threw exception for {}", path, e);
            }
        }

        // 3. Fallback to SIZE command (RFC 3659)
        if (ftpFile == null) {
            long size = getFileSize(ftp, path);
            if (size >= 0) {
                ftpFile = new FTPFile();
                ftpFile.setName(FileUtils.getName(uri));
                ftpFile.setSize(size);
                ftpFile.setType(FTPFile.FILE_TYPE);
            }
        }

        return ftpFile;
    }

    /**
     * Gets file size by querying RFC 3659 SIZE command, then mlistFile, then listFiles.
     *
     * @param ftp active FTPClient
     * @param path file path
     * @return file size in bytes, or -1 if unknown / not found
     */
    public static long getFileSize(FTPClient ftp, String path) {
        if (ftp == null || path == null) return -1;

        // 1. Try SIZE command
        try {
            String sizeStr = ftp.getSize(path);
            if (sizeStr != null) {
                try {
                    long size = Long.parseLong(sizeStr.trim());
                    if (log.isDebugEnabled()) log.debug("getFileSize: SIZE command for {}: {}", path, size);
                    return size;
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            log.warn("getFileSize: getSize failed for {}", path, e);
        }

        // 2. Try MLST
        try {
            FTPFile ftpFile = ftp.mlistFile(path);
            if (ftpFile != null && ftpFile.getSize() >= 0) {
                if (log.isDebugEnabled()) log.debug("getFileSize: mlistFile for {}: {}", path, ftpFile.getSize());
                return ftpFile.getSize();
            }
        } catch (IOException e) {
            log.warn("getFileSize: mlistFile failed for {}", path, e);
        }

        // 3. Try listFiles
        try {
            FTPFile[] files = ftp.listFiles(path);
            if (files != null && files.length == 1 && files[0].getSize() >= 0) {
                if (log.isDebugEnabled()) log.debug("getFileSize: listFiles for {}: {}", path, files[0].getSize());
                return files[0].getSize();
            }
        } catch (IOException e) {
            log.warn("getFileSize: listFiles failed for {}", path, e);
        }

        return -1;
    }
}
