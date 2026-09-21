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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.net.Uri;

import com.archos.filecorelibrary.AuthenticationException;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

/**
 * returns
 * @author alexandre
 *
 */
public class FTPRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(FTPRawLister.class);

    public FTPRawLister(Uri uri) {
        super(uri);
    }

    @Override
    public List<MetaFile2> getFileList() throws IOException, AuthenticationException {
        if (log.isDebugEnabled()) log.debug("getFileList: listing {}", mUri);
        FTPFile[] listFiles = null;
        FTPClient ftp = null;
        try {
            if ("ftps".equals(mUri.getScheme())) {
                ftp = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
            } else {
                ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
            }
            if (ftp != null) {
                ftp.cwd(mUri.getPath());
                listFiles = ftp.listFiles();
            }
        } finally {
            Session.close(ftp);
        }

        if (listFiles == null)
            return null;
        ArrayList<MetaFile2> list = new ArrayList<MetaFile2>();
        for (FTPFile f : listFiles) {
            if (f.getName().equals(".") || f.getName().equals(".."))
                continue;
            FTPFile2 sf = new FTPFile2(f, FileUtils.buildChildUri(mUri, f.getName()));
            if (log.isTraceEnabled()) log.trace("FTPRawLister: add {}", sf.getName());
            list.add(sf);
        }
        return list;
    }
}
