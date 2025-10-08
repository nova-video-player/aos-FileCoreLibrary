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

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.thegrizzlylabs.sardineandroid.DavAce;
import com.thegrizzlylabs.sardineandroid.DavResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import okhttp3.Request;
import okhttp3.OkHttpClient;

public class WebdavFile2 extends MetaFile2 {

    // see https://github.com/lookfirst/sardine/issues/359 to re-enable when issue fixed
    final static boolean CAN_SARDINE_CHECK_PERM = false;

    private static final Logger log = LoggerFactory.getLogger(WebdavFile2.class);

    private static String sResolvedBaseUrl = null;
    private static String sOriginUrl = "http";

    public static Uri uriToHttp(Uri u) {
        // convert scheme
        Uri httpUri;
        if (u.getScheme().equals("webdavs"))
            httpUri = u.buildUpon().scheme("https").build();
        else
            httpUri = u.buildUpon().scheme("http").build();

        // If baseurl was updated, new 302 request
        String sBaseUrl = u.getScheme() + "://" + u.getHost();
        if (!sBaseUrl.equals(sOriginUrl)) {
            sOriginUrl = sBaseUrl;
            sResolvedBaseUrl = null;
        }

        // if already resolved, reuse cached base URL
        if (sResolvedBaseUrl != null) {
            return Uri.parse(sResolvedBaseUrl + httpUri.getPath());
        }

        // make HEAD request only to domain (ignore path)
        String baseUrl = httpUri.getScheme() + "://" + httpUri.getHost();
        OkHttpClient client = new OkHttpClient.Builder()
                .followRedirects(false) // do not auto-follow
                .build();

        Request req = new Request.Builder().url(baseUrl + "/").head().build();
        try {
            var resp = client.newCall(req).execute();
            if (resp.code() == 302 || resp.code() == 301) {
                String location = resp.header("Location");
                // extract scheme + host + port only
                Uri redirectUri = Uri.parse(location);
                String base = redirectUri.getScheme() + "://" + redirectUri.getHost();
                if (redirectUri.getPort() != -1) {
                    base += ":" + redirectUri.getPort();
                }
                sResolvedBaseUrl = base;
                log.info("WebDAV redirect resolved to: " + sResolvedBaseUrl);
                return Uri.parse(base + httpUri.getPath());
            }
            else {
                sResolvedBaseUrl = baseUrl;
                return httpUri;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve WebDAV redirect for " + u, e);
            return httpUri; // fallback
        }
    }

    private static final long serialVersionUID = 2L;

    private final String mName;
    private final boolean mIsDirectory;
    private final boolean mIsFile;
    private final long mLastModified;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final long mLength;
    private final String mUriString;

    public WebdavFile2(DavResource res, Uri uri) {
        mUriString = uri.toString();
        mName = FileUtils.getName(uri);
        mIsDirectory = res.isDirectory();
        mIsFile = ! mIsDirectory;
        if (res.getModified() != null) mLastModified = res.getModified().getTime();
        else mLastModified = 0;
        mCanRead = true;
        mCanWrite = true;
        if (CAN_SARDINE_CHECK_PERM) {
            final List<DavAce> aces;
            try {
                aces = WebdavUtils.peekInstance().getSardine(uri).getAcl(uriToHttp(uri).toString()).getAces();
                if (!aces.isEmpty()) {
                    mCanRead = (Objects.equals(aces.get(0).getGranted().get(0), "read"));
                    mCanWrite = (Objects.equals(aces.get(0).getGranted().get(1), "write"));
                }
            } catch (IOException ioe) {
                if (log.isTraceEnabled()) log.error("WebdavFile2: caught IOException", ioe);
                else log.error("WebdavFile2: caught IOException");
            }
        }
        mLength = res.getContentLength();
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isDirectory() {
        return mIsDirectory;
    }

    @Override
    public boolean isFile() {
        return mIsFile;
    }

    @Override
    public long lastModified() {
        return mLastModified;
    }

    @Override
    public long length() {
        return mLength;
    }

    @Override
    public boolean canRead() {
        return mCanRead;
    }

    @Override
    public boolean canWrite() {
        return mCanWrite;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof WebdavFile2) {
            return getUri().equals(((WebdavFile2) other).getUri());
        } else {
            return false;
        }
    }

    @Override
    public Uri getUri() {
        return Uri.parse(this.mUriString);
    }

    @Override
    public RawLister getRawListerInstance() {
        return new WebdavRawLister(getUri());
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new WebdavFileEditor(Uri.parse(mUriString));
    }

    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary)
     */
    public static MetaFile2 fromUri(Uri uri) throws Exception {
        var sardine = WebdavUtils.peekInstance().getSardine(uri);
        Uri httpUri = WebdavFile2.uriToHttp(uri);
        List<DavResource> resources = sardine.list(httpUri.toString());
        return new WebdavFile2(resources.get(0), uri);

    }
}