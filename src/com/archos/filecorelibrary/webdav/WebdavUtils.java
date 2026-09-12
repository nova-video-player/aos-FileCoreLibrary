// Copyright 2019 Courville Software
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

import androidx.annotation.Nullable;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.xml.namespace.QName;

import okhttp3.OkHttpClient;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;

public class WebdavUtils {

    private static final Logger log = LoggerFactory.getLogger(WebdavUtils.class);
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, OkHttpSardine> sardines = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, OkHttpClient> httpClients = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<Uri, String> resolvedRedirects = new ConcurrentHashMap<>();
    /**
     * The only live properties needed to construct and sort WebdavFile2 entries. Requesting
     * allprop makes large directory scans unnecessarily expensive on many WebDAV servers.
     */
    private static final Set<QName> LISTING_PROPERTIES = Set.of(
            // OkHttpSardine requires a non-empty QName prefix when serializing a <prop> request.
            new QName("DAV:", "resourcetype", "D"),
            new QName("DAV:", "getcontentlength", "D"),
            new QName("DAV:", "getlastmodified", "D"));
    private static final OkHttpClient DEFAULT_REDIRECT_CLIENT = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
    private static volatile OkHttpClient sRedirectClient = DEFAULT_REDIRECT_CLIENT;

    /** Visible only for tests to inject custom SSL configurations safely */
    static void setRedirectClientForTesting(@Nullable OkHttpClient client) {
        sRedirectClient = (client != null) ? client : DEFAULT_REDIRECT_CLIENT;
    }
    private static Context mContext;
    // singleton, volatile to make double-checked-locking work correctly
    private static volatile WebdavUtils sInstance;

    // get the instance, context is used for initial context injection
    public static WebdavUtils getInstance(Context context) {
        if (context == null) log.warn("getInstance: context passed is null!!!");
        else if (mContext == null) mContext = context;
        if (sInstance == null) {
            synchronized(WebdavUtils.class) {
                if (sInstance == null) sInstance = new WebdavUtils(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    /** may return null but no Context required */
    public static WebdavUtils peekInstance() {
        return sInstance;
    }

    public static Context getContext() {
        return mContext;
    }

    static List<DavResource> listResources(OkHttpSardine sardine, String url, int depth) throws IOException {
        return sardine.list(url, depth, LISTING_PROPERTIES);
    }

    private WebdavUtils(Context context) {
        mContext = context;
        if (log.isDebugEnabled()) log.debug("WebdavUtils: initializing contexts");
    }

    public synchronized OkHttpSardine getSardine(Uri uri) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        final String password = cred.getPassword();
        final String username = cred.getUsername();
        OkHttpSardine sardine = sardines.get(cred);
        if (sardine == null) {
            // configure OkHttpClient to support 302 redirects
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            if (log.isTraceEnabled()) {
                HttpLoggingInterceptor logging = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                    @Override
                    public void log(String msg) {
                        log.trace("OkHttpSardine: webdav {}", msg);
                    }});
                logging.setLevel(HttpLoggingInterceptor.Level.HEADERS);
                logging.redactHeader("Authorization");
                builder.addInterceptor(logging);
            }
            // Add preemptive authentication scoped strictly to matching origin
            final String authHost = uri.getHost();
            final int authPort = uri.getPort() != -1 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) || "webdavs".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
            final boolean wasHttps = "https".equalsIgnoreCase(uri.getScheme()) || "webdavs".equalsIgnoreCase(uri.getScheme());
            final String preemptiveCredential = Credentials.basic(username, password, StandardCharsets.UTF_8);

            builder.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request request = chain.request();
                    if (request.header("Authorization") == null && !username.equals("anonymous")) {
                        var reqUrl = request.url();
                        // Block sending credentials in cleartext if originally configured as HTTPS
                        if (wasHttps && !reqUrl.isHttps()) {
                            log.warn("intercept: blocked attaching credentials over insecure HTTP for {}", reqUrl);
                        } else if (authHost != null && authHost.equalsIgnoreCase(reqUrl.host()) && authPort == reqUrl.port()) {
                            request = request.newBuilder()
                                .header("Authorization", preemptiveCredential)
                                .build();
                        } else {
                            // Target authority differs from client's base authority; lookup credentials for target
                            NetworkCredentialsDatabase.Credential targetCred =
                                NetworkCredentialsDatabase.getInstance().getCredential(reqUrl.toString());
                            if (targetCred != null && !targetCred.getUsername().equals("anonymous")) {
                                String targetCredential = Credentials.basic(targetCred.getUsername(), targetCred.getPassword(), StandardCharsets.UTF_8);
                                request = request.newBuilder()
                                    .header("Authorization", targetCredential)
                                    .build();
                            }
                        }
                    }
                    return chain.proceed(request);
                }
            });
            builder.authenticator(new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    if (response.request().header("Authorization") != null) {
                        return null;
                    }
                    var reqUrl = response.request().url();
                    if (wasHttps && !reqUrl.isHttps()) {
                        log.warn("authenticate: blocked authenticating over insecure HTTP for {}", reqUrl);
                        return null;
                    }
                    final String u;
                    final String p;
                    if (authHost != null && authHost.equalsIgnoreCase(reqUrl.host()) && authPort == reqUrl.port()) {
                        // Original authority
                        u = username;
                        p = password;
                    } else {
                        // Cross-origin target authority: require explicit matching credentials
                        NetworkCredentialsDatabase.Credential targetCred =
                            NetworkCredentialsDatabase.getInstance().getCredential(reqUrl.toString());
                        if (targetCred != null && !targetCred.getUsername().equals("anonymous")) {
                            u = targetCred.getUsername();
                            p = targetCred.getPassword();
                        } else {
                            return null;
                        }
                    }
                    if ("anonymous".equals(u)) {
                        return null;
                    }
                    String credential = Credentials.basic(u, p, StandardCharsets.UTF_8);
                    return response.request().newBuilder().header("Authorization", credential).build();
                }
            });
            builder.followRedirects(true);
            builder.followSslRedirects(true); // Handle SSL redirect
            // Set the custom client to the Sardine instance (do not call sardine.setCredentials as it replaces the scoped authenticator)
            var client = builder.build();
            sardine = new OkHttpSardine(client);
            httpClients.put(cred, client);
            sardines.put(cred, sardine);
            return sardine;
        }
        return sardine;
    }

    public synchronized OkHttpClient getHttpClient(Uri uri) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        return httpClients.get(cred);
    }

    private Uri buildKeyFromUri(Uri uri) {
        // use Uri without the path segment as key: for example, "webdav://blabla.com:5006/toto/titi" gives a "webdav://blabla.com:5006" key
        return uri.buildUpon().path("").build();
    }

    public String resolveRedirect(Uri uri) {
        Uri key = buildKeyFromUri(uri);
        String resolved = resolvedRedirects.get(key);
        if (resolved != null) {
            if (log.isTraceEnabled()) log.trace("resolveRedirect: cache hit for " + key + " -> " + resolved);
            return resolved;
        }
        if (log.isTraceEnabled()) log.trace("resolveRedirect: cache miss for " + key);

        String baseUrl = uri.getScheme() + "://" + uri.getHost();
        if (uri.getPort() != -1) {
            baseUrl += ":" + uri.getPort();
        }

        Request request = new Request.Builder().url(baseUrl + "/").head().build();
        try (Response response = sRedirectClient.newCall(request).execute()) {
            if (response.code() == 301 || response.code() == 302 || response.code() == 307 || response.code() == 308) {
                String location = response.header("Location");
                if (location != null && !location.isEmpty()) {
                    try {
                        java.net.URI baseUriObj = new java.net.URI(baseUrl + "/");
                        java.net.URI resolvedUriObj = baseUriObj.resolve(location);
                        String resolvedScheme = resolvedUriObj.getScheme();
                        if (resolvedUriObj.getHost() != null && resolvedScheme != null) {
                            if ("https".equalsIgnoreCase(uri.getScheme()) && "http".equalsIgnoreCase(resolvedScheme)) {
                                log.warn("resolveRedirect: blocked insecure HTTPS to HTTP downgrade from {} to {}", uri, resolvedUriObj);
                            } else {
                                String rawPath = resolvedUriObj.getRawPath();
                                if (rawPath != null && rawPath.endsWith("/")) {
                                    rawPath = rawPath.substring(0, rawPath.length() - 1);
                                }
                                String redirectBase = resolvedScheme + "://" + resolvedUriObj.getHost();
                                if (resolvedUriObj.getPort() != -1) {
                                    redirectBase += ":" + resolvedUriObj.getPort();
                                }
                                if (rawPath != null && !rawPath.isEmpty()) {
                                    redirectBase += rawPath;
                                }
                                resolved = redirectBase;
                                if (log.isDebugEnabled()) log.debug("resolveRedirect: resolved " + key + " to " + resolved);
                            }
                        } else {
                            log.warn("resolveRedirect: invalid redirect URL format: " + location);
                        }
                    } catch (Exception e) {
                        log.warn("resolveRedirect: failed to parse redirect URL: " + location, e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("resolveRedirect: failed for " + uri, e);
        }

        if (resolved == null) {
            resolved = baseUrl;
        }
        resolvedRedirects.put(key, resolved);
        return resolved;
    }
}
