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

package com.archos.filecorelibrary.sshj;

import static com.archos.filecorelibrary.sshj.SshjUtils.getSftpPath;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshjFile2 extends MetaFile2 {

    private static final Logger log = LoggerFactory.getLogger(SshjFile2.class);

    private static final long serialVersionUID = 2L;

    private final String mName;
    private final boolean mIsDirectory;
    private final boolean mIsFile;
    private final long mLastModified;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final long mLength;
    private final String mUriString;

    public SshjFile2(RemoteResourceInfo fileOrDir, Uri uri) {
        final FileAttributes fileAttributes = fileOrDir.getAttributes();
        mUriString = uri.toString();
        mName = uri.getLastPathSegment();
        mIsDirectory = fileOrDir.isDirectory();
        // TODO MARC check should work with link
        mIsFile = fileOrDir.isRegularFile();
        mLastModified = fileAttributes.getMtime();
        //mCanRead = fileAttributes.getPermissions(); // TODO assume true for now
        // TODO MARC
        mCanRead = true;
        mCanWrite = true;
        mLength = fileAttributes.getSize();
        log.trace("SshjFile2: uri=" + mUriString + ", mName=" + mName + ", isDirectory=" + isDirectory() +
                ", lastModified=" + mLastModified + ", canWrite=" + canWrite() + ", length=" + mLength);
    }

    public SshjFile2(FileAttributes fileAttributes, Uri uri) {
        mUriString = uri.toString();
        mName = uri.getLastPathSegment();
        final FileMode.Type type = fileAttributes.getType();
        mIsDirectory = (type == FileMode.Type.DIRECTORY);
        mIsFile = (type == FileMode.Type.REGULAR || type == FileMode.Type.SYMLINK);
        mLastModified = fileAttributes.getMtime();
        //mCanRead = fileAttributes.getPermissions(); // TODO assume true for now
        // TODO MARC
        mCanRead = true;
        mCanWrite = true;
        mLength = fileAttributes.getSize();
        log.trace("SshjFile2: uri=" + mUriString + ", mName=" + mName + ", isDirectory=" + isDirectory() +
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
        if (other instanceof SshjFile2) {
            return getUri().equals(((SshjFile2) other).getUri());
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
        return new SshjRawLister(getUri());
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new SshjFileEditor(Uri.parse(mUriString));
    }

    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary)
     */
    public static MetaFile2 fromUri(Uri uri) throws Exception {
        var sftpClient = SshjUtils.peekInstance().getSFTPClient(uri);
        final String filePath = getSftpPath(uri);
        var fileInformation =  sftpClient.lstat(filePath);
        return new SshjFile2(fileInformation, uri);
    }

}
