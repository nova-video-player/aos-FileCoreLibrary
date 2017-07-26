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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JavaFile extends MetaFile {
    private static final String FILE_SCHEME = "file";

    private final File mFile;

    public JavaFile(File file) {
        if (file == null)
            throw new IllegalArgumentException("file must not be null");
        mFile = file;
    }

    public JavaFile(String path) {
        mFile = new File(path);
    }

    @Override
    public boolean exists() {
        return mFile.exists();
    }

    @Override
    public String getName() {
        return mFile.getName();
    }

    @Override
    public String getParent() {
        return mFile.getParent();
    }

    @Override
    public MetaFile getParentFile() {
        return wrap(mFile.getParentFile());
    }

    @Override
    public boolean isDirectory() {
        return mFile.isDirectory();
    }

    @Override
    public boolean isFile() {
        return mFile.isFile();
    }

    @Override
    public long lastModified() {
        return mFile.lastModified();
    }

    @Override
    public long length() {
        return mFile.length();
    }

    @Override
    public MetaFile[] listFiles() {
        File[] list = mFile.listFiles();
        if (list != null) {
            MetaFile[] ret = new MetaFile[list.length];
            for (int i = 0; i < list.length; i++) {
                ret[i] = wrap(list[i]);
            }
            return ret;
        }
        return null;
    }

    @Override
    @Deprecated
    public String getAbsolutePath() {
        return mFile.getAbsolutePath();
    }

    @Override
    public String toString() {
        return mFile.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JavaFile) {
            return mFile.equals(((JavaFile) o).mFile);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mFile.hashCode();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new FileInputStream(mFile);
    }
    @Override
    public OutputStream openOutputStream() throws IOException {
        return new FileOutputStream(mFile);
    }

    @Override
    public boolean isJavaFile() {
        return true;
    }

    @Override
    public File getJavaFile() {
        return mFile;
    }

    @Override
    public boolean isLocalFile() {
        return true;
    }

    @Override
    public String getAccessPath() {
        return mFile.getPath();
    }

    @Override
    public Uri getUri() {
        return Uri.fromFile(mFile);
    }

    @Override
    public boolean createNewFile() throws IOException {
        return mFile.createNewFile();
    }

    @Override
    public boolean delete() {
        return mFile.delete();
    }

    @Override
    public int getBucketId() {
        // Note: Android is using the default locale to calculate this
        // in order not to break it, we do the same
        String parent = getParent();
        return parent != null ? parent.toLowerCase().hashCode() : -1;
    }

    /** wraps File, returns null on null input */
    private static JavaFile wrap(File f) {
        return f != null ? new JavaFile(f) : null;
    }

    /** creates JavaFile based on String */
    public static JavaFile fromString(String path) {
        return new JavaFile(new File(path));
    }

    /** creates JavaFile based on Uri */
    public static JavaFile fromUri(Uri uri) {
        return fromString(uri.getPath());
    }

    @Override
    protected MetaFile getCombined(String path) {
        return wrap(new File(mFile, path));
    }

    /** returns true if path seems to be a legal File path */
    public static boolean pathLegal(String path) {
        return path != null;
    }

    /** returns true if uri seems to be a legal File uri */
    public static boolean uriLegal(Uri uri) {
        // either "file:///path" or just "/path"
        return uri != null
                && (uri.getScheme() == null || FILE_SCHEME.equals(uri.getScheme()))
                && pathLegal(uri.getPath());
    }

    @Override
    protected FileType getFileTypeInternal() {
        if (isDirectory())
            return FileType.Directory;

        return FileType.File;
    }

    @Override
    public boolean canRead() {
        return mFile.canRead();
    }

    @Override
    public boolean canWrite() {
        return mFile.canWrite();
    }
}
