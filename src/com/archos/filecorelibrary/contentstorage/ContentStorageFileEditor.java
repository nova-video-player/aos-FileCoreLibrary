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
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.v4.provider.DocumentFile;
import android.support.v4.util.Pair;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MimeUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.util.Arrays;


public class ContentStorageFileEditor extends FileEditor {
    private final Context mContext;

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

    public ContentStorageFileEditor(Uri uri, Context context) {
        super(uri);

        if (context == null) {
            mContext = ArchosUtils.getGlobalContext();
        } else {
            mContext = context;
        }
    }

    public InputStream getInputStream() throws FileNotFoundException {
        return mContext.getContentResolver().openInputStream(mUri);
    }

    // Our FileEditor API throw an exception when the delete fails.
    // Since the java.File API does not do it (returns false instead) we're using this home-made exception instead
    public static class DeleteFailException extends Exception {}

    public static boolean checkIfShouldNotTouchFolder(Uri uri){
        String path  = uri.getPath();
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
        ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(mUri, "r");

        FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
        FileChannel fileChannel = fis.getChannel();
        fileChannel.position(from);
        return fis;
    }

    public long getSize() throws FileNotFoundException {
        Cursor returnCursor = mContext.getContentResolver().query(mUri, null, null, null, null);
        if(returnCursor.getCount() == 0)
            return 0;
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        return returnCursor.getLong(sizeIndex);
    }

    public OutputStream getOutputStream() throws IOException {
        touchFile();
        try {
            OutputStream stream = mContext.getContentResolver().openOutputStream(mUri, "rw");
            Log.d("copydebug", "getOutputStream "+mUri);

            return  stream;
        }catch (IOException e){
        }
return null;
    }

    @Override
    public boolean touchFile() {
        Pair<DocumentFile, String> pair = null;
        try {
            pair = DocumentUriBuilder.getParentDocumentFileAndFileName(mUri);
            if(pair==null)
                return false;
            pair.first.createFile(MimeUtils.guessMimeTypeFromExtension(MimeUtils.getExtension(pair.second)),pair.second);
            return true;
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
        return false;
    }

    @Override
    public boolean mkdir() {
        Pair<DocumentFile, String> pair = null;
        try {
            pair = DocumentUriBuilder.getParentDocumentFileAndFileName(mUri);
            pair.first.createDirectory(pair.second);
            return true;
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
        return false;
    }

    @Override
    public void delete() throws Exception {
        DocumentFile file = DocumentUriBuilder.getDocumentFileForUri(mUri);
        if(!file.delete())
            throw new DeleteFailException();
    }

    /**
     *
     * @param context
     * @throws DeleteFailException if database AND file delete fails
     */
    public void deleteFileAndDatabase(Context context) throws DeleteFailException {
        throw new DeleteFailException();
    }

    @Override
    public boolean rename(String newName) {
        DocumentFile file = null;
        try {
            file = DocumentUriBuilder.getDocumentFileForUri(mUri);
            return  file.renameTo(newName);
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
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        return false;
    }

    @Override
    public boolean exists() {
        return true;
    }

}
