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

public class WebdavFile2 extends MetaFile2 {

    // see https://github.com/lookfirst/sardine/issues/359 to re-enable when issue fixed
    final static boolean CAN_SARDINE_CHECK_PERM = false;

    private static final Logger log = LoggerFactory.getLogger(WebdavFile2.class);

    public static Uri uriToHttp(Uri u) {
        Uri httpUri;
        if (u.getScheme().equals("webdavs"))
            httpUri = u.buildUpon().scheme("https").build();
        else
            httpUri = u.buildUpon().scheme("http").build();
        
        try {
            WebdavUtils utils = WebdavUtils.peekInstance();
            if (utils == null) {
                log.warn("uriToHttp: WebdavUtils not initialized, using original URI");
                return httpUri;
            }
            String resolvedUrl = utils.resolveRedirect(httpUri);
            if (resolvedUrl == null) {
                log.warn("uriToHttp: redirect resolution failed for " + httpUri + ", using original");
                return httpUri;
            }
            String path = httpUri.getEncodedPath();
            if (path == null) path = "";
            if (!path.isEmpty() && !path.startsWith("/")) {
                path = "/" + path;
            }

            Uri resolvedUri = Uri.parse(resolvedUrl);
            String resolvedPath = resolvedUri.getEncodedPath();
            if (resolvedPath != null && !resolvedPath.isEmpty() && !"/".equals(resolvedPath)) {
                if (resolvedPath.endsWith("/")) {
                    resolvedPath = resolvedPath.substring(0, resolvedPath.length() - 1);
                }
                if (path.equals(resolvedPath) || path.startsWith(resolvedPath + "/")) {
                    path = path.substring(resolvedPath.length());
                }
            }

            return Uri.parse(resolvedUrl + path);
        } catch (Exception e) {
            log.warn("uriToHttp: redirect resolution error for " + httpUri + ", using original", e);
            return httpUri;
        }
    }

    public static boolean isSelfResource(DavResource res, Uri directoryUri) {
        return isSelfResource(res, normalizeWebdavPath(directoryUri.getPath()));
    }

    /**
     * Normalizes a WebDAV resource path for equality comparisons. Package-private so listing
     * callers can perform this once per directory rather than once per response entry.
     */
    static String normalizeWebdavPath(String path) {
        if (path == null) return null;
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return end == path.length() ? path : path.substring(0, end);
    }

    /**
     * Compares a resource against an already-normalized directory path.
     */
    static boolean isSelfResource(DavResource res, String normalizedDirectoryPath) {
        if (res == null || normalizedDirectoryPath == null) return false;
        String normalizedResourcePath = normalizeWebdavPath(res.getPath());
        return normalizedDirectoryPath.equals(normalizedResourcePath);
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

    static DavResource selectResourceForDepthZero(List<DavResource> resources, Uri httpUri) {
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        String normalizedRequestedPath = normalizeWebdavPath(httpUri.getPath());
        for (DavResource res : resources) {
            if (isSelfResource(res, normalizedRequestedPath)) {
                return res;
            }
        }
        return resources.size() == 1 ? resources.get(0) : null;
    }

    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary)
     */
    public static MetaFile2 fromUri(Uri uri) throws Exception {
        var sardine = WebdavUtils.peekInstance().getSardine(uri);
        Uri httpUri = WebdavFile2.uriToHttp(uri);
        List<DavResource> resources = WebdavUtils.listResources(sardine, httpUri.toString(), 0);
        DavResource resource = selectResourceForDepthZero(resources, httpUri);
        if (resource == null) {
            throw new IOException("WebDAV server did not return the requested resource: " + httpUri);
        }
        return new WebdavFile2(resource, uri);

    }
}
