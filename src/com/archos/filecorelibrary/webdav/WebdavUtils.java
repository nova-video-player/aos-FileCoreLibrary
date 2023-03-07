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


public class WebdavUtils {

    private static final Logger log = LoggerFactory.getLogger(WebdavUtils.class);
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, OkHttpSardine> sardines = new ConcurrentHashMap<>();
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
        log.debug("WebdavUtils: initializing contexts");
    }

    public synchronized OkHttpSardine getSardine(Uri uri) {
        String username = "anonymous";
        String password = "";
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        password = cred.getPassword();
        username = cred.getUsername();
        OkHttpSardine sardine = sardines.get(cred);
        if (sardine == null) {
            sardine = new OkHttpSardine();
            sardine.setCredentials(username, password);
            sardines.put(cred, sardine);
            return sardine;
        }
        return sardine;
    }

    private Uri buildKeyFromUri(Uri uri) {
        // use Uri without the path segment as key: for example, "webdav://blabla.com:5006/toto/titi" gives a "webdav://blabla.com:5006" key
        return uri.buildUpon().path("").build();
    }

}
