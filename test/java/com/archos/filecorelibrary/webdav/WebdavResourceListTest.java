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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.thegrizzlylabs.sardineandroid.DavResource;

import org.junit.Test;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

public class WebdavResourceListTest {

    private static final String COLLECTION = "/media/Movie/";
    private static final String FILE = "/media/Movie/Movie.mkv";

    @Test
    public void compliantResponseRemovesSelfAndKeepsChild() throws Exception {
        var self = resource(COLLECTION);
        var child = resource(FILE);

        var children = WebdavResourceList.children(COLLECTION, List.of(self, child));

        assertEquals(1, children.size());
        assertSame(child, children.get(0));
    }

    @Test
    public void responseOrderDoesNotMatter() throws Exception {
        var self = resource(COLLECTION);
        var firstChild = resource(FILE);
        var secondChild = resource("/media/Movie/subtitles.srt");

        var children = WebdavResourceList.children(
                COLLECTION, List.of(firstChild, self, secondChild));

        assertEquals(List.of(firstChild, secondChild), children);
        assertSame(self, WebdavResourceList.find(COLLECTION,
                List.of(firstChild, self, secondChild)));
    }

    @Test
    public void torBoxResponseWithoutSelfKeepsItsOnlyChild() throws Exception {
        var child = resource(FILE);

        var children = WebdavResourceList.children(COLLECTION, List.of(child));

        assertEquals(1, children.size());
        assertSame(child, children.get(0));
    }

    @Test
    public void emptyCollectionDoesNotFail() {
        assertEquals(Collections.emptyList(),
                WebdavResourceList.children(COLLECTION, Collections.emptyList()));
    }

    @Test
    public void trailingSlashDoesNotChangeSelfIdentity() throws Exception {
        var selfWithoutTrailingSlash = resource("/media/Movie");
        var child = resource(FILE);

        var children = WebdavResourceList.children(
                COLLECTION, List.of(selfWithoutTrailingSlash, child));

        assertEquals(List.of(child), children);
    }

    @Test
    public void depthZeroAcceptsSingleCanonicalHrefForRequestedAlias() throws Exception {
        var canonicalResource = resource(FILE);

        var selected = WebdavResourceList.forDepthZero(
                "/Movie.mkv", List.of(canonicalResource));

        assertSame(canonicalResource, selected);
    }

    @Test
    public void depthZeroPrefersExactHrefIfServerReturnsExtraResources() throws Exception {
        var canonicalResource = resource(FILE);
        var exactResource = resource("/Movie.mkv");

        var selected = WebdavResourceList.forDepthZero(
                "/Movie.mkv", List.of(canonicalResource, exactResource));

        assertSame(exactResource, selected);
    }

    @Test
    public void depthZeroRejectsAmbiguousResponseWithoutExactHref() throws Exception {
        var first = resource(FILE);
        var second = resource("/media/Other/Other.mkv");

        assertNull(WebdavResourceList.forDepthZero(
                "/Movie.mkv", List.of(first, second)));
    }

    @Test
    public void depthZeroRejectsEmptyResponse() {
        assertNull(WebdavResourceList.forDepthZero(
                "/Movie.mkv", Collections.emptyList()));
    }

    private static DavResource resource(String href) throws URISyntaxException {
        return new TestDavResource(href);
    }

    private static final class TestDavResource extends DavResource {
        private TestDavResource(String href) throws URISyntaxException {
            super(href, null, null, null, null, null, null,
                    Collections.emptyList(), null, Collections.emptyList(), Collections.emptyMap());
        }
    }
}
