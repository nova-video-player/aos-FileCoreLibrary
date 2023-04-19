// Copyright 2017 Archos SA
// Copyright 2019 Courville Software
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

package com.archos.filecorelibrary.jcifs;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import static com.archos.filecorelibrary.FileUtils.caughtException;
import static com.archos.filecorelibrary.jcifs.JcifsUtils.getSmbFile;

public class JcifsFile2 extends MetaFile2 {

    private static final long serialVersionUID = 2L;

    private static final Logger log = LoggerFactory.getLogger(JcifsFile2.class);

    private String mName;
    private boolean mIsDirectory;
    private boolean mIsFile;
    private long mLastModified;
    private boolean mCanRead;
    private boolean mCanWrite;
    private long mLength;
    private String mUriString;

    /**
     * SmbFile argument must contain already valid data (name, size, etc.)
     * because this method won't make any network call
     */
    public JcifsFile2(NovaSmbFile nSmbFile) throws SmbException {
        // no need to redo subst... not the right way
        buildJcifsFile2(nSmbFile);
    }

    /**
     * Constructor from Uri. Does network access to get the data.
     * Private so that user has to use the static fromUri() instead (to make it more clear)
     */
    private JcifsFile2(Uri uri) throws MalformedURLException, SmbException {
        // Create the SmbFile instance
        NovaSmbFile nSmbFile = getSmbFile(uri);
        buildJcifsFile2(nSmbFile);
    }

    public JcifsFile2(SmbFile file, String shareName, String shareIp) throws SmbException {
        buildJcifsFile2(file, shareName, shareIp);
    }

    private void buildJcifsFile2(SmbFile file, String shareName, String shareIp) throws SmbException {
        if (shareIp == null) shareIp = shareName;
        buildJcifsFile2(file);
        mUriString = mUriString.replaceFirst(shareIp, shareName);
        mName = mName.replaceFirst(shareIp, shareName);
    }

    private void buildJcifsFile2(NovaSmbFile nFile) throws SmbException {
        buildJcifsFile2(nFile.smbFile);
        mUriString = nFile.getCanonicalPath();
        mName = nFile.getName();
        if (mIsDirectory && mName.endsWith("/")) {
            mName = mName.substring(0, mName.length()-1);
        } else {
            mName = mName;
        }
    }

    private void buildJcifsFile2(SmbFile file) throws SmbException {

        if (file == null) {
            throw new IllegalArgumentException("JcifsFile2: file cannot be null");
        }
        // Only use methods doing no network access here
        mUriString = file.getCanonicalPath();
        String name  = file.getName();
        mIsDirectory = file.isDirectory();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
        mCanRead = false;
        mCanWrite = false;
        mLength = 0;
        if (mIsDirectory || mIsFile) {
            // generates an exception in case of a directory not accessible but need this information for deletion
            try {
                mCanRead = file.canRead();
                mCanWrite = file.canWrite();
                if (mIsFile) mLength = file.length();
            } catch (SmbAuthException e) {
                caughtException(e, "JfisFile2 " + file.getPath(), "SmbAuthException");
            } catch (SmbException e) {
                caughtException(e, "JfisFile2 " + file.getPath(), "SmbException");
            }
        }

        // remove the '/' at the end of directory name (Jcifs adds it)
        if (mIsDirectory && name.endsWith("/")) {
            mName = name.substring(0, name.length()-1);
        } else {
            mName = name;
        }
    }

    /**
     * This method performs network access to get data about the file. Handle with care!
     * @param uri
     * @return
     * @throws MalformedURLException
     * @throws SmbException
     */
    public static MetaFile2 fromUri(Uri uri) throws MalformedURLException, SmbException {
        return new JcifsFile2(uri);
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
        return other instanceof JcifsFile2 && getUri().equals(((JcifsFile2) other).getUri());
    }

    @Override
    public Uri getUri() {
        return Uri.parse(this.mUriString);
    }

    @Override
    public RawLister getRawListerInstance() {
        return new JcifsRawLister(getUri());
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new JcifsFileEditor(getUri());
    }
}
