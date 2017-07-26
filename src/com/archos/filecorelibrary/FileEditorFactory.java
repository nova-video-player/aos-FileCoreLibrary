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

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.contentstorage.ContentStorageFileEditor;
import com.archos.filecorelibrary.ftp.FtpFileEditor;
import com.archos.filecorelibrary.jcifs.JcifsFileEditor;
import com.archos.filecorelibrary.localstorage.LocalStorageFileEditor;
import com.archos.filecorelibrary.sftp.SftpFileEditor;
import com.archos.filecorelibrary.zip.ZipFileEditor;

/**
 * create a file editor
 * @author alexandre
 *
 */
public class FileEditorFactory {
    public static FileEditor getFileEditorForUrl(Uri uri, Context ct) {
        if ("smb".equalsIgnoreCase(uri.getScheme())) {
            return new JcifsFileEditor(uri);
        }
        else if ("ftp".equalsIgnoreCase(uri.getScheme())||"ftps".equalsIgnoreCase(uri.getScheme())) {
            return new FtpFileEditor(uri);
        }
        else if ("sftp".equalsIgnoreCase(uri.getScheme())) {
            return new SftpFileEditor(uri);
        }
        else if ("zip".equalsIgnoreCase(uri.getScheme())) {
            return new ZipFileEditor(uri);
        }
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return new ContentStorageFileEditor(uri, ct);
        }
        else if (Utils.isLocal(uri)) {
            return new LocalStorageFileEditor(uri, ct);
        }
        else {
            throw new IllegalArgumentException("not implemented yet for "+uri);
        }
    }
}
