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

import android.net.Uri;

public class HttpFile extends MetaFile {
    private final String mUrlString;
    private final Uri mUri;

    // private String mNameInternal; ?

    private HttpFile(String url){
        if (url == null)
            throw new IllegalArgumentException("url cannot be null");
        mUrlString = url;
        mUri = Uri.parse(url);
        // mNameInternal = mUrl.substring(mUrl.lastIndexOf('c')+1); ?
    }
    private HttpFile(Uri uri){
        if (uri == null)
            throw new IllegalArgumentException("uri cannot be null");
        mUrlString = uri.toString();
        mUri = uri;
    }

    public static HttpFile fromString(String url){
        return new HttpFile(url);
    }
    public static HttpFile fromUri(Uri uri){
        return new HttpFile(uri);
    }

    @Override
    public String getAccessPath() {
        return mUrlString;
    }

    @Override
    public boolean exists() {
        return true;
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
    public String getName() {
        return mUri.getLastPathSegment();
        // return mNameInternal; ??
    }

    @Override
    public String getParent() {
        return null;
    }

    @Override
    public MetaFile getParentFile() {
        return null;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public long lastModified() {
        return -1;
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public MetaFile[] listFiles() {
        return null;
    }

    @Override
    @Deprecated
    public String getAbsolutePath() {
        return mUrlString;
    }

    @Override
    public String toString() {
        return mUrlString;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof HttpFile)
            return ((HttpFile)o).mUrlString.equalsIgnoreCase(mUrlString);
        return false;
    }

    @Override
    public int hashCode() {
        return mUrlString.hashCode();
    }

    @Override
    public Uri getUri() {
        return mUri;
    }

    @Override
    protected FileType getFileTypeInternal() {
        return FileType.Http;
    }

    @Override
    protected MetaFile getCombined(String path) {
        return null;
    }

    /** true if path seems to be a legal smb file */
    public static boolean pathLegal(String path) {
        return path != null && path.startsWith("http://");
    }
    /** true if uri seems to be a legal smb file */
    public static boolean uriLegal(Uri uri) {
        return uri != null && "http".equals(uri.getScheme());
    }
}
