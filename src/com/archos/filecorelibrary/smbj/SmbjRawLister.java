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
import static com.archos.filecorelibrary.FileUtils.getShareName;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.AuthenticationException;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMBApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SmbjRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(SmbjRawLister.class);

    public SmbjRawLister(Uri uri) {
        super(uri);
    }

    @Override
    public ArrayList<MetaFile2> getFileList() throws IOException, AuthenticationException {
        try {
            var files = new ArrayList<MetaFile2>();
            var diskShare = SmbjUtils.peekInstance().getSmbShare(mUri);
            final String filePath = getFilePath(mUri);
            final String shareName = getShareName(mUri);
            List<FileIdBothDirectoryInformation> diskShareLst = diskShare.list(filePath);
            for (var fileOrDir : diskShareLst) {
                final String filename = fileOrDir.getFileName();
                final String fullFilename = "/" + shareName + "/" + filename;
                log.trace("getFileList: adding " + fullFilename);
                files.add(new SmbjFile2(fileOrDir, mUri.buildUpon().appendEncodedPath(filename).build()));
            }
            return files;
        } catch (SMBApiException se) { // most likely an Authentication error
            throw new AuthenticationException();
        } catch (Throwable t) {
            log.warn("Failed listing smbj files", t);
        }
        return null;
    }

}
