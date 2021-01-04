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

package com.archos.filecorelibrary.ftp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.archos.filecorelibrary.FileEditor;
import android.net.Uri;

public class FtpFileEditor extends FileEditor {
    private static final Logger log = LoggerFactory.getLogger(FtpFileEditor.class);

    public FtpFileEditor(Uri uri) {
        super(uri);
        // TODO Auto-generated constructor stub
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            if (mUri.getScheme().equals("ftps")) {
                FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
                Boolean isOk = ftp.makeDirectory(mUri.getPath());
                Session.closeNewFTPSClient(ftp);
                return isOk;
            } else {
                FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
                Boolean isOk = ftp.makeDirectory(mUri.getPath());
                Session.closeNewFTPClient(ftp);
                return isOk;
            }
        } catch (AuthenticationException e) {
            // TODO Auto-generated catch block
            log.error("Caught AuthenticationException: ",e);
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            log.error("Caught SocketException: ",e);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            log.error("Caught IOException: ",e);
        }
        return false;
    }

    @Override
    public InputStream getInputStream() throws AuthenticationException, SocketException, IOException {
        // TODO: missing way to close ftpClient, this creates leaks
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri,FTP.BINARY_FILE_TYPE );
            return ftp.retrieveFileStream(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
            return ftp.retrieveFileStream(mUri.getPath());
        }
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        // TODO: missing way to close ftpClient, this creates leaks
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
            ftp.setRestartOffset(from); // will refuse in ascii mode
            return ftp.retrieveFileStream(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
            ftp.setRestartOffset(from); // will refuse in ascii mode
            return ftp.retrieveFileStream(mUri.getPath());
        }
    }

    @Override
    public OutputStream getOutputStream() throws AuthenticationException, SocketException, IOException {
        // TODO: missing way to close ftpClient, this creates leaks
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
            return ftp.storeFileStream(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
            return ftp.storeFileStream(mUri.getPath());
        }
    }

    @Override
    public void delete() throws SocketException, IOException, AuthenticationException {
        // TODO: missing way to close ftpClient, this creates leaks
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
            ftp.deleteFile(mUri.getPath());
            Session.closeNewFTPSClient(ftp);
        } else {
            FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
            ftp.deleteFile(mUri.getPath());
            Session.closeNewFTPClient(ftp);
        }
    }

    @Override
    public boolean rename(String newName) {
        try {
            if (mUri.getScheme().equals("ftps")) {
                FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
                ftp.rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
                Session.closeNewFTPSClient(ftp);
            } else {
                FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
                ftp.rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
                Session.closeNewFTPClient(ftp);
            }
            return true;
        } catch (Exception e) {
            log.error("Caught Exception: ",e);
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        try {
            if (mUri.getScheme().equals("ftps")) {
                FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
                ftp.rename(mUri.getPath(), uri.getPath());
                Session.closeNewFTPSClient(ftp);
            } else {
                FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
                ftp.rename(mUri.getPath(), uri.getPath());
                Session.closeNewFTPClient(ftp);
            }
            return true;
        } catch (Exception e) {
            log.error("Caught Exception: ",e);
        }
        return false;
    }

    @Override
    public boolean exists() {
        try {
            if (mUri.getScheme().equals("ftps")) {
                FTPSClient ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
                FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
                Boolean isOK = ftpFile != null;
                Session.closeNewFTPSClient(ftp);
                return isOK;
            } else {
                FTPClient ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
                FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
                Boolean isOK = ftpFile != null;
                Session.closeNewFTPClient(ftp);
                return isOK;            }
        } catch (Exception e) {
            log.error("Caught Exception: ",e);
        }
        return false;
    }

}
