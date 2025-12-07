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

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.OkHttpClient;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;

public class WebdavUtils {

    private static final Logger log = LoggerFactory.getLogger(WebdavUtils.class);
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, OkHttpSardine> sardines = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, OkHttpClient> httpClients = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<Uri, String> resolvedRedirects = new ConcurrentHashMap<>();
    private static final OkHttpClient redirectClient = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
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
                builder.addInterceptor(logging);
            }
            builder.authenticator(new Authenticator() {
                @Override
                public Request authenticate(Route route, Response response) throws IOException {
                    if (response.request().header("Authorization") != null) {
                        return null;
                    }
                    String credential = Credentials.basic(username, password, StandardCharsets.UTF_8);
                    return response.request().newBuilder().header("Authorization", credential).build();
                }
            });
            builder.followRedirects(true);
            builder.followSslRedirects(true); // Handle SSL redirect
            // Set the custom client to the Sardine instance
            var client = builder.build();
            sardine = new OkHttpSardine(client);
            sardine.setCredentials(username, password);
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
        try (Response response = redirectClient.newCall(request).execute()) {
            if (response.code() == 301 || response.code() == 302 || response.code() == 307 || response.code() == 308) {
                String location = response.header("Location");
                if (location != null && !location.isEmpty()) {
                    try {
                        Uri redirectUri = Uri.parse(location);
                        if (redirectUri.getHost() != null && redirectUri.getScheme() != null) {
                            String redirectBase = redirectUri.getScheme() + "://" + redirectUri.getHost();
                            if (redirectUri.getPort() != -1) {
                                redirectBase += ":" + redirectUri.getPort();
                            }
                            resolved = redirectBase;
                            if (log.isDebugEnabled()) log.debug("resolveRedirect: resolved " + key + " to " + resolved);
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
