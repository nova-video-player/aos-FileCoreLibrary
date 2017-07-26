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
import android.os.NetworkOnMainThreadException;
import android.util.Log;

import com.archos.filecorelibrary.samba.SambaConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import jcifs2.smb.SmbException;
import jcifs2.smb.SmbFile;
import jcifs2.smb.SmbFileInputStream;
import jcifs2.smb.SmbFileOutputStream;

/**
 * NOTE: Credentials are secret now. We don't leak them out anywhere from this implementation.
 * The only way to get them is to do {@link #getSmbFile()} or to ask {@link SambaConfiguration}
 */
public class JcifsFile extends MetaFile {
    private static final String SMB_SCHEME = "smb";
    private static final String SMB_START = SMB_SCHEME + "://";

    private final SmbFile mFile;
    private final String mPathNoCredentials;
    private final FileType mCustomFileType;

    public JcifsFile(SmbFile file) {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");

        mFile = file;
        String path = file.getCanonicalPath();
        mCustomFileType = path.endsWith("/") ? FileType.SmbDir : FileType.SmbFile;
        mPathNoCredentials = SambaConfiguration.getNoCredentialsPath(path);
    }

    @Override
    public boolean exists() {
        try {
            return mFile.exists();
        } catch (SmbException e) {
            log("exists", e);
            return false;
        } catch (NetworkOnMainThreadException e){
            log("exists", e);
            return false;
        } catch (RuntimeException e){
            log("exists", e);
            return false;
        }
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
        String parent = mFile.getParent();
        SmbFile parentFile;
        try {
            parentFile = new SmbFile(parent);
        } catch (MalformedURLException e) {
            log("getParentFile", e);
            parentFile = null;
        } catch (NetworkOnMainThreadException e){
            log("getParentFile", e);
            parentFile = null;
        }
        return wrap(parentFile);
    }

    @Override
    public boolean isDirectory() {
        try {
            return mFile.isDirectory();
        } catch (SmbException e) {
            log("isDirectory", e);
            return mPathNoCredentials.endsWith("/");
        } catch (NetworkOnMainThreadException e){
            log("isDirectory", e);
            return mPathNoCredentials.endsWith("/");
        }
    }

    @Override
    public boolean isFile() {
        try {
            return mFile.isFile();
        } catch (SmbException e) {
            log("isFile", e);
            return !mPathNoCredentials.endsWith("/");
        } catch (NetworkOnMainThreadException e){
            log("isFile", e);
            return !mPathNoCredentials.endsWith("/");
        }
    }

    @Override
    public long lastModified() {
        try {
            return mFile.lastModified();
        } catch (SmbException e) {
            log("lastModified", e);
            return -1;
        } catch (NetworkOnMainThreadException e){
            log("lastModified", e);
            return -1;
        }
    }

    @Override
    public long length() {
        try {
            return mFile.length();
        } catch (SmbException e) {
            log("length", e);
            return -1;
        } catch (NetworkOnMainThreadException e){
            log("length", e);
            return -1;
        }
    }

    @Override
    public MetaFile[] listFiles() {
        try {
            SmbFile[] files = mFile.listFiles();
            if (files != null) {
                MetaFile[] ret = new MetaFile[files.length];
                for (int i = 0; i < files.length; i++) {
                    ret[i] = wrap(files[i]);
                }
                return ret;
            }
        } catch (SmbException e) {
            log("listFiles", e);
        } catch (NetworkOnMainThreadException e){
            log("listFiles", e);
        }
        return null;
    }

    @Override
    @Deprecated
    public String getAbsolutePath() {
        return mPathNoCredentials;
    }

    @Override
    public String toString() {
        return mPathNoCredentials;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JcifsFile) {
            JcifsFile other = (JcifsFile) o;
            return mPathNoCredentials.equals(other.mPathNoCredentials);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mPathNoCredentials.hashCode();
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new SmbFileInputStream(mFile);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return new SmbFileOutputStream(mFile);
    }

    @Override
    public boolean isSmbFile() {
        return true;
    }

    @Override
    public SmbFile getSmbFile() {
        return mFile;
    }

    @Override
    public String getServer() {
        return mFile.getServer();
    }

    @Override
    public String getAccessPath() {
        return mPathNoCredentials;
    }

    @Override
    public Uri getUri() {
        return Uri.parse(mPathNoCredentials);
    }

    @Override
    public FileType getFileTypeInternal() {
        return mCustomFileType;
    }

    /** wraps file, returns null on null input */
    private static JcifsFile wrap(SmbFile file) {
        return file != null ? new JcifsFile(file) : null;
    }

    /** logs exception */
    private static void log(String method, Exception e) {
        Log.d(MetaFile.TAG, "Exception in " + method + "()", e);
    }

    /** true if path seems to be a legal smb file */
    public static boolean pathLegal(String path) {
        return path != null && path.startsWith(SMB_START);
    }

    /** true if uri seems to be a legal smb file uri */
    public static boolean uriLegal(Uri uri) {
        return uri != null && SMB_SCHEME.equals(uri.getScheme());
    }

    /** creates JcifsFile or null from path */
    public static JcifsFile fromString(String path, boolean resolveCredentials) {
        try {
            if (resolveCredentials) {
                path = SambaConfiguration.getCredentials(path);
            }
            SmbFile file = new SmbFile(path);
            return new JcifsFile(file);
        } catch (MalformedURLException e) {
            log("fromString", e);
            return null;
        } catch (NetworkOnMainThreadException e){
            log("fromString", e);
            return null;
        }
    }

    /** creates JcifsFile or null from uri */
    public static JcifsFile fromUri(Uri uri, boolean resolveCredentials) {
        return fromString(uri.toString(), resolveCredentials);
    }

    @Override
    protected MetaFile getCombined(String path) {
        try {
            return wrap(new SmbFile(mFile, path));
        } catch (MalformedURLException e) {
            log("getCombined", e);
        } catch (UnknownHostException e) {
            log("getCombined", e);
        } catch (NetworkOnMainThreadException e){
            log("getCombined", e);
            return null;
        }
        return null;
    }

    @Override
    public boolean canRead() {
        try {
            return mFile.canRead();
        } catch (SmbException e) {
            log("canRead", e);
            return false;
        } catch (NetworkOnMainThreadException e){
            log("canRead", e);
            return false;
        }
    }

    @Override
    public boolean canWrite() {
        try {
            return mFile.canWrite();
        } catch (SmbException e) {
            log("canRead", e);
            return false;
        } catch (NetworkOnMainThreadException e){
            log("canRead", e);
            return false;
        }
    }

    @Override
    public boolean deleteAnywhere() {
        try {
            mFile.delete();
            return true;
        } catch (SmbException e) {
            log("deleteAnywhere", e);
        } catch (NetworkOnMainThreadException e) {
            log("deleteAnywhere", e);
        }
        return false;
    }
}
