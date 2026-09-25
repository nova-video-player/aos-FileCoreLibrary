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

package com.archos.filecorelibrary.sshj;

import static com.archos.filecorelibrary.FileUtils.caughtException;
import static com.archos.filecorelibrary.FileUtils.getParentDirectoryPath;
import static com.archos.filecorelibrary.sshj.SshjUtils.getSftpPath;

import android.net.Uri;

import com.archos.filecorelibrary.AuthenticationException;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.OwnedStreams;
import com.archos.filecorelibrary.ReadOptions;

import net.schmizz.sshj.common.SSHException;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.EnumSet;

public class SshjFileEditor extends FileEditor {

    private static final Logger log = LoggerFactory.getLogger(SshjFileEditor.class);

    public SshjFileEditor(Uri uri) { super(uri);}

    @Override
    public InputStream getInputStream() throws Exception {
        return getInputStream(0);
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        return getInputStream(from, ReadOptions.DEFAULT);
    }

    @Override
    public InputStream getInputStream(ReadOptions options) throws Exception {
        return getInputStream(0, options);
    }

    @Override
    public InputStream getInputStream(long from, ReadOptions options) throws Exception {
        if (from < 0) throw new IllegalArgumentException("Negative file offset");
        RemoteFile file = SshjUtils.peekInstance().getSFTPClient(mUri).open(getSftpPath(mUri));
        try {
            InputStream stream = options.conservative()
                    ? file.new RemoteFileInputStream(from)
                    : file.new ReadAheadRemoteFileInputStream(options.pipelineDepth(), from);
            return options.wrap(OwnedStreams.input(stream, file));
        } catch (Throwable failure) {
            try { file.close(); } catch (Throwable closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    @Override
    public OutputStream getOutputStream() throws Exception {
        RemoteFile file = SshjUtils.peekInstance().getSFTPClient(mUri).open(getSftpPath(mUri),
                EnumSet.of(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC));
        try {
            return OwnedStreams.output(file.new RemoteFileOutputStream(), file);
        } catch (Throwable failure) {
            try { file.close(); } catch (Throwable closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            SshjUtils.peekInstance().getSFTPClient(mUri).mkdir(getSftpPath(mUri));
            return true;
        } catch (IOException e) {
            caughtException(e, "SshjFileEditor:mkdir", "IOException" + mUri);
            if (e instanceof SSHException) {
                SshjUtils.closeSFTPClient(mUri);
                SshjUtils.disconnectSshClient(mUri);
            }
        } catch (AuthenticationException e) {
            caughtException(e, "SshjFileEditor:mkdir", "AuthenticationException" + mUri);
            SshjUtils.closeSFTPClient(mUri);
            SshjUtils.disconnectSshClient(mUri);
        }
        return false;
    }

    @Override
    public Boolean delete() throws Exception {
        final SFTPClient sftpClient = SshjUtils.peekInstance().getSFTPClient(mUri);
        final String mFilePath = getSftpPath(mUri);
        final FileAttributes fileAttributes = sftpClient.lstat(mFilePath);
        if (fileAttributes == null) return null;
        final FileMode.Type type = fileAttributes.getType();
        if (type == FileMode.Type.REGULAR || type == FileMode.Type.SYMLINK) {
            sftpClient.rm(mFilePath);
            return true;
        } else if (type == FileMode.Type.DIRECTORY) {
            sftpClient.rmdir(mFilePath);
            return true;
        }
        return null;
    }

    @Override
    public boolean move(Uri uri) { return false;}

    @Override
    public boolean rename(String newName) {
        try {
            final String mFilePath = getSftpPath(mUri);
            final SFTPClient sftpClient = SshjUtils.peekInstance().getSFTPClient(mUri);
            sftpClient.rename(getSftpPath(mUri), getParentDirectoryPath(mFilePath) + newName);
            return true;
        } catch (IOException e) {
            caughtException(e, "SshjFileEditor:rename", "IOException" + mUri);
            if (e instanceof SSHException) {
                SshjUtils.closeSFTPClient(mUri);
                SshjUtils.disconnectSshClient(mUri);
            }
        } catch (AuthenticationException e) {
            caughtException(e, "SshjFileEditor:rename", "AuthenticationException" + mUri);
            SshjUtils.closeSFTPClient(mUri);
            SshjUtils.disconnectSshClient(mUri);
        }
        return false;
    }

    @Override
    public boolean exists() {
        SFTPClient sftpClient;
        try {
            sftpClient = SshjUtils.peekInstance().getSFTPClient(mUri);
            final String mFilePath = getSftpPath(mUri);
            final FileAttributes fileAttributes = sftpClient.statExistence(mFilePath);
            if (fileAttributes == null) return false;
            final FileMode.Type type = fileAttributes.getType();
            if (type == FileMode.Type.REGULAR || type == FileMode.Type.SYMLINK || type == FileMode.Type.DIRECTORY)
                return true;
        } catch (IOException e) {
            caughtException(e, "SshjFileEditor:exists", "IOException" + mUri);
            if (e instanceof SSHException) {
                SshjUtils.closeSFTPClient(mUri);
                SshjUtils.disconnectSshClient(mUri);
            }
        } catch (AuthenticationException e) {
            caughtException(e, "SshjFileEditor:exists", "AuthenticationException" + mUri);
            SshjUtils.closeSFTPClient(mUri);
            SshjUtils.disconnectSshClient(mUri);
        }
        return false;
    }
}
