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

import com.archos.filecorelibrary.contentstorage.ContentFile2;
import com.archos.filecorelibrary.ftp.FTPFile2;
import com.archos.filecorelibrary.jcifs.JcifsFile2;
import com.archos.filecorelibrary.localstorage.JavaFile2;
import com.archos.filecorelibrary.sftp.SFTPFile2;

/**
 * Created by alexandre on 22/04/15.
 */
public class MetaFile2Factory {
    public static MetaFile2 getMetaFileForUrl(Uri uri) throws Exception {


        if ("smb".equalsIgnoreCase(uri.getScheme())) {
            return JcifsFile2.fromUri(uri);
        }
        else if ("ftp".equalsIgnoreCase(uri.getScheme())||"ftps".equalsIgnoreCase(uri.getScheme())) {
            return FTPFile2.fromUri(uri);
        }
        else if ("sftp".equalsIgnoreCase(uri.getScheme())) {
            return SFTPFile2.fromUri(uri);
        }
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return ContentFile2.fromUri(uri);
        }
        else if (Utils.isLocal(uri)) {
            return JavaFile2.fromUri(uri);
        }
        else {
            throw new IllegalArgumentException("not implemented yet for "+uri);
        }
    }
}
