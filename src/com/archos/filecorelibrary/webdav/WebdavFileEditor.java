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

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class WebdavFileEditor extends FileEditor {

    private static final Logger log = LoggerFactory.getLogger(WebdavFileEditor.class);

    private OkHttpSardine mSardine;

    public WebdavFileEditor(Uri uri) {
        super(uri);
        var cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
        mSardine = new OkHttpSardine();
        if (cred != null) {
            mSardine.setCredentials(cred.getUsername(), cred.getPassword());
        }
    }

    @Override
    public InputStream getInputStream() throws Exception {
        var u = WebdavFile2.uriToHttp(mUri);
        return mSardine.get(u.toString());
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        var u = WebdavFile2.uriToHttp(mUri);
        var headers = new HashMap<String, String>();
        headers.put("Range", "bytes=" + from + "-");
        return mSardine.get(u.toString(), headers);
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            var u = WebdavFile2.uriToHttp(mUri);
            mSardine.createDirectory(u.toString());
            return true;
        } catch (IOException e) {
            caughtException(e, "mkdir", "IOException in mkdir " + mUri);
        }
        return false;
    }

    @Override
    public Boolean delete() throws Exception {
        var u = WebdavFile2.uriToHttp(mUri);
        mSardine.delete(u.toString());
        return null;
    }

    @Override
    public boolean move(Uri uri) {
        var origin = WebdavFile2.uriToHttp(mUri);
        try {
            if (origin != null) {
                var destination = WebdavFile2.uriToHttp(uri);
                if (destination != null) {
                    mSardine.move(origin.toString(), destination.toString());
                    return true;
                }
            }
        } catch (IOException e) {
            caughtException(e, "rename", "IOException in move " + mUri + " into " + uri);
        }
        return false;
    }

    @Override
    public boolean rename(String newName) {
        return move(Uri.parse(FileUtils.getParentUrl(mUri.toString()) + "/" + newName));
    }

    @Override
    public boolean exists() {
        try {
            var u = WebdavFile2.uriToHttp(mUri);
            boolean doesItExist;
            if (u != null) {
                doesItExist = mSardine.exists(u.toString());
                if (log.isTraceEnabled()) {
                    if (doesItExist) log.trace("exists: " + mUri + " exists");
                    else log.trace("exists: " + mUri + " does not exist");
                }
                return doesItExist;
            } else {
                log.warn("exists: uriToHttp returned null!");
            }
        } catch (IOException e) {
            caughtException(e, "exists", "IOException in exists for " + mUri);
        }
        return false;
    }
}
