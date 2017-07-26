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

package com.archos.filecorelibrary.zip;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.Utils;
import com.archos.filecorelibrary.localstorage.JavaFile2;
import com.archos.filecorelibrary.localstorage.LocalStorageFileEditor;
import com.archos.filecorelibrary.localstorage.LocalStorageRawLister;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFile2 extends MetaFile2 {

    private static final long serialVersionUID = 2L;


    private final long mLastModified;
    private final long mLength;
    private final boolean mIsFile;
    private final String mPath;

    /**
     * This method does access the actual storage to get data about the file
     */
    public ZipFile2(File file) {
        if (file == null)
            throw new IllegalArgumentException("file must not be null");

        //remove file://
        if(file.getAbsolutePath().startsWith("file://"))
            mPath = file.getAbsolutePath().substring("file://".length());
        else
            mPath = file.getAbsolutePath();
        mLength = file.length();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
    }
    public ZipFile2(String zipPath, ZipEntry entry) {
        if (entry == null)
            throw new IllegalArgumentException("file must not be null");
        if(!zipPath.endsWith("/"))
            zipPath += "/";
       
        mPath = zipPath+entry.getName();
        mLength = entry.getSize();
        mIsFile = !entry.isDirectory();
        mLastModified = entry.getTime();
    }


    /**
     * Get Metafile2 object from a uri.
     * This method does access the actual storage to get data about the file
     * Use this only if absolutely necessary.
     */
    public static MetaFile2 fromUri(Uri uri){
        File file = new File(uri.getPath());
        if(file.exists())
            return new ZipFile2(file);
        return null;
    }

    @SuppressWarnings("unused")
    private ZipFile2() {
        throw new IllegalArgumentException("Unauthorized to create a JavaFile2 from nothing! Can only be created from a java.io.File");
    }

    @Override
    public String getName() {
        return Utils.getName(getUri());
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
        return Uri.parse("zip://"+mPath);
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ZipFile2) {
            return getUri().equals( ((ZipFile2)other).getUri());
        } else {
            return false;
        }
    }

    @Override
    public RawLister getRawListerInstance() {
        return new ZipRawLister(getUri()) ;
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new ZipFileEditor(getUri());
    }


}
