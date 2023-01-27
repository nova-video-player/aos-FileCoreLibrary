// Copyright 2023 Pierre-Hugues HUSSON
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

package com.archos.filecorelibrary.webdav;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebdavRawLister extends RawLister {
    public WebdavRawLister(Uri uri) {
        super(uri);
    }

    @Override
    public ArrayList<MetaFile2> getFileList() throws IOException, AuthenticationException {
        try {
            var cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
            var sardine = new OkHttpSardine();
            sardine.setCredentials(cred.getUsername(), cred.getPassword());

            var httpUri = WebdavFile2.uriToHttp(mUri);

            var files = new ArrayList<MetaFile2>();
            var resources = sardine.list(httpUri.toString());

            // First answer is ourselves, ignore it
            resources.remove(0);
            for (var res : resources) {
                files.add(new WebdavFile2(res, mUri.buildUpon().appendEncodedPath(res.getName()).build()));
            }
            return files;
        } catch (Throwable t) {
            android.util.Log.d("PHH", "Failed listing webdav files", t);
        }
        return null;
    }

}
