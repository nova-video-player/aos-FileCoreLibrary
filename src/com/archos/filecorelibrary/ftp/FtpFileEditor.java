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
                FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
                return ftp.makeDirectory(mUri.getPath());
            } else {
                FTPClient ftp = Session.getInstance().getFTPClient(mUri);
                return ftp.makeDirectory(mUri.getPath());
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
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
            return ftp.retrieveFileStream(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getFTPClient(mUri);
            return ftp.retrieveFileStream(mUri.getPath());
        }
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
            ftp.setRestartOffset(from); // will refuse in ascii mode
            return ftp.retrieveFileStream(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getFTPClient(mUri);
            ftp.setRestartOffset(from); // will refuse in ascii mode
            return ftp.retrieveFileStream(mUri.getPath());
        }
    }

    @Override
    public OutputStream getOutputStream() throws AuthenticationException, SocketException, IOException {
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
            return ftp.storeFileStream(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getFTPClient(mUri);
            return ftp.storeFileStream(mUri.getPath());
        }
    }

    @Override
    public void delete() throws SocketException, IOException, AuthenticationException {
        if (mUri.getScheme().equals("ftps")) {
            FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
            ftp.deleteFile(mUri.getPath());
        } else {
            FTPClient ftp = Session.getInstance().getFTPClient(mUri);
            ftp.deleteFile(mUri.getPath());
        }
    }

    @Override
    public boolean rename(String newName) {
        try {
            if (mUri.getScheme().equals("ftps")) {
                FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
                ftp.rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
            } else {
                FTPClient ftp = Session.getInstance().getFTPClient(mUri);
                ftp.rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
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
                FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
                ftp.rename(mUri.getPath(), uri.getPath());
            } else {
                FTPClient ftp = Session.getInstance().getFTPClient(mUri);
                ftp.rename(mUri.getPath(), uri.getPath());
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
                FTPSClient ftp = Session.getInstance().getFTPSClient(mUri);
                FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
                return ftpFile != null;
            } else {
                FTPClient ftp = Session.getInstance().getFTPClient(mUri);
                FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
                return ftpFile != null;
            }
        } catch (Exception e) {
            log.error("Caught Exception: ",e);
        }
        return false;
    }

}
