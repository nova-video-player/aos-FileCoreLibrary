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

package com.archos.filecorelibrary;

import static com.archos.filecorelibrary.smbj.SmbjUtils.isSMBjEnabled;
import static com.archos.filecorelibrary.sshj.SshjUtils.isSSHjEnabled;

import android.net.Uri;

import com.archos.filecorelibrary.contentstorage.ContentStorageRawLister;
import com.archos.filecorelibrary.ftp.FTPRawLister;
import com.archos.filecorelibrary.jcifs.JcifsFile2;
import com.archos.filecorelibrary.jcifs.JcifsRawLister;
import com.archos.filecorelibrary.localstorage.LocalStorageRawLister;
import com.archos.filecorelibrary.sftp.SFTPFile2;
import com.archos.filecorelibrary.sftp.SFTPRawLister;
import com.archos.filecorelibrary.smbj.SmbjFile2;
import com.archos.filecorelibrary.sshj.SshjFile2;
import com.archos.filecorelibrary.sshj.SshjRawLister;
import com.archos.filecorelibrary.webdav.WebdavRawLister;
import com.archos.filecorelibrary.smbj.SmbjRawLister;
import com.archos.filecorelibrary.zip.ZipRawLister;

public class RawListerFactory {

    public static RawLister getRawListerForUrl(Uri uri) {

        if ("smb".equals(uri.getScheme())) {
            if (isSMBjEnabled()) return new SmbjRawLister(uri);
            else return new JcifsRawLister(uri);
        }
        else if ("ftp".equals(uri.getScheme()) || "ftps".equals(uri.getScheme())) {
            return new FTPRawLister(uri) {
            };
        }
        else if ("sftp".equals(uri.getScheme())) {
            if (isSSHjEnabled()) return new SshjRawLister(uri);
            else return new SFTPRawLister(uri);
        }
        else if ("sshj".equals(uri.getScheme())) {
            return new SshjRawLister(uri);
        }
        else if ("zip".equals(uri.getScheme())) {
            return new ZipRawLister(uri);
        }
        else if ("content".equals(uri.getScheme())) {
            return new ContentStorageRawLister(uri);
        }
        else if (FileUtils.isLocal(uri)) {
            return new LocalStorageRawLister(uri);
        }
        else if("webdav".equals(uri.getScheme())) {
            return new WebdavRawLister(uri);
        }
        else if("webdavs".equals(uri.getScheme())) {
            return new WebdavRawLister(uri);
        }
        else if("smbj".equals(uri.getScheme())) {
            return new SmbjRawLister(uri);
        }
        else {
            throw new IllegalArgumentException("not implemented yet for "+uri);
        }
    }
}
