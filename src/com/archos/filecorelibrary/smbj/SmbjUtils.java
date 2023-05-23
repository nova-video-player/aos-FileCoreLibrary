// Copyright 2023 Courville Software
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

package com.archos.filecorelibrary.smbj;

import static com.archos.filecorelibrary.FileUtils.getShareName;

import android.content.Context;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;

import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SmbjUtils {

    private static final Logger log = LoggerFactory.getLogger(SmbjUtils.class);
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, Session> smbjSessions = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, DiskShare> smbjShares = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, Connection> smbjConnections = new ConcurrentHashMap<>();
    private static Context mContext;
    // singleton, volatile to make double-checked-locking work correctly
    private static volatile SmbjUtils sInstance;

    private static SmbConfig smbConfig;

    // get the instance, context is used for initial context injection
    public static SmbjUtils getInstance(Context context) {
        if (context == null) log.warn("getInstance: context passed is null!!!");
        else if (mContext == null) mContext = context;
        if (sInstance == null) {
            synchronized(SmbjUtils.class) {
                if (sInstance == null) sInstance = new SmbjUtils(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    /** may return null but no Context required */
    public static SmbjUtils peekInstance() {
        return sInstance;
    }

    private SmbjUtils(Context context) {
        mContext = context;
        log.debug("SmbjUtils: initializing contexts");
        smbConfig = SmbConfig.builder().withSecurityProvider(new BCSecurityProvider())
                .build();
    }

    public synchronized void getSmbConnection(Uri uri) throws IOException, SMBApiException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        String server = uri.getHost();
        String password = cred.getPassword();
        String username = cred.getUsername();
        String domain = cred.getDomain();
        int port = uri.getPort();
        Connection smbConnection = smbjConnections.get(cred);
        if (smbConnection == null || !smbConnection.isConnected()) {
            log.trace("getSmbConnection: smbConnection is null or not connected for " + uri + ", connecting to " + server);
            SMBClient smbClient;
            if (smbConfig != null) smbClient = new SMBClient(smbConfig);
            else smbClient = new SMBClient();
            final String serverIP = JcifsUtils.getInstance(mContext).getBaseContextOnly(true).getNameServiceClient().getByName(server).getHostAddress();
            log.trace("getSmbConnection: {} -> {}", server, serverIP);
            if (port != -1) smbConnection = smbClient.connect(serverIP, port);
            else smbConnection = smbClient.connect(serverIP);
            smbjConnections.put(cred, smbConnection);
            // need to regenerate smbSession in this case too
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), domain);
            Session smbSession = smbConnection.authenticate(ac);
            smbjSessions.put(cred, smbSession);
        }
    }

    public synchronized DiskShare getSmbShare(Uri uri) throws IOException, SMBApiException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        String shareName = getShareName(uri);
        getSmbConnection(uri); // be sure to be connected
        // shareName can be null when asking for smbj://server/
        if (shareName == null) return null;
        DiskShare smbShare = smbjShares.get(cred);
        if (smbShare == null || !shareName.equals(getShareName(Uri.parse(cred.getUriString()))) || !smbShare.isConnected()) {
            log.trace("getSmbShare: smbShare is null or not connected for " + shareName);
            // ensures that there is a valid connection and regenerate session if not connected
            getSmbConnection(uri);
            Session smbSession = smbjSessions.get(cred);
            if (smbSession != null) {
                smbShare = (DiskShare) smbSession.connectShare(shareName);
                log.trace("getSmbShare: saving smbShare " + shareName + ", smbshare=" + smbShare);
                smbjShares.put(cred, smbShare);
            }
        }
        log.debug("getSmbShare: for uri {}, sharename={}, smbShare={}, isConnected={}", uri, shareName, smbShare, (smbShare != null)?smbShare.isConnected():"false");
        return smbShare;
    }

    private static Uri buildKeyFromUri(Uri uri) {
        // use Uri without the path segment as key: for example, "smbj://blabla.com:5006/toto/titi" gives a "smbj://blabla.com:5006" key
        return uri.buildUpon().path("").build();
    }

    public static boolean isDirectory(FileIdBothDirectoryInformation fileEntry) {
        return EnumWithValue.EnumUtils.isSet(fileEntry.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
    }

    public static boolean isSMBjEnabled() {
        log.trace("isSMBjEnabled=" + PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbj", false));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbj", false);
    }

}
