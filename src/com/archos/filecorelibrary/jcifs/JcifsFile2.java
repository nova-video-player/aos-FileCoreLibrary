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
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import java.net.MalformedURLException;

import jcifs.CIFSContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class JcifsFile2 extends MetaFile2 {

    private static final long serialVersionUID = 2L;
    private static final String TAG = "JcifsFile2";
    private static final boolean DBG = true;

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
    public JcifsFile2(SmbFile file) throws SmbException {
        if (file == null) {
            throw new IllegalArgumentException("file cannot be null");
        }
        // Only use methods doing no network access here
        mUriString = file.getCanonicalPath();
        String name  = file.getName();
        mIsDirectory = file.isDirectory();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
        if (DBG) Log.d(TAG,file.getPath());
        mCanRead = file.canRead();
        mCanWrite = file.canWrite();
        mLength = file.length();

        // remove the '/' at the end of directory name (Jcifs adds it)
        if (mIsDirectory && name.endsWith("/")) {
            mName = name.substring(0, name.length()-1);
        } else {
            mName = name;
        }
    }

    /**
     * Constructor from Uri. Does network access to get the data.
     * Private so that user has to use the static fromUri() instead (to make it more clear)
     */
    private JcifsFile2(Uri uri) throws MalformedURLException, SmbException {

        // Create the SmbFile instance
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        SmbFile file;

        CIFSContext context = JcifsUtils.getBaseContext(false);
        NtlmPasswordAuthenticator auth = null;
        if(cred!=null)
            auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
        else
            auth = new NtlmPasswordAuthenticator("","GUEST", "");
        file = new SmbFile(uri.toString(), context.withCredentials(auth));

        // Using the methods doing network access to get the actual data
        mUriString = file.getCanonicalPath();
        String name  = file.getName();
        mIsDirectory = file.isDirectory();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
        mCanRead = file.canRead();
        mCanWrite = file.canWrite();
        try {
            mLength = file.length();
        } catch (SmbException e) {
            mLength = 0;
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
