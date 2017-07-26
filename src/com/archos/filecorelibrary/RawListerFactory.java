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

import android.net.Uri;

import com.archos.filecorelibrary.contentstorage.ContentStorageRawLister;
import com.archos.filecorelibrary.ftp.FTPRawLister;
import com.archos.filecorelibrary.jcifs.JcifsRawLister;
import com.archos.filecorelibrary.localstorage.LocalStorageRawLister;
import com.archos.filecorelibrary.sftp.SFTPRawLister;
import com.archos.filecorelibrary.zip.ZipRawLister;

public class RawListerFactory {

    public static RawLister getRawListerForUrl(Uri uri) {

        if ("smb".equals(uri.getScheme())) {
            return new JcifsRawLister(uri);
        }
        else if ("ftp".equals(uri.getScheme()) || "ftps".equals(uri.getScheme())) {
            return new FTPRawLister(uri) {
            };
        }
        else if ("sftp".equals(uri.getScheme())) {
            return new SFTPRawLister(uri);
        }
        else if ("zip".equals(uri.getScheme())) {
            return new ZipRawLister(uri);
        }
        else if ("content".equals(uri.getScheme())) {
            return new ContentStorageRawLister(uri);
        }
        else if (Utils.isLocal(uri)) {
            return new LocalStorageRawLister(uri);
        }
        else {
            throw new IllegalArgumentException("not implemented yet for "+uri);
        }
    }
}
