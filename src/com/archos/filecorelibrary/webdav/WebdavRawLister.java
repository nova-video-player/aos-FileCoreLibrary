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

import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.AuthenticationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;

public class WebdavRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(WebdavRawLister.class);

    public WebdavRawLister(Uri uri) {
        super(uri);
    }

    @Override
    public ArrayList<MetaFile2> getFileList() throws IOException, AuthenticationException {
        try {
            var sardine = WebdavUtils.peekInstance().getSardine(mUri);
            var httpUri = WebdavFile2.uriToHttp(mUri);

            var files = new ArrayList<MetaFile2>();
            var resources = WebdavUtils.listResources(sardine, httpUri.toString(), 1);
            var normalizedDirectoryPath = WebdavFile2.normalizeWebdavPath(httpUri.getPath());

            for (var res : resources) {
                if (WebdavFile2.isSelfResource(res, normalizedDirectoryPath)) {
                    continue;
                }
                files.add(new WebdavFile2(res, FileUtils.buildChildUri(mUri, res.getName())));
            }
            return files;
        } catch (com.thegrizzlylabs.sardineandroid.impl.SardineException e) {
            log.warn("Failed listing webdav files uri={}", mUri, e);
            if (e.getStatusCode() == 401) {
                throw new AuthenticationException();
            }
            throw e;
        } catch (IOException e) {
            log.warn("Failed listing webdav files uri={}", mUri, e);
            if (e.getMessage() != null && e.getMessage().contains("401")) {
                throw new AuthenticationException();
            }
            throw e;
        }
    }

}
