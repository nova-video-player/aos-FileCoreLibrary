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


package com.archos.filecorelibrary;

import android.content.Context;
import android.net.Uri;

import java.io.Serializable;

/**
 * Abstraction of a "file". Several file types are supported.
 * Not all File methods implemented yet. Constructors are private.
 */
public abstract class MetaFile2 implements Serializable {

    private static final long serialVersionUID = 2L;

    protected static final String TAG = "MetaFile2";
    private long mComputedLength = -1;

    /** the name of the underlying file */
    public abstract String getName();

    /** true if underlying entity is a directory */
    public abstract boolean isDirectory();

    /** true if underlying entity is a file */
    public abstract boolean isFile();

    /** date of last modification, -1 if unavailable */
    public abstract long lastModified();

    /** true if the underlying file is readable */
    public abstract boolean canRead();

    /** true if the underlying file writable */
    public abstract boolean canWrite();

    /** file size in bytes, -1 if unavailable */
    public abstract long length();

    public long getComputedLength(){
        if(mComputedLength==-1)
            return length();
        return mComputedLength;
    }

    /** Returns lister of this metafile */
    public abstract RawLister getRawListerInstance();
    
    /** Returns editor of this metafile
     * context can be nulled if not using file writing methods*/
    public abstract FileEditor getFileEditorInstance(Context ct);

    /**
     * Returns the Uri that describes this file, i.e. that is used to "open" this file
     * This Uri can be used to list the content if it is a directory
     * This Uri can be used to open the file with an application if it is a file 
     **/
    public abstract Uri getUri();

    /**
     * Usually this returns the same uri as getUri except for upnp where we need a streaming http:// uri
     * @return
     */
    public  Uri getStreamingUri(){
        return getUri();
    }

    /** false if the file is on local storage */
    public abstract boolean isRemote();

    public String getNameWithoutExtension(){
        return Utils.stripExtensionFromName(getName());
    }

    /**
     * Get the lowercase file extension of this file. Can be null
     */
    public String getExtension() {
        return MimeUtils.getExtension(getName());
    }

    /**
     * when manually calculating file length
     * @param length
     */
    public void setLength(long length){
        mComputedLength = length;
    }
    /**
     * Get the MimeType. Can be null
     */
    public String getMimeType() {
        return MimeUtils.guessMimeTypeFromExtension(getExtension());
    }

}
