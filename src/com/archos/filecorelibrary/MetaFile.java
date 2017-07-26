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
import android.provider.MediaStore;
import android.util.Log;

import com.archos.filecorelibrary.samba.SambaConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import jcifs2.smb.SmbFile;

/**
 * Abstraction of a "file". Several file types are supported.
 * Not all File methods implemented yet. Constructors are private,
 * use {@link #from(File)} etc.
 */
public abstract class MetaFile implements Comparable<MetaFile>, Serializable {

    public enum FileType {
        Directory, File, Shortcut, ZipFile, ZipDirectory, SmbFile, SSHFile,FTPFile, SmbDir,ClickableItem, Http
    }

    // Overwritable attributes - use set methods to set them.
    private String mMimeType;
    private String mName;
    private String mDisplayPath;
    private FileType mFileType = FileType.File;

    /**
     * Object that can be set to whatever you want for whatever reason
     * (may hold a bitmap, for example)
     */
    public Object mHolder;
    
    protected static final String TAG = MetaFile.class.getSimpleName();

    /**
     * @return A String path that can be used
     * to construct a MetaFile and is the path we store in the database.<p>
     * In case this represents an {@link SmbFile},
     * the path does NOT contain smb://<b>user:pass@</b>server/share/ like credentials.
     * Use {@link SambaConfiguration#handleCredentials(String)} on this path if you
     * really need them.<p>
     *
     * Also be careful about implementation differences regarding paths:
     * smb directories will end with a '/' while regular directories won't.
     */
    public abstract String getAccessPath();

    /**
     * @return path used for display purposes only.<p>
     *
     * Also be careful about implementation differences regarding paths:
     * smb directories will end with a '/' while regular directories won't.<p>
     *
     * Can be overridden by using {@link #setDisplayPath(String)}
     */
    public final String getDisplayPath() {
        String result;

        // 1. Try overridden path
        result = mDisplayPath;
        if (result != null)
            return result;

        // 2. ask subclasses
        result = getDisplayPathInternal();
        if (result != null)
            return result;

        // 3. default to AccessPath
        return getAccessPath();
    }

    /**
     * Sets a DisplayPath for this file.
     * Use {@link #getDisplayPath()} to get it.
     *
     * @return The MetaFile itself to allow chaining of setters
     */
    public final MetaFile setDisplayPath(String path) {
        mDisplayPath =  path;
        return this;
    }

    /** Override to provide custom display paths */
    protected String getDisplayPathInternal() {
        return null;
    }

    /** true if the underlying file exists */
    public abstract boolean exists();

    /** true if the underlying file is readable */
    public abstract boolean canRead();

    /** true if the underlying file writable */
    public abstract boolean canWrite();

    /** the name of the underlying file */
    public abstract String getName();

    /** parent file, can be used in {@link #from(String)} */
    public abstract String getParent();

    /** parent file, display path */
    public String getParentDisplayPath() {
        MetaFile parent = getParentFile();
        return parent != null ? parent.getDisplayPath() : null;
    }
    /** parent file, display path */
    public String getParentAccessPath() {
        MetaFile parent = getParentFile();
        return parent != null ? parent.getAccessPath() : null;
    }
    /** parent file */
    public abstract MetaFile getParentFile();

    /**
     * @deprecated use {@link #getAccessPath()} or {@link #getDisplayPath()}, this
     * just returns {@link #getAccessPath()}
     */
    @Deprecated
    public final String getPath() {
        return getAccessPath();
    }

    /** true if underlying entity is a directory */
    public abstract boolean isDirectory();

    /** true if underlying entity is a file */
    public abstract boolean isFile();

    /** date of last modification, -1 if unavailable */
    public abstract long lastModified();

    /** filesize, -1 if unavailable */
    public abstract long length();

    /** lists all files in a directory, can be null */
    public abstract MetaFile[] listFiles();

    public interface MetaFileFilter {
        boolean accept(MetaFile file);
    }

    /**
     * lists all files in a directory, result can be null,
     * optional filter to list just some files
     * optional comparator to define a sort order
     **/
    public final MetaFile[] listFiles(MetaFileFilter filter, Comparator<MetaFile> sortOrder) {
        MetaFile[] list = listFiles();
        // nothing to list or nothing to do in addition? return here!
        if (list == null || (filter == null && sortOrder == null))
            return list;

        List<MetaFile> tempList;
        if (filter != null) {
            // with filter
            tempList = new ArrayList<MetaFile>(list.length);
            for (MetaFile metaFile : list) {
                if (filter.accept(metaFile))
                    tempList.add(metaFile);
            }
        } else {
            // without filter
            tempList = Arrays.asList(list);
        }

        // if sort
        if (sortOrder != null)
            Collections.sort(tempList, sortOrder);

        return tempList.toArray(new MetaFile[tempList.size()]);
    }

    /**
     * @deprecated getAbsolutePath is not really useful on Android.
     * All it would do is to prepend a {@code /} to a path without.<p>
     * Use {@link #getAccessPath()} or {@link #getDisplayPath()} instead.<p>
     * {@link SmbFile} does not have that method so this just returns
     * {@link #getAccessPath()}.
     */
    @Deprecated
    public abstract String getAbsolutePath();

    /**
     * Human readable representations<p>
     * use {@link MetaFile#getAccessPath()} for machine readable version.
     */
    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + getAccessPath() + "]";
    }

    /**
     * Notice for SmbFile: the behavior here is different than
     * {@link SmbFile#equals(Object)}.<p>
     * This returns true if {@link #getAccessPath()} is equals whereas
     * {@link SmbFile} resolves hostnames to ip addresses to do that.
     */
    @Override
    public abstract boolean equals(Object o);

    /**
     * Notice for SmbFile: the behavior here is different than
     * {@link SmbFile#hashCode()}.<p>
     * This just returns the hash of {@link #getAccessPath()} whereas
     * {@link SmbFile} resolves hostnames to ip addresses to do that.
     */
    @Override
    public abstract int hashCode();

    public InputStream openInputStream() throws IOException {
        throw new IOException("Unsupported Operation");
    }
    public OutputStream openOutputStream() throws IOException {
        throw new IOException("Unsupported Operation");
    }

    /** true if internally a {@link File} */
    public boolean isJavaFile() {
        // default behavior
        return false;
    }

    /** returns the internal {@link File} if it is one */
    public File getJavaFile() {
        // default behavior
        return null;
    }

    /** true if internally a {@link SmbFile} */
    public boolean isSmbFile() {
        // default behavior
        return false;
    }

    /** returns the internal {@link SmbFile} if it is one */
    public SmbFile getSmbFile() {
        // default behavior
        return null;
    }

 
    /** true if internally a {@link ZippedFile} */
    public boolean isZippedFile() {
        // default behavior
        return false;
    }

  
    /**
     * true if the file is present on the local filesystem and can probably be accessed from
     * the UI thread without big worries. It's still bad to do so
     **/
    public boolean isLocalFile() {
        return false;
    }

    /** null for java files, {@link SmbFile#getServer()} if appropriate */
    public String getServer() {
        // default behavior
        return null;
    }

    public boolean canBeIndexed() {
        return isJavaFile() || isSmbFile();
    }

    /**
     * returns an Uri that describes this file<p>
     * like <code>smb://server/share/file</code><p>
     * or <code>file:///mnt/storage/file</code><p>
     * The Uri does not contain passwords. That could leak to the log.
     **/
    public abstract Uri getUri();

    public boolean createNewFile() throws IOException {
        // default behavior - throw exception because creation not possible
        throw new IOException("Operation not available");
    }

    /**
     * tries to delete this file / directory, for safety reasons not over SMB<p>
     * use {@link #deleteAnywhere()} to delete there.
     **/
    public boolean delete() {
        // default - deletion failed
        return false;
    }

    /**
     * Computes the BUCKET_ID (See {@link MediaStore.Video.VideoColumns#BUCKET_ID})
     * for this file. The id is used in our and Android's database to identify the
     * folder of a file/directory. All files / subdirectories in a directory have
     * the same id. The folder in which all these files reside has the bucket id
     * of it's parent folder. <p>
     * Note: Since the bucket id is a hash over the lowercased parent path, chances
     * are that it's not 100% perfect. Hash collision and case sensitive filenames
     * can in theory lead to some confusion.
     *
     * @return the bucket id or -1 if not available
     */
    public int getBucketId() {
        String parent = getParent();
        // Here we do use language independent toLowerCase
        return parent != null ? parent.toLowerCase(Locale.ROOT).hashCode() : -1;
    }

    /** construction of MetaFile(MetaFile, String) */
    protected abstract MetaFile getCombined(String path);

    // --------------- Factory methods ------------------------------------- //
    /** returns null or MetaFile based on input */
    public static MetaFile from(File file) {
        return file == null ? null : new JavaFile(file);
    }

    /** returns null or MetaFile based on input */
    public static MetaFile from(SmbFile file) {
        return file == null ? null : from(file.getPath());
    }


    /**
     * returns null or a MetaFile based on input<p>
     * will also add missing credentials if smb://
     *
     * <code>"smb://server/share/path/file.ext"</code> or
     * <code>"/mnt/storage/dir/file.ext"</code> are supported
     **/
    public static MetaFile from(String path) {
        if (path == null || path.isEmpty())
            return null;
        if (JcifsFile.pathLegal(path)) {
            return JcifsFile.fromString(path, true);
        }
        if (HttpFile.pathLegal(path)) {
            return HttpFile.fromString(path);
        }
        

        if (JavaFile.pathLegal(path)) {
            return JavaFile.fromString(path);
        }
        Log.w(TAG, "unsupported Path:" + path);
        return null;
    }

    /**
     * returns null or a MetaFile based on input<p>
     * will also add missing credentials if smb://
     *
     * <code>"smb://server/share/path/file.ext"</code> or
     * <code>"file:///mnt/storage/dir/file.ext"</code> are supported
     **/
    public static MetaFile from(Uri uri) {
        if (uri == null) return null;
        if (JcifsFile.uriLegal(uri)) {
            return JcifsFile.fromUri(uri, true);
        }
        if (HttpFile.uriLegal(uri)){
            return HttpFile.fromUri(uri);
        }
        if (JavaFile.uriLegal(uri)) {
            return JavaFile.fromUri(uri);
        }
        // this will effectively never happen since JavaFile will already take the file.
        // we need some special zip:// uri or something like that.
        if (ZippedFile.uriLegal(uri)){
            return ZippedFile.fromUri(uri);
        }
        Log.w(TAG, "Unsupported Uri:" + uri.toString());
        return null;
    }

    /**
     * Returns null or results of {@link File#File(File, String)}
     * / {@link SmbFile#SmbFile(SmbFile, String)}
     */
    public static MetaFile from(MetaFile file, String path) {
        if (path == null || path.isEmpty()) {
            // return unaltered file, may be null
            return file;
        }
        if (file == null) {
            // no file but path
            return from(path);
        }

        // join
        return file.getCombined(path);
    }

    public static Uri pathToUri(String path) {
        if (path == null) {
            Log.e(TAG, "pathToUri can't work with null path");
            return null;
        }
        MetaFile temp = MetaFile.from(path);
        if (temp != null)
            return temp.getUri();
        Log.e(TAG, "pathToUri can't handle [" + path + "] correctly");
        // fallback to something that might be useful..
        return Uri.parse("file://" + Uri.encode(path, File.separator));
    }

    // ---------------- MimeType methods
    /**
     * Sets a MimeType, use {@link #getMimeType()} to get.
     * @return The MetaFile itself to allow chaining of setters
     */
    public final MetaFile setMimeType(String mimeType) {
        mMimeType = mimeType;
        return this;
    }

    /**
     * @return the MimeType set by {@link #setMimeType(String)} or derived from
     * the real file. Custom set type is used over derived type
     */
    public final String getMimeType() {
        if (mMimeType != null)
            return mMimeType;

        return getMimeTypeFromFile();
    }

    /** Override to supply custom MimeTypes */
    protected String getMimeTypeFromFile() {
        String extension = getFileExtension();
        String mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
       
       	
        return mimeType;
    }

    /** Gets the lowercase file extension of this file. Can be null */
    public String getFileExtension() {
        String name = getName();
        if (name != null) {
            int lastDot = name.lastIndexOf('.');
            if (lastDot > 0) {
                name = name.substring(lastDot + 1);
            }
            name = name.toLowerCase(Locale.ROOT);
        }
        return name;
    }

    public String getFileNameWithoutExtension() {
        String name = getName();
        if (name != null) {
            int dotPos = name.lastIndexOf('.');
            if (dotPos > 0) {
                name = name.substring(0, dotPos);
            }
        }
        return name;
    }

    // ---------------- FakeName methods
    /**
     * Sets a name for this file.
     * Use {@link #getFakeName()} to get it. {@link #getName()} will always return
     * the true filename.
     * @return The MetaFile itself to allow chaining of setters
     */
    public final MetaFile setFakeName(String name) {
        mName = name;
        return this;
    }

    /**
     * @return the path set by {@link #setFakeName} or the real filename if unset.
     * - /mnt/storage/video.avi -> video.avi
     * - /mnt/storage/ -> storage
     */
    public final String getFakeName() {
        if (mName != null)
            return mName;
        return getName();
    }

    // ---------------- FileType methods
    /**
     * @return the FileType set by {@link #setFileType(FileType)} or derived from
     * the real file. Custom set Type overrides derived type.
     */
    public final FileType getFileType() {
        if (mFileType != null)
            return mFileType;
        return getFileTypeInternal();
    }

    /** Override to supply custom FileTypes */
    protected FileType getFileTypeInternal() {
        return null;
    }

    /**
     * Sets a FileType, use {@link #getFileType()} to get.
     * @return The MetaFile itself to allow chaining of setters
     */
    public MetaFile setFileType(FileType type) {
        mFileType = type;
        return this;
    }
    /**
     * @return true if FileType == FileType.Shortcut;
     */
    public boolean isShortcut(){
        return mFileType == FileType.Shortcut;
    }

    /**
     * Compares this file to another one. Comparison is based on filename first
     * then by path. Comparison ignores case.
     */
    @Override
    public final int compareTo(MetaFile f) {
        // Note: It's IMO not a good idea to have the default comparator compare
        // by filename only.

        // first compare filename
        int ret = stringCompare(getName(), f.getName(), true);
        if (ret == 0) {
            // if they are the same compare full path
            ret = stringCompare(getAccessPath(), f.getAccessPath(), true);
        }
        return ret;
    }

    /**
     * Basic copy implementation, overwrites data, does not delete on error
     */
    public void copyTo(MetaFile target) throws IOException {
        if (target == null) return;

        OutputStream outputStream = null;
        InputStream inputStream = null;

        try {
            outputStream = target.openOutputStream();
            inputStream = openInputStream();
            IOUtils.streamCopy(inputStream, outputStream);
            // close here too so closing errors are thrown
            outputStream.close();
            inputStream.close();
        } finally {
            IOUtils.closeSilently(outputStream);
            IOUtils.closeSilently(inputStream);
        }
    }

    /** Compares by accesspath, ignores case */
    public static final Comparator<MetaFile> COMPARATOR_CASE_INSENSITIVE = new Comparator<MetaFile>() {
        @Override
        public int compare(MetaFile lhs, MetaFile rhs) {
            if (lhs == null || rhs == null) {
                return nullCompare(lhs, rhs);
            }
            return stringCompare(lhs.getAccessPath(), rhs.getAccessPath(), true);
        }
    };
    /** Compares by accesspath, does not ignore case */
    public static final Comparator<MetaFile> COMPARATOR_CASE_SENSITIVE = new Comparator<MetaFile>() {
        @Override
        public int compare(MetaFile lhs, MetaFile rhs) {
            if (lhs == null || rhs == null) {
                return nullCompare(lhs, rhs);
            }
            return stringCompare(lhs.getAccessPath(), rhs.getAccessPath(), false);
        }
    };
    /** To be used with {@link #listFiles(MetaFileFilter)} */
    public static final MetaFileFilter FILTER_MIMETYPE_VIDEO = new MetaFileFilter() {
        @Override
        public boolean accept(MetaFile file) {
            if (file != null && file.isFile()) {
                String mimeType = file.getMimeType();
                if (mimeType != null && mimeType.startsWith("video/"))
                    return true;
            }
            return false;
        }
    };
    public static final MetaFileFilter FILTER_MIMETYPE_AUDIO = new MetaFileFilter() {
        @Override
        public boolean accept(MetaFile file) {
            if (file != null && file.isFile()) {
                String mimeType = file.getMimeType();
                if (mimeType != null && mimeType.startsWith("audio/"))
                    return true;
            }
            return false;
        }
    };

    protected static final int stringCompare(String one, String two, boolean ignoreCase) {
        if (one == null || two == null)
            return nullCompare(one, two);

        return ignoreCase ? one.compareToIgnoreCase(two) : one.compareTo(two);
    }

    protected static final int nullCompare(Object lhs, Object rhs) {
        // both null = same, otherwise null < anything
        if (lhs == null && rhs == null)
            return 0;
        if (lhs == null)
            return -1;
        if (rhs == null)
            return 1;
        throw new AssertionError("nullCompare needs to have one parameter null");
    }

    /**
     * like delete but also works over SMB <p>
     * See {@link SmbFile#delete()} since deleting directories over SMB works recursively
     **/
    public boolean deleteAnywhere() {
        return delete();
    }

	
}
