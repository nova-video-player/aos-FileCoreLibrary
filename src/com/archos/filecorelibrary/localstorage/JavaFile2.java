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

package com.archos.filecorelibrary.localstorage;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

import java.io.File;

public class JavaFile2 extends MetaFile2 {

    private static final long serialVersionUID = 2L;

    /**
     * Used for getNumberOfFilesInside() and getNumberOfDirectoriesInside()
     */
    public  static final int NUMBER_UNKNOWN = -1;

    private final int mNumberOfFilesInside;
    private final int mNumberOfDirectoriesInside;
    private final long mLastModified;
    private final String mName;
    private final long mLength;
    private final boolean mIsFile;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final String mPath;

    /**
     * This method does access the actual storage to get data about the file
     */
    public JavaFile2(File file) {
        if (file == null)
            throw new IllegalArgumentException("file must not be null");
        mPath = Uri.fromFile(file).getEncodedPath();
        mName = file.getName();
        mLength = file.length();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
        mCanRead = file.canRead();
        mCanWrite = file.canWrite();
        mNumberOfFilesInside = NUMBER_UNKNOWN;
        mNumberOfDirectoriesInside = NUMBER_UNKNOWN;
    }

    /**
     * This method does access the actual storage to get data about the file
     */
    public JavaFile2(File file, int numberOfFilesInside, int numberOfDirectoriesInside) {
        if (file == null)
            throw new IllegalArgumentException("file must not be null");
        mPath = Uri.fromFile(file).getEncodedPath();
        mName = file.getName();
        mLength = file.length();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
        mCanRead = file.canRead();
        mCanWrite = file.canWrite();
        mNumberOfFilesInside = numberOfFilesInside;
        mNumberOfDirectoriesInside = numberOfDirectoriesInside;
    }

    /**
     * Get Metafile2 object from a uri.
     * This method does access the actual storage to get data about the file
     * Use this only if absolutely necessary.
     */
    public static MetaFile2 fromUri(Uri uri){
        File file = new File(uri.getPath());
        if(file.exists())
            return new JavaFile2(file);
        return null;
    }

    @SuppressWarnings("unused")
    private JavaFile2() {
        throw new IllegalArgumentException("Unauthorized to create a JavaFile2 from nothing! Can only be created from a java.io.File");
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isDirectory() {
        return !mIsFile;
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
    public Uri getUri() {
        return Uri.parse("file://"+mPath);
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
        return false;
    }

    /**
     * May return NUMBER_UNKNOWN
     */
    public int getNumberOfFilesInside() {
        return mNumberOfFilesInside;
    }

    /**
     * May return NUMBER_UNKNOWN
     */
    public int getNumberOfDirectoriesInside() {
        return mNumberOfDirectoriesInside;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof JavaFile2) {
            return getUri().equals( ((JavaFile2)other).getUri());
        } else {
            return false;
        }
    }

    @Override
    public RawLister getRawListerInstance() {
        return new LocalStorageRawLister(getUri()) ;
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new LocalStorageFileEditor(getUri(), ct);
    }
}
