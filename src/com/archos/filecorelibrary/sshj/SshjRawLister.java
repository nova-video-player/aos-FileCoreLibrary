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
import static com.archos.filecorelibrary.sshj.SshjUtils.getSftpPath;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.AuthenticationException;

import net.schmizz.sshj.common.SSHException;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SshjRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(SshjRawLister.class);

    public SshjRawLister(Uri uri) {
        super(uri);
        log.trace("SshjRawLister: " + mUri);
    }

    @Override
    public ArrayList<MetaFile2> getFileList() throws AuthenticationException, IOException {
        log.trace("getFileList: " + mUri);
        SFTPClient sftpClient = null;
        try {
            var files = new ArrayList<MetaFile2>();
            sftpClient = SshjUtils.peekInstance().getSFTPClient(mUri);
            final String filePath = getSftpPath(mUri);
            List<RemoteResourceInfo> remoteResourceInfos = sftpClient.ls(filePath);
            for (var fileOrDir : remoteResourceInfos) {
                final String filename = fileOrDir.getName();
                log.trace("getFileList: adding " + filename);
                files.add(new SshjFile2(fileOrDir, mUri.buildUpon().appendEncodedPath(filename).build()));
            }
            return files;
        } catch (IOException ioe) {
            caughtException(ioe, "SshjRawLister:getFileList", "IOException for " + mUri);
            if (ioe instanceof SSHException) {
                SshjUtils.closeSFTPClient(mUri);
                SshjUtils.disconnectSshClient(mUri);
            }
            throw ioe;
        } catch (AuthenticationException ae) {
            caughtException(ae, "SshjRawLister:getFileList", "AuthenticationException for " + mUri);
            //SshjUtils.closeSFTPClient(mUri);
            //SshjUtils.disconnectSshClient(mUri);
            throw ae;
        } catch (Throwable t) {
            caughtException(t, "SshjRawLister:getFileList", "Exception for " + mUri);
            //SshjUtils.closeSFTPClient(mUri);
            //SshjUtils.disconnectSshClient(mUri);
            throw t;
        }
    }

}
