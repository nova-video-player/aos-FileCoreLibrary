// Copyright 2026 Courville Software
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

import com.thegrizzlylabs.sardineandroid.DavResource;

import java.util.ArrayList;
import java.util.List;

/** Helpers for interpreting the flat resource list returned by a PROPFIND. */
final class WebdavResourceList {

    private WebdavResourceList() {
    }

    /**
     * Return the members of a collection from a Depth: 1 response.
     *
     * WebDAV response order is not significant. A compliant response normally contains the
     * requested collection as well as its members, while some servers omit the collection. Only
     * remove a resource whose href actually identifies the requested collection.
     */
    static List<DavResource> children(String requestedPath, List<DavResource> resources) {
        var children = new ArrayList<DavResource>(resources.size());
        for (var resource : resources) {
            if (!samePath(requestedPath, resource.getPath())) {
                children.add(resource);
            }
        }
        return children;
    }

    static DavResource find(String requestedPath, List<DavResource> resources) {
        for (var resource : resources) {
            if (samePath(requestedPath, resource.getPath())) {
                return resource;
            }
        }
        return null;
    }

    /**
     * Select the resource described by a Depth: 0 response.
     *
     * Depth: 0 applies only to the requested resource, so a response containing exactly one
     * resource describes that resource even when its href is a different (for example canonical)
     * URI. Prefer an exact href match when present, and reject ambiguous non-conforming responses.
     */
    static DavResource forDepthZero(String requestedPath, List<DavResource> resources) {
        DavResource exactMatch = find(requestedPath, resources);
        if (exactMatch != null) {
            return exactMatch;
        }
        return resources.size() == 1 ? resources.get(0) : null;
    }

    private static boolean samePath(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return normalizePath(first).equals(normalizePath(second));
    }

    private static String normalizePath(String path) {
        if (path.isEmpty()) {
            return "/";
        }
        int end = path.length();
        while (end > 1 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }
}
