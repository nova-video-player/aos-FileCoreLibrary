// Copyright 2023 Courville Software
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

package com.archos.filecorelibrary.smbj;

import static com.archos.filecorelibrary.FileUtils.encodeUri;
import static com.archos.filecorelibrary.FileUtils.getFilePath;
import static com.archos.filecorelibrary.FileUtils.getShareName;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.jcifs.JcifsFile2;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileBasicInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.File;
import com.jcraft.jsch.IO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

public class SmbjFile2 extends MetaFile2 {

    private static final Logger log = LoggerFactory.getLogger(SmbjFile2.class);

    private static final long serialVersionUID = 2L;

    private final String mName;
    private final boolean mIsDirectory;
    private final boolean mIsFile;
    private final long mLastModified;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final long mLength;
    private final String mUriString;

    public SmbjFile2(FileIdBothDirectoryInformation fileOrDir, Uri uri) {
        final long fileAttributes = fileOrDir.getFileAttributes();
        mUriString = uri.toString();
        mName = uri.getLastPathSegment();
        mIsDirectory = EnumWithValue.EnumUtils.isSet(fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
        mIsFile = !mIsDirectory;
        if (fileOrDir.getChangeTime() != null) mLastModified = fileOrDir.getChangeTime().toDate().getTime();
        else mLastModified = 0;
        mCanRead = true; // TODO assume true for now
        mCanWrite = ! EnumWithValue.EnumUtils.isSet(fileAttributes, FileAttributes.FILE_ATTRIBUTE_READONLY);
        mLength = fileOrDir.getAllocationSize();
        log.trace("SmbjFile2: uri=" + mUriString + ", mName=" + mName + ", isDirectory=" + isDirectory() +
                ", lastModified=" + mLastModified + ", canWrite=" + canWrite() + ", length=" + mLength);
    }

    public SmbjFile2(FileAllInformation fileInformation, Uri uri) {
        mUriString = uri.toString();
        mName = encodeUri(uri).getLastPathSegment();
        mIsDirectory = fileInformation.getStandardInformation().isDirectory();
        mIsFile = !mIsDirectory;
        mLastModified = fileInformation.getBasicInformation().getChangeTime().toDate().getTime();
        mCanRead = true; // TODO assume true for now
        mCanWrite = ! EnumWithValue.EnumUtils.isSet(fileInformation.getBasicInformation().getFileAttributes(),
                FileAttributes.FILE_ATTRIBUTE_READONLY);
        mLength = fileInformation.getStandardInformation().getAllocationSize();
        log.trace("SmbjFile2: uri=" + mUriString + ", mName=" + mName + ", isDirectory=" + isDirectory() +
                ", lastModified=" + mLastModified + ", canWrite=" + canWrite() + ", length=" + mLength);
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
        if (other instanceof SmbjFile2) {
            return getUri().equals(((SmbjFile2) other).getUri());
        } else {
            return false;
        }
    }

    @Override
    public Uri getUri() {
        return Uri.parse(this.mUriString);
    }

    @Override
    public RawLister getRawListerInstance() {
        return new SmbjRawLister(getUri());
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new SmbjFileEditor(Uri.parse(mUriString));
    }

    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary)
     */
    public static MetaFile2 fromUri(Uri uri) throws Exception {
        var diskShare = SmbjUtils.peekInstance().getSmbShare(uri);
        final String filePath = getFilePath(uri);
        final String shareName = getShareName(uri);
        //List<FileIdBothDirectoryInformation> diskShareLst = diskShare.list(filePath);
        //return new SmbjFile2(diskShareLst.get(0), uri);
        DiskEntry file = diskShare.open(filePath,
                EnumSet.of(AccessMask.FILE_READ_DATA),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_READONLY),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_RANDOM_ACCESS));
        FileAllInformation fileInformation = diskShare.getFileInformation(filePath);
        file.close();
        return new SmbjFile2(fileInformation, uri);
    }
}
