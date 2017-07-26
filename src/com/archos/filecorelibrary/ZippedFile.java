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
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;


public class ZippedFile extends MetaFile {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private final File mFile;
    private final ZipEntry mZipEntry;
    private final String displayPath;
    private final FileType mCustomFileType;
    private String mCustomName;

    public ZippedFile(String path) {
        this(new File(path), null);
    }

    public ZippedFile(File file, String name, ZipEntry zipEntry) {
        this(file, zipEntry);
        if (name != null) mCustomName = name;
    }

    public ZippedFile(File file, ZipEntry zipEntry) {
        if (file == null)
            throw new IllegalArgumentException("file cannot be null");
        mFile = file;
        mZipEntry = zipEntry;
        mCustomFileType = file.getPath().endsWith("/") ? FileType.ZipDirectory : FileType.ZipFile;
        if (zipEntry == null)
            displayPath = file.getPath();
        else {
            displayPath = file.getPath()+"/"+mZipEntry.getName();
            mCustomName = getName();
        }
    }

    public boolean isZippedFile() {
        return true;
    }

    @Override
    public boolean isLocalFile() {
        return true;
    }

    @Override
    public boolean exists() {
        return mFile.exists();
    }

    public File getFile(){
        return mFile;
    }

    public ZipEntry getEntry(){
        return mZipEntry;
    }

    @Override
    public String getName() {
        if (mCustomName != null)
            return mCustomName;

        if (mZipEntry == null) {
            mCustomName = mFile.getName();
        } else {
            String name = mZipEntry.getName();
            if (mZipEntry.isDirectory())
                name = name.substring(0,name.length()-1);
            mCustomName = name.substring(name.lastIndexOf('/')+1);
        }
        return mCustomName;
    }

    @Override
    public String getParent() {
        return mFile.getParent();
    }

    public String getParentDisplayPath(){
        String path = displayPath.substring(0,displayPath.length()-1);
        path = path.substring(0,path.lastIndexOf('/')+1);
        return path;
    }

    @Override
    public MetaFile getParentFile() {
        return MetaFile.from(mFile.getParentFile());
    }

    @Override
    public boolean isDirectory() {
        return mZipEntry != null && mZipEntry.isDirectory();
    }

    @Override
    public boolean isFile() {
        return mZipEntry == null || !mZipEntry.isDirectory();
    }

    @Override
    public long lastModified() {
        if (mZipEntry != null)
            return mZipEntry.getTime();
        else
            return mFile.lastModified();
    }

    @Override
    public long length() {
        if (mZipEntry != null)
            return mZipEntry.getSize();
        else
            return mFile.length();
    }

    @SuppressWarnings("unchecked")
    @Override
    public MetaFile[] listFiles() {
        if (mZipEntry == null){
            ZipFile zipFile;
            try {
                zipFile = new ZipFile(mFile);
                ArrayList<ZipEntry> entries = (ArrayList<ZipEntry>) Collections.list(zipFile.entries());
                MetaFile[] list = new MetaFile[entries.size()];
                for (int i = 0 ; i< entries.size() ; i++){
                    list[i] = wrap(mFile, entries.get(i));

                }
                return list;
            } catch (ZipException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    @Deprecated
    public String getAbsolutePath() {
        return displayPath;
    }

    @Override
    public String toString() {
        return displayPath;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ZippedFile) {
            ZippedFile other = (ZippedFile) o;
            return displayPath.equals(other.displayPath);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return displayPath.hashCode();
    }

    @Override
    public boolean isSmbFile() {
        return false;
    }

    @Override
    public String getAccessPath() {
        return displayPath;
    }

    @Override
    public Uri getUri() {
        // TODO - we may want to use our own zip:// like uri
        // or something like that.
        return Uri.fromFile(mFile);
    }

    /** wraps file, returns null on null input */
    private static ZippedFile wrap(File file) {
        return file != null ? new ZippedFile(file,null) : null;
    }

    /** wraps file, returns null on null input */
    private static ZippedFile wrap(File file, ZipEntry zipEntry) {
        return file != null && zipEntry !=null ? new ZippedFile(file,zipEntry) : null;
    }

    /** logs exception */
    private static void log(String method, Exception e) {
        Log.d(MetaFile.TAG, "Exception in " + method + "()", e);
    }

    /** true if path seems to be a legal smb file */
    public static boolean pathLegal(String path) {
        return path != null && path.contains(".zip");
    }

    /** true if uri seems to be a legal smb file uri */
    public static boolean uriLegal(Uri uri) {
        return uri != null && uri.toString().contains(".zip");
    }

    /** creates JcifsFile or null from path */
    public static ZippedFile fromString(String path) {
        Log.d("zip", "fromString "+path);
        if (path.endsWith(".zip"))
            return wrap(new File(path));
        else {
            int zipPos = path.indexOf(".zip");
            if (zipPos == -1)
                return null;
            File file = new File(path.substring(0, zipPos+4));
            ZipFile zipFile;
            try {
                zipFile = new ZipFile(file);
                return wrap(file, zipFile.getEntry(path.substring(zipPos+4)));
            } catch (ZipException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /** creates JcifsFile or null from uri */
    public static ZippedFile fromUri(Uri uri) {
        return fromString(uri.toString());
    }

    @Override
    protected String getMimeTypeFromFile() {
        if (isFile())
            return MimeUtils.guessMimeTypeFromExtension(displayPath.substring(displayPath.lastIndexOf('.')+1).toLowerCase());
        return null;
    }


    @Override
    protected FileType getFileTypeInternal() {
        return mCustomFileType;
    }

    @Override
    public boolean canRead() {
        return mFile.canRead();
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    protected MetaFile getCombined(String path) {
        return fromString(displayPath+"/"+path);
    }
}
