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

import static com.archos.filecorelibrary.FileUtils.caughtException;

import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileUtils;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;
import com.thegrizzlylabs.sardineandroid.impl.SardineException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import okhttp3.Request;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

public class WebdavFileEditor extends FileEditor {

    private static final Logger log = LoggerFactory.getLogger(WebdavFileEditor.class);

    private OkHttpSardine mSardine;
    private OkHttpClient mHttpClient;
    private long mLength = -1;

    public WebdavFileEditor(Uri uri) {
        super(uri);
        mSardine = WebdavUtils.peekInstance().getSardine(uri);
        mHttpClient = WebdavUtils.peekInstance().getHttpClient(uri);
    }

    @Override
    public InputStream getInputStream() throws Exception {
        return getInputStream(-1);
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        var uri = WebdavFile2.uriToHttp(mUri);
        if (log.isTraceEnabled()) log.trace("getInputStream: requesting length for {}", uri);
        var reqBuilder = new Request.Builder()
            .url(uri.toString())
            .get();
        if (from >= 0) {
            var headers = new HashMap<String, String>();
            headers.put("Range", "bytes=" + from + "-");
            reqBuilder.headers(Headers.of(headers));
        }
        var req = reqBuilder.build();
        var resp = mHttpClient.newCall(req).execute();

        // For 206 Partial Content responses, extract total size from Content-Range
        // header (format: "bytes start-end/total") rather than Content-Length which
        // only reflects the partial response size
        var contentRange = resp.header("Content-Range");
        if (contentRange != null && contentRange.contains("/")) {
            var totalStr = contentRange.substring(contentRange.indexOf('/') + 1).trim();
            if (!"*".equals(totalStr)) {
                try {
                    mLength = Long.parseLong(totalStr);
                    if (log.isTraceEnabled()) log.trace("getInputStream: got total length {} from Content-Range: {}", mLength, contentRange);
                } catch (NumberFormatException e) {
                    log.warn("getInputStream: failed to parse Content-Range total: {}", contentRange);
                }
            }
        }

        // Fall back to Content-Length if Content-Range was not available
        if (mLength < 0) {
            var length = resp.header("Content-Length");
            if (length != null) {
                try {
                    mLength = Long.parseLong(length);
                    if (log.isTraceEnabled()) log.trace("getInputStream: got length {} from Content-Length", mLength);
                } catch (NumberFormatException e) {
                    log.warn("getInputStream: failed to parse Content-Length: {}", length);
                }
            }
        }

        return resp.body().byteStream();
    }

    @Override
    public long length() throws Exception {
        return mLength;
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
            caughtException(e, "WebdavFileEditor:mkdir", "IOException in mkdir " + mUri);
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
            caughtException(e, "WebdavFileEditor:rename", "IOException in move " + mUri + " into " + uri);
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
                    if (doesItExist) log.trace("exists: {} exists", mUri);
                    else if (log.isTraceEnabled()) log.trace("exists: {} does not exist", mUri);
                }
                return doesItExist;
            } else {
                log.warn("exists: uriToHttp returned null!");
            }
        } catch (IOException e) {
            caughtException(e, "WebdavFileEditor:exists", "IOException in exists for " + mUri);
        }
        return false;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                var u = WebdavFile2.uriToHttp(mUri);
                var fileContent = toByteArray();
                mSardine.put(u.toString(), fileContent);
            }
        };
    }
}
