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

import com.archos.filecorelibrary.FileEditor;
import android.net.Uri;

public class FtpFileEditor extends FileEditor {
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
        FTPClient ftp = null;
        try {
            if (mUri.getScheme().equals("ftps"))
                ftp = Session.getInstance().getNewFTPSClient(mUri, -1);
            else
                ftp = Session.getInstance().getNewFTPClient(mUri, -1);
            return ftp.makeDirectory(mUri.getPath());
        } catch (AuthenticationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public InputStream getInputStream() throws AuthenticationException, SocketException, IOException {
        FTPClient ftp = null;
        if (mUri.getScheme().equals("ftps"))
            ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
        else
            ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
        InputStream is = ftp.retrieveFileStream(mUri.getPath());
        return is;

    }

    @Override
    public InputStream getInputStream(long from) throws Exception {

        FTPClient ftp;
        if (mUri.getScheme().equals("ftps"))
            ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
        else
            ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
        ftp.setRestartOffset(from); // will refuse in ascii mode
        InputStream is = ftp.retrieveFileStream(mUri.getPath());
        return is;
    }

    @Override
    public OutputStream getOutputStream() throws AuthenticationException, SocketException, IOException {
        FTPClient ftp = null;
        if (mUri.getScheme().equals("ftps"))
            ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
        else
            ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
        return ftp.storeFileStream(mUri.getPath());
    }

    @Override
    public void delete() throws SocketException, IOException, AuthenticationException {
        FTPClient ftp = null;
        if (mUri.getScheme().equals("ftps"))
            ftp = Session.getInstance().getNewFTPSClient(mUri, -1);
        else
            ftp = Session.getInstance().getNewFTPClient(mUri, -1);
        ftp.deleteFile(mUri.getPath());
    }

    @Override
    public boolean rename(String newName) {
        try {
            FTPClient ftp = null;
            if (mUri.getScheme().equals("ftps"))
                ftp = Session.getInstance().getFTPSClient(mUri);
            else
                ftp = Session.getInstance().getFTPClient(mUri);
            ftp.rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        try {
            FTPClient ftp = null;
            if (mUri.getScheme().equals("ftps"))
                ftp = Session.getInstance().getFTPSClient(mUri);
            else
                ftp = Session.getInstance().getFTPClient(mUri);
            ftp.rename(mUri.getPath(), uri.getPath());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean exists() {
        try {
            FTPClient ftp = null;
            if (mUri.getScheme().equals("ftps"))
                ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
            else
                ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
            FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
            return ftpFile != null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
