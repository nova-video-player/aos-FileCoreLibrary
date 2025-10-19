// Copyright 2023 Courville Software
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

package com.archos.filecorelibrary.smbj;

import static com.archos.filecorelibrary.FileUtils.caughtException;
import static com.archos.filecorelibrary.FileUtils.getFilePath;
import static com.archos.filecorelibrary.FileUtils.getParentDirectoryPath;

import android.net.Uri;

import com.archos.environment.ObservableInputStream;
import com.archos.environment.ObservableOutputStream;
import com.archos.filecorelibrary.FileEditor;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.concurrent.TimeoutException;

public class SmbjFileEditor extends FileEditor {

    private static final Logger log = LoggerFactory.getLogger(SmbjFileEditor.class);

    public SmbjFileEditor(Uri uri) { super(uri); }

    @Override
    public InputStream getInputStream() throws Exception {
        log.trace("getInputStream: opening " + mUri);
        File smbjFile = SmbjUtils.peekInstance().getSmbShare(mUri).openFile(getFilePath(mUri),
                EnumSet.of(AccessMask.FILE_READ_DATA),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_READONLY),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_RANDOM_ACCESS));
        InputStream is = smbjFile.getInputStream();
        ObservableInputStream ois = new ObservableInputStream(is);
        ois.onClose(() -> {if (smbjFile != null) {
            log.trace("getInputStream: closing " + mUri);
            // check that DiskShare has not already been closed (seen on sentry)
            if (smbjFile.getDiskShare().isConnected()) smbjFile.closeSilently();
        }});
        return ois;
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        log.trace("getInputStream: opening " + mUri);
        File smbjFile = SmbjUtils.peekInstance().getSmbShare(mUri).openFile(getFilePath(mUri),
                EnumSet.of(AccessMask.FILE_READ_DATA),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_READONLY),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_RANDOM_ACCESS));
        InputStream is = smbjFile.getInputStream();
        is.skip(from);
        ObservableInputStream ois = new ObservableInputStream(is);
        ois.onClose(() -> {if (smbjFile != null) {
            log.trace("getInputStream: closing " + mUri);
            // check that DiskShare has not already been closed (seen on sentry)
            if (smbjFile.getDiskShare().isConnected()) smbjFile.closeSilently();
        }});
        return ois;
    }

    @Override
    public OutputStream getOutputStream() throws Exception {
        log.trace("getOutputStream: opening " + mUri);
        File smbjFile =  SmbjUtils.peekInstance().getSmbShare(mUri).openFile(getFilePath(mUri),
                EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                null, SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null);
        OutputStream os = smbjFile.getOutputStream();
        ObservableOutputStream oos = new ObservableOutputStream(os);
        oos.onClose(() -> {if (smbjFile != null) {
            log.trace("getOutputStream: closing " + mUri);
            // check that DiskShare has not already been closed (seen on sentry)
            if (smbjFile.getDiskShare().isConnected()) smbjFile.closeSilently();
        }});
        return oos;
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            SmbjUtils.peekInstance().getSmbShare(mUri).mkdir(getFilePath(mUri));
            return true;
        } catch (IOException e) {
            caughtException(e, "SmbjFileEditor:mkdir", "IOException in mkdir " + mUri);
        }  catch (SMBApiException se) {
            caughtException(se, "SMBApiException:mkdir", "IOException in mkdir " + mUri);
        }
        return false;
    }

    @Override
    public Boolean delete() throws Exception {
        DiskShare mDiskShare = SmbjUtils.peekInstance().getSmbShare(mUri);
        String mFilePath = getFilePath(mUri);
        try {
            // Try to delete as file first (most common case)
            try {
                mDiskShare.rm(mFilePath);
                log.debug("delete: successfully deleted file " + mUri);
                return true;
            } catch (SMBApiException e) {
                NtStatus status = e.getStatus();
                // If it's not a file, try as a directory
                if (status == NtStatus.STATUS_FILE_IS_A_DIRECTORY) {
                    log.debug("delete: path is a directory, using rmdir " + mUri);
                    mDiskShare.rmdir(mFilePath, true);
                    log.debug("delete: successfully deleted directory " + mUri);
                    return true;
                } else if (status == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND) {
                    // File doesn't exist - this is not an error, just return true
                    log.debug("delete: file does not exist " + mUri);
                    return true;
                } else {
                    throw e;
                }
            } catch (Exception e) {
                // Handle smbj quirk: TransportException with EOFException during delete
                // The file is typically deleted on server despite the exception, but the
                // connection is compromised. Invalidate the cached share to force reconnection.
                if (isEofTransportException(e)) {
                    log.debug("delete: got EOF exception during rm/rmdir, invalidating share cache for " + mUri);
                    SmbjUtils.peekInstance().invalidateShare(mUri);
                    // File was deleted on server; return true to proceed with next operation
                    // which will get a fresh, connected share via getSmbShare()
                    return true;
                }
                throw e;
            }
        } catch (Exception e) {
            caughtException(e, "SmbjFileEditor:delete", "Exception in delete " + mUri);
            throw e;
        }
    }

    /**
     * Check if exception is a TransportException wrapping EOFException (smbj library quirk)
     */
    private boolean isEofTransportException(Exception e) {
        if (!(e instanceof TransportException)) {
            return false;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof java.io.EOFException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) { return false;}

    @Override
    public boolean rename(String newName) {
        String mFilePath = getFilePath(mUri);
        try {
            File from = SmbjUtils.peekInstance().getSmbShare(mUri).openFile(mFilePath,
                    EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_DELETE),
                    SMB2CreateDisposition.FILE_OPEN,
                    EnumSet.of(SMB2CreateOptions.FILE_RANDOM_ACCESS)
                    );

            log.debug("rename: mFilePath=" + mFilePath + " -> " + getParentDirectoryPath(mFilePath) + newName);
            if (from != null) {
                from.rename(getParentDirectoryPath(mFilePath) + newName);
                from.close();
                return true;
            }
        } catch (IOException e) {
            caughtException(e, "SmbjFileEditor:rename", "IOException in rename " + mUri + " into " + newName);
        } catch (SMBApiException se) {
            caughtException(se, "SmbjFileEditor:rename", "SMBApiException in rename " + mUri + " into " + newName);
        }
        return false;
    }

    @Override
    public boolean exists() {
        try {
            SmbjUtils smbjUtilsInstance = SmbjUtils.peekInstance();
            if (smbjUtilsInstance == null) {
                throw new IllegalStateException("SmbjUtils instance is null");
            }
            DiskShare mDiskShare = smbjUtilsInstance.getSmbShare(mUri);
            // at this stage, mDiskShare if not null should be connected i.e. .isConnected() should be true granted by getSmbShare
            if (mDiskShare == null || ! mDiskShare.isConnected()) {
                log.error("exists: mDiskShare is null or not connected for " + mUri + " returning false");
                return false;
            }
            String mFilePath = getFilePath(mUri);
            return mDiskShare.fileExists(mFilePath) || mDiskShare.folderExists(mFilePath);
        } catch (Exception e) { // can be IOException | SMBApiException but also TimeoutException claimed not to be thrown
            caughtException(e, "SmbjFileEditor:exists", "Exception in exists " + mUri);
        }
        return false;
    }
}
