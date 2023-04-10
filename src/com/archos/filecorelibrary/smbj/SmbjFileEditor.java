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

import static com.archos.filecorelibrary.FileUtils.getFilePath;
import static com.archos.filecorelibrary.FileUtils.getParentDirectoryPath;

import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.EnumSet;

public class SmbjFileEditor extends FileEditor {

    private static final Logger log = LoggerFactory.getLogger(SmbjFileEditor.class);

    private DiskShare mDiskShare;
    private String mFilePath;

    public SmbjFileEditor(Uri uri) {
        super(uri);
        mDiskShare = SmbjUtils.peekInstance().getSmbShare(uri);
        mFilePath = getFilePath(uri);
    }

    @Override
    public InputStream getInputStream() throws Exception {
        File smbjFile = mDiskShare.openFile(mFilePath, EnumSet.of(AccessMask.FILE_READ_DATA), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
        return smbjFile.getInputStream();
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        File smbjFile = mDiskShare.openFile(mFilePath, EnumSet.of(AccessMask.FILE_READ_DATA), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
        InputStream is = smbjFile.getInputStream();
        is.skip(from);
        return is;
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            mDiskShare.mkdir(mFilePath);
            return true;
        } catch (SMBApiException e) {
            caughtException(e, "mkdir", "SMBApiException in mkdir " + mUri);
        }
        return false;
    }

    @Override
    public Boolean delete() throws Exception {
        if(mDiskShare.folderExists(mFilePath)) mDiskShare.rm(mFilePath);
        else mDiskShare.rm(mFilePath);
        return null;
    }

    @Override
    public boolean move(Uri uri) { return false;}

    @Override
    public boolean rename(String newName) {
        File from = mDiskShare.openFile(mFilePath, EnumSet.of(AccessMask.GENERIC_ALL), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null);
        if (from != null) {
            from.rename(getParentDirectoryPath(mFilePath) + "/" + newName);
            return true;
        }
        return false;
    }

    @Override
    public boolean exists() {
        boolean exists = false;
        try {
            exists = mDiskShare.fileExists(mFilePath);
            log.trace("exists: " + mFilePath + " as file " + exists);
            return exists;
        } catch (SMBApiException e) {
            try {
                exists = mDiskShare.folderExists(mFilePath);
                log.trace("exists: " + mFilePath + " as folder " + exists);
                return exists;
            } catch (SMBApiException e1) {
                log.trace("exists: " + mFilePath + " is not a file nor a folder");
                return false;
            }
        }
    }
}
