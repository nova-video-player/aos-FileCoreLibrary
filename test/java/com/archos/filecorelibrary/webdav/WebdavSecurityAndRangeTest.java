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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;
import com.thegrizzlylabs.sardineandroid.impl.SardineException;
import com.thegrizzlylabs.sardineandroid.model.Prop;
import com.thegrizzlylabs.sardineandroid.model.Propstat;
import com.thegrizzlylabs.sardineandroid.model.Resourcetype;
import com.thegrizzlylabs.sardineandroid.model.Response;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WebdavSecurityAndRangeTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        WebdavUtils.getInstance(mContext);
    }

    private DavResource createDavResource(String href, boolean isDirectory) throws Exception {
        Response response = new Response();
        java.lang.reflect.Field hrefField = Response.class.getDeclaredField("href");
        hrefField.setAccessible(true);
        hrefField.set(response, href);

        Propstat propstat = new Propstat();
        Prop prop = new Prop();
        if (isDirectory) {
            Resourcetype rt = new Resourcetype();
            java.lang.reflect.Field colField = Resourcetype.class.getDeclaredField("collection");
            colField.setAccessible(true);
            colField.set(rt, new com.thegrizzlylabs.sardineandroid.model.Collection());
            java.lang.reflect.Field rtField = Prop.class.getDeclaredField("resourcetype");
            rtField.setAccessible(true);
            rtField.set(prop, rt);
        }
        java.lang.reflect.Field propField = Propstat.class.getDeclaredField("prop");
        propField.setAccessible(true);
        propField.set(propstat, prop);

        java.lang.reflect.Field statusField = Propstat.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(propstat, "HTTP/1.1 200 OK");

        java.lang.reflect.Field psListField = Response.class.getDeclaredField("propstat");
        psListField.setAccessible(true);
        psListField.set(response, Collections.singletonList(propstat));

        return new DavResource(response);
    }

    @Test
    public void testSelfResourceCasingAndMatching() throws Exception {
        Uri dirUri = Uri.parse("webdav://example.com/DavFolder/");

        // Exact matching directory
        DavResource selfRes = createDavResource("/DavFolder/", true);
        assertTrue("Self resource with trailing slash should match", WebdavFile2.isSelfResource(selfRes, dirUri));

        DavResource selfResNoSlash = createDavResource("/DavFolder", true);
        assertTrue("Self resource without trailing slash should match", WebdavFile2.isSelfResource(selfResNoSlash, dirUri));

        // Different casing: WebDAV paths can be case-sensitive, must not match
        DavResource differentCaseRes = createDavResource("/davfolder/", true);
        assertFalse("Different case should not match self-resource", WebdavFile2.isSelfResource(differentCaseRes, dirUri));

        // Child resource with same name prefix
        DavResource childRes = createDavResource("/DavFolder/child", false);
        assertFalse("Child resource should not match self-resource", WebdavFile2.isSelfResource(childRes, dirUri));
    }

    @Test
    public void testRangeValidationExactStart() {
        assertTrue(WebdavFileEditor.validateExactContentRangeStart("bytes 100-200/500", 100));
        assertTrue(WebdavFileEditor.validateExactContentRangeStart("bytes 0-499/500", 0));

        // Offset mismatch
        assertFalse(WebdavFileEditor.validateExactContentRangeStart("bytes 0-200/500", 100));
        assertFalse(WebdavFileEditor.validateExactContentRangeStart("bytes 50-200/500", 100));

        // Malformed headers
        assertFalse(WebdavFileEditor.validateExactContentRangeStart("100-200/500", 100));
        assertFalse(WebdavFileEditor.validateExactContentRangeStart("bytes invalid-200/500", 100));
        assertFalse(WebdavFileEditor.validateExactContentRangeStart(null, 100));
    }

    @Test
    public void testRangeRequestRejects200Ok() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200));
            server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("full content without range support"));
            server.start();

            Uri uri = Uri.parse("webdav://" + server.getHostName() + ":" + server.getPort() + "/file.bin");
            WebdavFileEditor editor = new WebdavFileEditor(uri);

            try (InputStream in = editor.getInputStream(100)) {
                fail("Expected IOException when server returns 200 instead of 206 for range request");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("206"));
            }
        }
    }

    @Test
    public void testRangeRequestHandles416() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200));
            server.enqueue(new MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */50"));
            server.start();

            Uri uri = Uri.parse("webdav://" + server.getHostName() + ":" + server.getPort() + "/file.bin");
            WebdavFileEditor editor = new WebdavFileEditor(uri);

            try (InputStream in = editor.getInputStream(100)) {
                fail("Expected IOException on HTTP 416");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("416"));
            }
        }
    }

    @Test
    public void testRangeRequestAccepts206WithExactStart() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200));
            server.enqueue(new MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 100-199/1000")
                .setBody("test content"));
            server.start();

            Uri uri = Uri.parse("webdav://" + server.getHostName() + ":" + server.getPort() + "/file.bin");
            WebdavFileEditor editor = new WebdavFileEditor(uri);

            try (InputStream in = editor.getInputStream(100)) {
                assertNotNull(in);
                assertEquals(1000L, editor.length());
            }
        }
    }

    @Test
    public void testRedirectWithBasePathPreservation() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/remote.php/webdav/"));
            server.start();

            Uri originalUri = Uri.parse("webdav://" + server.getHostName() + ":" + server.getPort() + "/sub/file.txt");
            Uri httpUri = Uri.parse("http://" + server.getHostName() + ":" + server.getPort() + "/sub/file.txt");

            String resolved = WebdavUtils.peekInstance().resolveRedirect(httpUri);
            assertEquals("http://" + server.getHostName() + ":" + server.getPort() + "/remote.php/webdav", resolved);

            Uri finalHttpUri = WebdavFile2.uriToHttp(originalUri);
            assertEquals("http://" + server.getHostName() + ":" + server.getPort() + "/remote.php/webdav/sub/file.txt", finalHttpUri.toString());
        }
    }

    @Test
    public void testHttpsToHttpRedirectBlocked() throws Exception {
        HeldCertificate localhostCertificate = new HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder()
            .heldCertificate(localhostCertificate)
            .build();

        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(serverCertificates.sslSocketFactory(), false);
            server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://insecure.example.com/dav/"));
            server.start();

            // Configure redirectClient SSL socket factory to trust localhost test cert
            HandshakeCertificates clientCertificates = new HandshakeCertificates.Builder()
                .addTrustedCertificate(localhostCertificate.certificate())
                .build();
            OkHttpClient testTlsClient = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager())
                .build();
            WebdavUtils.setRedirectClientForTesting(testTlsClient);

            try {
                Uri httpsUri = Uri.parse("https://localhost:" + server.getPort() + "/secure/dir/");
                String resolved = WebdavUtils.peekInstance().resolveRedirect(httpsUri);

                // Assert that the insecure HTTP redirect was rejected and base remains HTTPS
                assertFalse("HTTPS to HTTP downgrade redirect must not be accepted", resolved.startsWith("http://insecure.example.com"));
                assertEquals("Must fall back to original HTTPS base URL", "https://localhost:" + server.getPort(), resolved);
            } finally {
                WebdavUtils.setRedirectClientForTesting(null);
            }
        }
    }

    @Test
    public void testCrossOriginAuthSecurity() throws Exception {
        try (MockWebServer server1 = new MockWebServer();
             MockWebServer server2 = new MockWebServer()) {

            // Server 1 redirects directly to Server 2 with 302
            server1.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server2.url("/resource").toString()));

            server2.enqueue(new MockResponse().setResponseCode(200).setBody("success"));

            server1.start();
            server2.start();

            Uri uri1 = Uri.parse("webdav://" + server1.getHostName() + ":" + server1.getPort() + "/path");
            NetworkCredentialsDatabase.getInstance().addCredential(
                new NetworkCredentialsDatabase.Credential("user", "secret", "webdav://" + server1.getHostName() + ":" + server1.getPort(), "", false)
            );

            // getSardine builds the client and caches in httpClients
            WebdavUtils.peekInstance().getSardine(uri1);
            OkHttpClient client = WebdavUtils.peekInstance().getHttpClient(uri1);
            Request req = new Request.Builder()
                .url(server1.url("/path").toString())
                .get()
                .build();

            try (okhttp3.Response resp = client.newCall(req).execute()) {
                assertEquals(200, resp.code());
            }

            // Server 1 (origin) received preemptive Authorization
            RecordedRequest req1 = server1.takeRequest();
            assertNotNull("Server 1 should receive Authorization", req1.getHeader("Authorization"));

            // Server 2 (redirected cross-origin) did NOT receive Server 1's credentials
            RecordedRequest req2 = server2.takeRequest();
            assertNull("Cross-origin redirect must not disclose credentials", req2.getHeader("Authorization"));
        }
    }

    @Test
    public void testCrossOriginRedirect401ChallengeDoesNotDiscloseSourceCredentials() throws Exception {
        try (MockWebServer server1 = new MockWebServer();
             MockWebServer server2 = new MockWebServer()) {

            // Server 1 HEAD / (used by resolveRedirect) redirects to Server 2
            server1.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", server2.url("/webdav").toString()));

            // Server 2 challenges with 401 Unauthorized
            server2.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("WWW-Authenticate", "Basic realm=\"target-realm\"")
                .setBody("Authentication Required"));

            server1.start();
            server2.start();

            Uri originUri = Uri.parse("webdav://" + server1.getHostName() + ":" + server1.getPort() + "/files/");
            NetworkCredentialsDatabase.getInstance().addCredential(
                new NetworkCredentialsDatabase.Credential("superUser", "secretPass", "webdav://" + server1.getHostName() + ":" + server1.getPort(), "", false)
            );

            OkHttpSardine sardine = WebdavUtils.peekInstance().getSardine(originUri);

            // Resolve redirect via uriToHttp
            Uri finalHttpUri = WebdavFile2.uriToHttp(originUri);
            assertEquals("Target should be resolved to server 2", server2.getHostName(), finalHttpUri.getHost());

            // Perform PROPFIND (sardine.list) against finalHttpUri
            try {
                sardine.list(finalHttpUri.toString());
                fail("Expected 401 SardineException when target challenges for authentication without explicit credentials");
            } catch (SardineException se) {
                assertEquals(401, se.getStatusCode());
            }

            // Server 2 received the PROPFIND request
            RecordedRequest targetReq = server2.takeRequest();
            assertEquals("PROPFIND", targetReq.getMethod());
            // Verify Server 2 received NO Authorization header (neither preemptive nor via authenticator fallback)
            assertNull("Cross-origin target must receive NO source Authorization header", targetReq.getHeader("Authorization"));

            // Verify Server 2 received only 1 request (no retry loops or credential disclosures)
            assertEquals("Server 2 should have received exactly one request", 1, server2.getRequestCount());
        }
    }

    @Test
    public void testTempFileDeletedAfterFailedPut() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200));
            server.enqueue(new MockResponse().setResponseCode(500).setBody("Server Error"));
            server.start();

            Uri uri = Uri.parse("webdav://" + server.getHostName() + ":" + server.getPort() + "/failed_upload.bin");
            WebdavFileEditor editor = new WebdavFileEditor(uri);

            File cacheDir = mContext.getCacheDir();
            File[] beforeFiles = cacheDir.listFiles((dir, name) -> name.startsWith("webdav_upload_"));
            int countBefore = beforeFiles != null ? beforeFiles.length : 0;

            try {
                OutputStream os = editor.getOutputStream();
                os.write("sample payload".getBytes());
                os.close();
                fail("Expected IOException on PUT failure");
            } catch (IOException e) {
                // expected
            }

            File[] afterFiles = cacheDir.listFiles((dir, name) -> name.startsWith("webdav_upload_"));
            int countAfter = afterFiles != null ? afterFiles.length : 0;
            assertEquals("Temporary file must be deleted after failed upload", countBefore, countAfter);
        }
    }

    @Test
    public void testDepthZeroSelectExactMatchWhenMultiple() throws Exception {
        Uri queryUri = Uri.parse("http://example.com/media/Movie.mkv");
        DavResource exactRes = createDavResource("/media/Movie.mkv", false);
        DavResource canonicalRes = createDavResource("/canonical/Movie.mkv", false);

        // When exact match is present alongside another resource, exact match is chosen
        DavResource selected = WebdavFile2.selectResourceForDepthZero(
            java.util.Arrays.asList(canonicalRes, exactRes), queryUri);
        assertEquals("Exact match should be preferred", exactRes, selected);
    }

    @Test
    public void testDepthZeroAcceptsSingleCanonicalHref() throws Exception {
        Uri aliasUri = Uri.parse("http://example.com/alias/Movie.mkv");
        DavResource canonicalRes = createDavResource("/real-path/Movie.mkv", false);

        // When server returns a single resource with a canonical/alternate href, accept it
        DavResource selected = WebdavFile2.selectResourceForDepthZero(
            Collections.singletonList(canonicalRes), aliasUri);
        assertEquals("Single canonical resource should be accepted for alias", canonicalRes, selected);
    }

    @Test
    public void testDepthZeroRejectsAmbiguousNonMatchingResponses() throws Exception {
        Uri queryUri = Uri.parse("http://example.com/alias/Movie.mkv");
        DavResource res1 = createDavResource("/real-path/Movie1.mkv", false);
        DavResource res2 = createDavResource("/real-path/Movie2.mkv", false);

        // When multiple resources are returned and neither matches exact href, reject as ambiguous
        DavResource selected = WebdavFile2.selectResourceForDepthZero(
            java.util.Arrays.asList(res1, res2), queryUri);
        assertNull("Ambiguous multi-resource response without exact match should return null", selected);
    }

    @Test
    public void testDepthZeroRejectsEmptyResponse() throws Exception {
        Uri queryUri = Uri.parse("http://example.com/nonexistent.mkv");
        DavResource selected = WebdavFile2.selectResourceForDepthZero(
            Collections.emptyList(), queryUri);
        assertNull("Empty response should return null", selected);
    }

    @Test
    public void testFromUriIssuesDepthZeroRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            // Sardine expects a 207 Multi-Status XML body for PROPFIND
            String multistatusBody = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
                + "<D:multistatus xmlns:D=\"DAV:\">\n"
                + "  <D:response>\n"
                + "    <D:href>/video.mkv</D:href>\n"
                + "    <D:propstat>\n"
                + "      <D:prop>\n"
                + "        <D:getcontentlength>1048576</D:getcontentlength>\n"
                + "        <D:getlastmodified>Wed, 09 Sep 2026 12:00:00 GMT</D:getlastmodified>\n"
                + "      </D:prop>\n"
                + "      <D:status>HTTP/1.1 200 OK</D:status>\n"
                + "    </D:propstat>\n"
                + "  </D:response>\n"
                + "</D:multistatus>";

            // 1st request: HEAD / used by resolveRedirect
            server.enqueue(new MockResponse().setResponseCode(200));
            // 2nd request: PROPFIND used by fromUri
            server.enqueue(new MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(multistatusBody));

            server.start();

            Uri webdavUri = Uri.parse("webdav://" + server.getHostName() + ":" + server.getPort() + "/video.mkv");
            com.archos.filecorelibrary.MetaFile2 metaFile = WebdavFile2.fromUri(webdavUri);

            assertNotNull("MetaFile2 must be successfully returned", metaFile);
            assertEquals("video.mkv", metaFile.getName());
            assertEquals(1048576L, metaFile.length());

            // 1st request was HEAD (resolveRedirect)
            RecordedRequest headReq = server.takeRequest();
            assertEquals("HEAD", headReq.getMethod());

            // 2nd request was PROPFIND (fromUri)
            RecordedRequest propfindReq = server.takeRequest();
            assertEquals("PROPFIND", propfindReq.getMethod());
            assertEquals("/video.mkv", propfindReq.getPath());
            assertEquals("fromUri must issue Depth: 0 header", "0", propfindReq.getHeader("Depth"));
        }
    }
}
