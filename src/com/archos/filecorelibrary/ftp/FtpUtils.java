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
import org.apache.commons.net.ftp.parser.MLSxEntryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;

public class FtpUtils {
    private static final Logger log = LoggerFactory.getLogger(FtpUtils.class);

    private FtpUtils() {
    }

    public static boolean containsGlobChars(String path) {
        if (path == null) return false;
        return path.indexOf('[') >= 0 || path.indexOf(']') >= 0
                || path.indexOf('*') >= 0 || path.indexOf('?') >= 0;
    }

    public static String getParentPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String cleanPath = (path.length() > 1 && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash >= 0) {
            return cleanPath.substring(0, lastSlash + 1);
        }
        return "/";
    }

    public static String getBaseName(String path) {
        if (path == null || path.isEmpty()) return null;
        String cleanPath = (path.length() > 1 && path.endsWith("/")) ? path.substring(0, path.length() - 1) : path;
        int lastSlash = cleanPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < cleanPath.length() - 1) {
            return cleanPath.substring(lastSlash + 1);
        }
        return cleanPath;
    }

    /**
     * Finds an FTPFile for the given path by listing.
     * If the path contains wildcard/glob characters ('[', ']', '*', '?'), listing the path directly
     * triggers server-side globbing where '[' and ']' are treated as character classes, which fails
     * on Unix FTP daemons. In that case, we list the parent directory and match the entry by exact name.
     */
    public static FTPFile findFTPFileByListing(FTPClient ftp, String path) {
        if (ftp == null || path == null) return null;
        try {
            if (containsGlobChars(path)) {
                String parent = getParentPath(path);
                String name = getBaseName(path);
                if (parent != null && name != null) {
                    if (log.isDebugEnabled()) log.debug("findFTPFileByListing: glob chars detected, listing parent '{}' for target '{}'", parent, name);
                    FTPFile[] files = ftp.listFiles(parent);
                    if (files != null) {
                        for (FTPFile f : files) {
                            if (name.equals(f.getName())) {
                                return f;
                            }
                        }
                    }
                }
            } else {
                FTPFile[] files = ftp.listFiles(path);
                if (files != null && files.length == 1) {
                    return files[0];
                }
            }
        } catch (Exception e) {
            log.warn("findFTPFileByListing: listFiles failed for {}", path, e);
        }
        return null;
    }

    /**
     * Resolves an FTPFile for the specified URI by attempting:
     * 1. MLST (if supported by the server)
     * 2. SIZE command (RFC 3659) + MDTM timestamp (immune to globbing)
     * 3. Directory listing fallback (parent listing if glob chars are present)
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
        } catch (Exception e) {
            log.warn("resolveFTPFile: mlistFile threw exception for {}", path, e);
        }

        // 2. Fallback to SIZE command (RFC 3659) + MDTM
        // SIZE and MDTM bypass server-side globbing because they invoke direct stat(2) on the OS.
        if (ftpFile == null) {
            long size = getFileSizeOnly(ftp, path);
            if (size >= 0) {
                ftpFile = new FTPFile();
                ftpFile.setName(FileUtils.getName(uri));
                ftpFile.setSize(size);
                ftpFile.setType(FTPFile.FILE_TYPE);
                try {
                    String mtime = ftp.getModificationTime(path);
                    if (mtime != null) {
                        Calendar cal = MLSxEntryParser.parseGMTdateTime(mtime);
                        if (cal != null) {
                            ftpFile.setTimestamp(cal);
                        }
                    }
                } catch (Exception e) {
                    log.warn("resolveFTPFile: MDTM failed for {}", path, e);
                }
            }
        }

        // 3. Fallback to directory listing (handles directories, servers without SIZE, and bracketed files)
        if (ftpFile == null) {
            ftpFile = findFTPFileByListing(ftp, path);
        }

        return ftpFile;
    }

    /**
     * Queries the RFC 3659 SIZE command directly.
     *
     * @param ftp active FTPClient
     * @param path file path
     * @return file size in bytes, or -1 if unknown / not found
     */
    public static long getFileSizeOnly(FTPClient ftp, String path) {
        if (ftp == null || path == null) return -1;
        try {
            String sizeStr = ftp.getSize(path);
            if (sizeStr != null) {
                try {
                    long size = Long.parseLong(sizeStr.trim());
                    if (log.isDebugEnabled()) log.debug("getFileSizeOnly: SIZE command for {}: {}", path, size);
                    return size;
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            log.warn("getFileSizeOnly: getSize failed for {}", path, e);
        }
        return -1;
    }

    /**
     * Gets file size by querying RFC 3659 SIZE command, then mlistFile, then directory listing.
     *
     * @param ftp active FTPClient
     * @param path file path
     * @return file size in bytes, or -1 if unknown / not found
     */
    public static long getFileSize(FTPClient ftp, String path) {
        if (ftp == null || path == null) return -1;

        // 1. Try SIZE command
        long size = getFileSizeOnly(ftp, path);
        if (size >= 0) return size;

        // 2. Try MLST
        try {
            FTPFile ftpFile = ftp.mlistFile(path);
            if (ftpFile != null && ftpFile.getSize() >= 0) {
                if (log.isDebugEnabled()) log.debug("getFileSize: mlistFile for {}: {}", path, ftpFile.getSize());
                return ftpFile.getSize();
            }
        } catch (Exception e) {
            log.warn("getFileSize: mlistFile failed for {}", path, e);
        }

        // 3. Try directory listing (handles glob characters via parent listing)
        FTPFile file = findFTPFileByListing(ftp, path);
        if (file != null && file.getSize() >= 0) {
            if (log.isDebugEnabled()) log.debug("getFileSize: listing for {}: {}", path, file.getSize());
            return file.getSize();
        }

        return -1;
    }
}
