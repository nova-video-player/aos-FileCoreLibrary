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

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/**
 * Opt-in real-network benchmark comparing Nova's minimal-property Depth: 1 PROPFIND with
 * Sardine's legacy allprop request. It is skipped unless an external CSV is explicitly supplied;
 * credentials must never be committed to the repository or printed in test output.
 *
 * <p>CSV format: {@code webdav[s]://host/path,user,password}. Blank lines and lines beginning with
 * {@code #} are ignored. The URL must name a directory. Passwords cannot contain commas.</p>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :FileCoreLibrary:testDebugUnitTest --tests "*WebdavPropfindBenchmarkTest" \
 *     -Dnova.test.webdavPropfindCsv=/private/tmp/webdav.csv
 * </pre>
 * </p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WebdavPropfindBenchmarkTest {

    private static final String CSV_PATH_PROPERTY = "nova.test.webdavPropfindCsv";
    private static final int MEASUREMENTS = 3;

    private static final class Row {
        final Uri uri;
        final String username;
        final String password;

        Row(Uri uri, String username, String password) {
            this.uri = uri;
            this.username = username;
            this.password = password;
        }
    }

    private static final class Measurement {
        final long elapsedMillis;
        final int resourceCount;
        final Set<String> metadata;

        Measurement(long elapsedMillis, List<DavResource> resources) {
            this.elapsedMillis = elapsedMillis;
            this.resourceCount = resources.size();
            this.metadata = metadata(resources);
        }
    }

    @Before
    public void setUp() {
        String csvPath = System.getProperty(CSV_PATH_PROPERTY);
        assumeTrue("Set -D" + CSV_PATH_PROPERTY + "=/absolute/path/to/webdav.csv to run this benchmark",
                csvPath != null && new File(csvPath).isFile());

        Application app = ApplicationProvider.getApplicationContext();
        ArchosUtils.setGlobalContext(app);
        WebdavUtils.getInstance(app);
    }

    @Test
    public void compareMinimalPropertiesWithAllprop() throws Exception {
        List<Row> rows = parseCsv(System.getProperty(CSV_PATH_PROPERTY));
        assumeTrue("CSV contains no usable WebDAV rows", !rows.isEmpty());

        for (Row row : rows) {
            if (!row.username.isEmpty()) {
                NetworkCredentialsDatabase.getInstance().addCredential(
                        new NetworkCredentialsDatabase.Credential(
                                row.username, row.password, row.uri.toString(), "", true));
            }

            OkHttpSardine sardine = WebdavUtils.peekInstance().getSardine(row.uri);
            Uri httpUri = WebdavFile2.uriToHttp(row.uri);

            // Warm both request variants so connection establishment and redirect probing do not
            // dominate one of the measured variants.
            WebdavUtils.listResources(sardine, httpUri.toString(), 1);
            sardine.list(httpUri.toString(), 1);

            List<Measurement> minimal = new ArrayList<>();
            List<Measurement> allprop = new ArrayList<>();
            for (int i = 0; i < MEASUREMENTS; i++) {
                // Alternate first request to avoid consistently favoring one variant.
                Measurement minimalMeasurement;
                Measurement allpropMeasurement;
                if ((i & 1) == 0) {
                    minimalMeasurement = measureMinimal(sardine, httpUri);
                    allpropMeasurement = measureAllprop(sardine, httpUri);
                } else {
                    allpropMeasurement = measureAllprop(sardine, httpUri);
                    minimalMeasurement = measureMinimal(sardine, httpUri);
                }
                assertEquivalent(row.uri, minimalMeasurement, allpropMeasurement);
                minimal.add(minimalMeasurement);
                allprop.add(allpropMeasurement);
            }

            printResult(row.uri, minimal, allprop);
        }
    }

    private Measurement measureMinimal(OkHttpSardine sardine, Uri uri) throws Exception {
        long start = System.nanoTime();
        List<DavResource> resources = WebdavUtils.listResources(sardine, uri.toString(), 1);
        return new Measurement(elapsedMillis(start), resources);
    }

    private Measurement measureAllprop(OkHttpSardine sardine, Uri uri) throws Exception {
        long start = System.nanoTime();
        List<DavResource> resources = sardine.list(uri.toString(), 1);
        return new Measurement(elapsedMillis(start), resources);
    }

    private static void assertEquivalent(Uri uri, Measurement minimal, Measurement allprop) {
        assertEquals("Minimal-property PROPFIND returned a different resource count for " + uri,
                allprop.resourceCount, minimal.resourceCount);
        assertEquals("Minimal-property PROPFIND omitted metadata Nova needs for " + uri,
                allprop.metadata, minimal.metadata);
    }

    private static Set<String> metadata(List<DavResource> resources) {
        Set<String> result = new HashSet<>();
        for (DavResource resource : resources) {
            result.add(resource.getPath() + "\\u0000" + resource.isDirectory() + "\\u0000"
                    + resource.getContentLength() + "\\u0000"
                    + (resource.getModified() == null ? -1 : resource.getModified().getTime()));
        }
        return result;
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static void printResult(Uri uri, List<Measurement> minimal, List<Measurement> allprop) {
        long minimalMedian = medianMillis(minimal);
        long allpropMedian = medianMillis(allprop);
        int minimalResources = minimal.get(0).resourceCount;
        int allpropResources = allprop.get(0).resourceCount;
        double ratio = allpropMedian == 0 ? 0 : (double) minimalMedian / allpropMedian;

        System.out.println(String.format(Locale.US,
                "WebDAV PROPFIND %s%n  minimal props: median=%d ms, resources=%d%n"
                        + "  allprop:       median=%d ms, resources=%d%n"
                        + "  minimal/allprop ratio: %.2f%n",
                uri, minimalMedian, minimalResources, allpropMedian, allpropResources, ratio));
    }

    private static long medianMillis(List<Measurement> measurements) {
        List<Long> elapsed = new ArrayList<>();
        for (Measurement measurement : measurements) {
            elapsed.add(measurement.elapsedMillis);
        }
        Collections.sort(elapsed);
        return elapsed.get(elapsed.size() / 2);
    }

    private static List<Row> parseCsv(String path) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",", -1);
                if (parts.length < 1 || parts[0].trim().isEmpty()) continue;
                if (parts.length > 3) {
                    throw new IllegalArgumentException("Expected url,username,password (passwords cannot contain commas)");
                }

                Uri uri = Uri.parse(parts[0].trim());
                String scheme = uri.getScheme();
                if (!"webdav".equals(scheme) && !"webdavs".equals(scheme)) {
                    throw new IllegalArgumentException("Only webdav:// and webdavs:// URLs are supported: " + uri);
                }
                if (uri.getUserInfo() != null) {
                    throw new IllegalArgumentException("Credentials must be in the CSV columns, not the URL");
                }
                String username = parts.length > 1 ? parts[1].trim() : "";
                String password = parts.length > 2 ? parts[2] : "";
                rows.add(new Row(uri, username, password));
            }
        }
        return rows;
    }
}
