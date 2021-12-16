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

import static com.archos.filecorelibrary.FileUtils.prefixPublicNfoPosterUri;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.environment.ArchosUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class LocalStorageFileEditor extends FileEditor {
    private final Context mContext;

    private static final Logger log = LoggerFactory.getLogger(LocalStorageFileEditor.class);

    private static final String PICTURES_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath();
    private static final String USBHOST_PTP_PATH = ExtStorageManager.getExternalStorageUsbHostPtpDirectory().getPath();
    private static final String VIDEO_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath();
    private static final String MUSIC_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getPath();
    private static final String DCIM_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getPath();
    private static final File STORAGE = Environment.getExternalStorageDirectory();

    /**
     * These directories cannot be deleted, renamed, moved, ...
     */
    public static final String[] DO_NOT_TOUCH = {
        DCIM_PATH,(new File(STORAGE, "Android")).getPath(), MUSIC_PATH, PICTURES_PATH, VIDEO_PATH, USBHOST_PTP_PATH
    };

    public LocalStorageFileEditor(Uri uri, Context context) {
        super(uri);
        mContext = context;
    }

    public InputStream getInputStream() throws FileNotFoundException {
        return new FileInputStream(new File(mUri.getPath()));
    }

    // Our FileEditor API throw an exception when the delete fails.
    // Since the java.File API does not do it (returns false instead) we're using this home-made exception instead
    public static class DeleteFailException extends Exception {}

    public static boolean checkIfShouldNotTouchFolder(Uri uri) {
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return Arrays.asList(DO_NOT_TOUCH).contains(path);
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(new File(mUri.getPath()), "r");
        if (raf != null) {
            raf.seek(from);
            return Channels.newInputStream(raf.getChannel());
        }
        return null;
    }

    public OutputStream getOutputStream() throws IOException {
        OutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(mUri.getPath()));
        }
        catch (FileNotFoundException e) {
            // when permission error, try externalsdfilewriter
            if (mContext != null) {
                ExternalSDFileWriter external = new ExternalSDFileWriter(mContext.getContentResolver(), new File(mUri.getPath()));
                fos = external.write();
            }
            else {
                throw e;
            }
        }
        return fos;
    }

    @Override
    public boolean touchFile() {
        try {
            boolean create = new File(mUri.getPath()).createNewFile();
            if (create && mContext != null) {
                scanFile(mUri, false);
            }
            return create;
        } catch (IOException e) {
            return false;
        }
    }

    private void scanFile(Uri toIndex, boolean isAddingDirectory) {
        if (isAddingDirectory) {
            ExternalSDFileWriter external = new ExternalSDFileWriter(mContext.getContentResolver(), new File(toIndex.getPath()));
            external.addFolderToDB();
            return;
        }
        if (toIndex.getScheme() == null) {
            toIndex = Uri.parse("file://" + toIndex.toString());
        }
        Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        scanIntent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        scanIntent.setData(toIndex);
        mContext.sendBroadcast(scanIntent);
    }

    @Override
    public boolean mkdir() {
        if (!new File(mUri.getPath()).mkdir()) {
            //when permission error, try externalsdfilewriter
            if (mContext != null) {
                ExternalSDFileWriter external = new ExternalSDFileWriter(mContext.getContentResolver(), new File(mUri.getPath()));
                return external.mkdir();
            }
        }
        else if (mContext != null) {
            scanFile(mUri, true);
            return true;
        }
        return false;
    }

    @Override
    public Boolean delete() throws Exception {
        Boolean isDeleteOK = null;
        if (checkIfShouldNotTouchFolder(mUri)) {
            // when some folders are protected
            throw new DeleteFailException();
        }
        // TODO: does not work on usb external storage on recent Android
        File fileToDelete = new File(mUri.getPath());
        if (fileToDelete.isDirectory()) {
            log.debug("delete: folder " + mUri.getPath());
            isDeleteOK = deleteFolder(mUri);
            return isDeleteOK;
        } else {
            log.debug("delete: file " + mUri.getPath());
            isDeleteOK = deleteFile(fileToDelete);
            return isDeleteOK;
        }
    }

    public Boolean deleteDir(Uri dirUri) throws Exception {
        Boolean isDeleteOK = null;
        File dirToDelete = new File(dirUri.getPath());
        if (dirToDelete.isDirectory()) {
            log.debug("deleteDir: folder " + dirUri.getPath());
            isDeleteOK = dirToDelete.delete();
            return isDeleteOK;
        } else {
            log.debug("deleteDir: not a folder " + dirUri.getPath());
            return false;
        }
    }

    public void deleteFromDatabase(File file) {
        deleteFromDatabase(file.getAbsolutePath());
    }

    public void deleteFromDatabase(Uri uri) {
        deleteFromDatabase(uri.getPath());
    }

    private void deleteFromDatabase(String path) {
        log.debug("deleteFromDatabase: " + path);
        if (mContext != null) {
            Uri extUri = MediaStore.Files.getContentUri("external");
            String where = MediaStore.MediaColumns.DATA + "=?";
            String[] selectionArgs = { path };
            log.debug("deleteFromDatabase: where " + where + ", selectionArgs " + Arrays.toString(selectionArgs));
            mContext.getContentResolver().delete(extUri, where, selectionArgs);
        }
    }

    private Boolean deleteFile(File file) throws DeleteFailException {
        Boolean isDeleteOK = null;
        log.debug("deleteFile: file " + file.getPath() + ", mUri " + mUri);
        if (!file.delete()) {
            if (mContext != null) {
                if (Build.VERSION.SDK_INT > 29 && (! FileUtils.isNovaOwnedFile(file))) {
                    log.debug("deleteFile: delete failed -> going the Q way");
                    // isDeleteOK can be null since UI involved in Android Q+
                    isDeleteOK = FileUtilsQ.delete(FileUtilsQ.getDeleteLauncher(), FileUtilsQ.getContentUri(mUri));
                    if (isDeleteOK != null && ! isDeleteOK)
                        throw new DeleteFailException();
                    else return isDeleteOK;
                } else {
                    log.debug("deleteFile: delete failed -> going the traditional way");
                    ExternalSDFileWriter external = new ExternalSDFileWriter(mContext.getContentResolver(), file);
                    try {
                        if (!external.delete()) {
                            throw new DeleteFailException();
                        }
                        return true;
                    } catch (IOException e1) {}
                }
            } else {
                throw new DeleteFailException();
            }
        } else {
            // nova db delete
            deleteFromDatabase(file);
            return true;
        }
        return isDeleteOK;
    }

    private Boolean deleteFolder(Uri uri) throws Exception {
        Boolean isDeleteOK = null;
        log.debug("deleteFolder: " + uri);
        LocalStorageRawLister lsrl = new LocalStorageRawLister(uri);
        List<MetaFile2> children = lsrl.getFileList();
        if (children != null) {
            List<Uri> toDeleteLocal = new ArrayList<>();
            List<Uri> contentUrisToDelete = new ArrayList<>();
            Uri contentUri;
            for (MetaFile2 child : children) {
                toDeleteLocal.add(child.getUri());
                contentUri = FileUtilsQ.getContentUri(child.getUri());
                log.debug("deleteFolder: files to be batch processed: " + child.getUri() + " -> contentUri " + contentUri);
                // if contentUri is null file has already been deleted before...
                if (contentUri != null) contentUrisToDelete.add(contentUri);
                else {
                    log.debug("deleteFolder: " + child.getUri() + " has no contentUri, try java file delete");
                    File toDelete = new File(child.getUri().getPath());
                    toDelete.delete();
                }
            }
            if (! contentUrisToDelete.isEmpty())
                isDeleteOK = FileUtilsQ.deleteAll(FileUtilsQ.getDeleteLauncher(), contentUrisToDelete);
            else isDeleteOK = true;
            if (children.isEmpty()) {
                log.debug("deleteFolder: empty children for " + uri);
                File toDelete = new File(uri.getPath());
                toDelete.delete();
            }
        } else { // in case of single empty directory we get there too
            log.debug("deleteFolder: empty directory children for " + uri);
            File toDelete = new File(uri.getPath());
            toDelete.delete();
            isDeleteOK = deleteFile(new File(uri.getPath()));
        }
        deleteDir(uri); // directory is not in MediaStore and java IO delete seems to work when folder is empty
        // delete nfoJpg corresponding folder too and avoid loops
        if (! uri.getPath().startsWith(FileUtilsQ.publicAppDirectory + "/nfoPoster")) {
            isDeleteOK = deleteFolder(prefixPublicNfoPosterUri(uri));
        }
        return isDeleteOK;
    }

    /**
     *
     * @param context
     * @throws DeleteFailException if database AND file delete fails
     */
    public Boolean deleteFileAndDatabase(Context context) throws DeleteFailException {
        Boolean isDeleteOK = null;
        if (checkIfShouldNotTouchFolder(mUri)) {
            // when some folders are protected
            throw new DeleteFailException();
        }
        deleteFromDatabase(mUri);
        boolean delete = false;
        try {
            // TODO: does not work on external usb storage on recent Android
            isDeleteOK = delete();
            delete = true;
        } catch (Exception e) {
            log.error("deleteFileAndDatabase: caught exception ",e);
        }
        if (!(delete || !exists())) {
            // in case delete fail because file has already been deleted by cr.delete
            throw new DeleteFailException();
        }
        return isDeleteOK;
    }

    @Override
    public boolean rename(String newName) {
        if (checkIfShouldNotTouchFolder(mUri)) {
            // when some folders are protected
            return false;
        }
        File newFile = new File(new File(mUri.getPath()).getParentFile(), newName);
        boolean success = new File(mUri.getPath()).renameTo(newFile);
        if (success) {
            deleteFromDatabase(new File(mUri.getPath()));
            scanFile(Uri.fromFile(newFile), newFile.isDirectory());
        }
        return success;
    }

    @Override
    public boolean move(Uri uri) {
        if (checkIfShouldNotTouchFolder(mUri)) {
            // when some folders are protected
            return false;
        }
        if (uri.getScheme().equals(mUri.getScheme())) {
           return new File(mUri.getPath()).renameTo(new File(uri.getPath()));
        }
        return false;
    }

    @Override
    public boolean exists() {
        String path = mUri.getPath();
        return path != null && new File(path).exists();
    }

}
