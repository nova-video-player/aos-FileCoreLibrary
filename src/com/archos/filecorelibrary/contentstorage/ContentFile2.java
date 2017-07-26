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

package com.archos.filecorelibrary.contentstorage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MimeUtils;
import com.archos.filecorelibrary.RawLister;

import java.lang.reflect.InvocationTargetException;

public class ContentFile2 extends MetaFile2 {

    private static final long serialVersionUID = 2L;

    /**
     * Used for getNumberOfFilesInside() and getNumberOfDirectoriesInside()
     */
    public  static final int NUMBER_UNKNOWN = -1;

    private final int mNumberOfFilesInside;
    private final int mNumberOfDirectoriesInside;
    private final long mLastModified;
    private String mName;
    private String mMimeType;
    private final long mLength;
    private final boolean mIsFile;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final String mPath;

    /**
     * This method does access the actual storage to get data about the file
     */
    public ContentFile2(DocumentFile file) {
        if (file == null)
            throw new IllegalArgumentException("file must not be null");
        Log.d("extdebug","current uri "+file.getUri());
        mPath = file.getUri().toString();

        mName = file.getName();
        if(mName==null)
            mName = file.getUri().getLastPathSegment();
        mLength = file.length();
        mIsFile = file.isFile();
        mLastModified = file.lastModified();
        mCanRead = file.canRead();
        mCanWrite = file.canWrite();
        mNumberOfFilesInside = NUMBER_UNKNOWN;
        mNumberOfDirectoriesInside = NUMBER_UNKNOWN;
    }


    private ContentFile2(Uri uri) {
        if (uri == null)
            throw new IllegalArgumentException("uri must not be null");
        mPath = uri.toString();
        mName = DocumentUriBuilder.getNameFromContentProvider(uri);
        if(mName==null)
            mName = uri.getLastPathSegment();
        mMimeType = DocumentUriBuilder.getTypeFromContentProvider(uri);
        mLength = 0;
        mIsFile = true;
        mLastModified = 0;
        mCanRead = true;
        mCanWrite = false;
        mNumberOfFilesInside = NUMBER_UNKNOWN;
        mNumberOfDirectoriesInside = NUMBER_UNKNOWN;
    }
    /**
     * Get Metafile2 object from a uri.
     * This method does access the actual storage to get data about the file
     * Use this only if absolutely necessary.
     */
    public static MetaFile2 fromUri(Uri uri){

        DocumentFile file = null;

        try {
            try {
                file = DocumentUriBuilder.getDocumentFileForUri(uri);
            }
            catch (IllegalArgumentException e){
                //assume this is a file (for content providers like googledrive)
                return new ContentFile2(uri);
            }
            return new ContentFile2(file);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    @SuppressWarnings("unused")
    private ContentFile2() {
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

        return Uri.parse(mPath);
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
        if (other instanceof ContentFile2) {
            return getUri().equals( ((ContentFile2)other).getUri());
        } else {
            return false;
        }
    }

    public String getMimeType(){
        if(mMimeType!=null)
            return mMimeType;
        return super.getMimeType();
    }
    @Override
    public String getExtension(){ //most of the time we don't have any extension
        if(mMimeType!=null){
            String builtExtension = MimeUtils.guessExtensionFromMimeType(mMimeType);
            if(builtExtension!=null&&!builtExtension.isEmpty())
                return builtExtension;
        }
        return super.getExtension();
    }

    @Override
    public RawLister getRawListerInstance() {
        return new ContentStorageRawLister(getUri()) ;
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new ContentStorageFileEditor(getUri(), ct);
    }
}
