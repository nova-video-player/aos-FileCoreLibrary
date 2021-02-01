// Copyright 2017 Archos SA
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

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

import java.io.IOException;

public class FTPFile2 extends MetaFile2 {

    private static final Logger log = LoggerFactory.getLogger(FTPFile2.class);

    private static final long serialVersionUID = 2L;

    private final String mUriString;
    private final String mName;
    private final boolean mIsDirectory;
    private final boolean mIsLink;
    private final boolean mIsFile;
    private final long mLastModified;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final long mLength;

    public FTPFile2(FTPFile file, Uri uri, String forcedName) {

        if (uri == null) {
            throw new IllegalArgumentException("uri cannot be null");
        }

        mUriString = uri.toString();
        String name;
        if (forcedName == null) name = file.getName();
        else name = forcedName;
        mIsLink = file.isSymbolicLink();

        FTPFile fileToCheck = null;
        if (mIsLink) {
            try {
                fileToCheck = getFTPFile(uri);
            } catch (Exception e) {
                log.warn("FTPFile2: caught exception following link " + uri);
            }
            if (fileToCheck == null) {
                fileToCheck = file;
            }
        } else {
            fileToCheck = file;
        }
        mIsDirectory = fileToCheck.isDirectory();
        mIsFile = fileToCheck.isFile();
        if (fileToCheck.getTimestamp() != null)
            mLastModified = fileToCheck.getTimestamp().getTimeInMillis();
        else
            mLastModified = 0;
        mCanRead = fileToCheck.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION);
        mCanWrite = fileToCheck.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION);
        mLength = fileToCheck.getSize();
        // remove the '/' at the end of directory name
        if (mIsDirectory && name.endsWith("/")) {
            mName = name.substring(0, name.length()-1);
        } else {
            mName = name;
        }
        log.trace("FTPFile2 uri: " + uri + ", isFile=" + mIsFile + ", isDirectory=" + mIsDirectory + ", canRead=" + mCanRead + ", length=" + mLength);
    }

    @SuppressWarnings("unused")
    private FTPFile2() {
        throw new IllegalArgumentException("Unauthorized to create a FTPFile2 from nothing! Can only be created from a org.apache.commons.net.ftp.FTPFile and an android.net.Uri");
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isDirectory() {
        return mIsDirectory;
    }

    @Override
    public boolean isFile() {
        return mIsFile;
    }

    @Override
    public long lastModified() {
        return mLastModified;
    }

    @Override
    public long length() {
        return mLength;
    }

    @Override
    public boolean canRead() {
        return mCanRead;
    }

    @Override
    public boolean canWrite() {
        return mCanWrite;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FTPFile2) {
            return getUri().equals(((FTPFile2)other).getUri());
        } else {
            return false;
        }
    }

    @Override
    public Uri getUri() {
        return Uri.parse(this.mUriString);
    }

    public RawLister getRawListerInstance() {
        return new FTPRawLister(getUri()) ;
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new FtpFileEditor(getUri());
    }
    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary
     *
     */
    /*
    public static MetaFile2 fromUri(Uri uri) throws Exception {
        log.debug("fromUri: " + uri);
        FTPFile ftpFile;
        if (uri.getScheme().equals("ftps")) {
            // ftpClient is not thread safe: using a new instance (need to close afterwards)
            FTPSClient ftp = Session.getInstance().getNewFTPSClient(uri, FTP.BINARY_FILE_TYPE);
            if (ftp.featureValue("MLST") == null) log.error("fromUri: ftp server does not support MLST!!!");
            ftpFile = ftp.mlistFile(uri.getPath());
            Session.closeNewFTPClient(ftp);
        } else {
            FTPClient ftp = Session.getInstance().getNewFTPClient(uri, FTP.BINARY_FILE_TYPE);
            if (ftp.featureValue("MLST") == null) log.warn("fromUri: ftp server does not support MLST!!!");
            ftpFile = ftp.mlistFile(uri.getPath());
            Session.closeNewFTPClient(ftp);
        }
        if (ftpFile != null) return new FTPFile2(ftpFile,uri);
        log.warn("fromUri: ftp detected but ftpfile is null!");
        return null;
    }
     */

    public static MetaFile2 fromUri(Uri uri) throws Exception {
        log.debug("fromUri: " + uri);
        FTPFile ftpFile = getFTPFile(uri);
        if (ftpFile != null) return new FTPFile2(ftpFile,uri, null);
        log.warn("fromUri: ftp detected but ftpfile is null!");
        return null;
    }

    // getFTPFile follows links one hop
    // /!\ works only on files not directories
    public final static FTPFile getFTPFile(Uri uri) throws Exception {
        if (uri == null) return null;
        log.debug("getFTPFile: " + uri);
        FTPFile ftpFile;
        if (uri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getNewFTPSClient(uri, FTP.BINARY_FILE_TYPE);
            if (ftp.featureValue("MLST") == null) log.warn("getFTPFile: ftp server does not support MLST!!!");
            log.debug("getFTPFile: mlistFile on " + uri.getPath());
            ftpFile = ftp.mlistFile(uri.getPath());
            log.debug("getFTPFile: still alive got ftpFile for " + ftpFile.getName());
            if (ftpFile != null && ftpFile.isSymbolicLink()) {
                log.debug("getFTPFile: follow link " + ftpFile.getLink());
                ftpFile = ftp.mlistFile(ftpFile.getLink());
            }
            Session.closeNewFTPSClient(ftp);
        } else {
            FTPClient ftp = Session.getInstance().getNewFTPClient(uri, FTP.BINARY_FILE_TYPE);
            if (ftp.featureValue("MLST") == null) log.warn("getFTPFile: ftp server does not support MLST!!!");
            ftpFile = ftp.mlistFile(uri.getPath());
            // Important: mlst works on single file only for proftpd no dir! otherwise use mlsd
            // TODO mlst or mlsd for dir
            if (ftpFile != null && ftpFile.isSymbolicLink()) {
                log.debug("getFTPFile: follow link " + ftpFile.getLink());
                ftpFile = ftp.mlistFile(ftpFile.getLink());
            }
            Session.closeNewFTPClient(ftp);
        }
        if (ftpFile != null) return ftpFile;
        log.warn("getFTPFile: ftp detected but ftpfile is null!");
        return null;
    }
}
