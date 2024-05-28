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
import com.archos.filecorelibrary.AuthenticationException;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebdavRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(WebdavRawLister.class);

    public WebdavRawLister(Uri uri) {
        super(uri);
    }

    @Override
    public ArrayList<MetaFile2> getFileList() throws IOException, AuthenticationException {
        try {
            OkHttpSardine sardine = WebdavUtils.peekInstance().getSardine(mUri);
            Uri httpUri = WebdavFile2.uriToHttp(mUri);

            ArrayList<MetaFile2> files = new ArrayList<MetaFile2>();
            List<DavResource> resources = sardine.list(httpUri.toString());

            // First answer is ourselves, ignore it
            resources.remove(0);
            for (DavResource res : resources) {
                files.add(new WebdavFile2(res, mUri.buildUpon().appendEncodedPath(res.getName()).build()));
            }
            return files;
        } catch (Throwable t) {
            log.warn("Failed listing webdav files uri=" + mUri, t);
            if(t.getMessage() != null && t.getMessage().contains("401 Un")) throw new AuthenticationException();
        }
        return null;
    }

}
