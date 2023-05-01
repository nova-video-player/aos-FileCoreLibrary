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

package com.archos.filecorelibrary.sshj;

import static com.archos.filecorelibrary.FileUtils.caughtException;
import static com.archos.filecorelibrary.FileUtils.encodeUri;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class SshjUtils {

    private static final Logger log = LoggerFactory.getLogger(SshjUtils.class);
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, SSHClient> sshClients = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, SFTPClient> sftpClients = new ConcurrentHashMap<>();
    private static Context mContext;
    // singleton, volatile to make double-checked-locking work correctly
    private static volatile SshjUtils sInstance;

    // get the instance, context is used for initial context injection
    public static SshjUtils getInstance(Context context) {
        if (context == null) log.warn("getInstance: context passed is null!!!");
        else if (mContext == null) mContext = context;
        if (sInstance == null) {
            synchronized(SshjUtils.class) {
                if (sInstance == null) sInstance = new SshjUtils(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    /** may return null but no Context required */
    public static SshjUtils peekInstance() {
        return sInstance;
    }

    private SshjUtils(Context context) {
        mContext = context;
        log.debug("SshjUtils: initializing contexts");
    }

    public synchronized SSHClient getSshClient(Uri uri) throws IOException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        String server = uri.getHost();
        String password = cred.getPassword();
        String username = cred.getUsername();
        int port = uri.getPort();
        SSHClient sshClient = sshClients.get(cred);
        if (sshClient == null || !sshClient.isConnected()) {
            log.trace("getSshClient: sshClient is null or not connected for " + uri + ", connecting to " + server);
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            if (port != -1) sshClient.connect(server, port);
            else sshClient.connect(server);
            sshClient.authPassword(username, password.toCharArray());
            sshClients.put(cred, sshClient);
        }
        return sshClient;
    }

    public static synchronized void disconnectSshClient(Uri uri) {
        try {
            NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
            if (cred == null)
                cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
            SSHClient sshClient = sshClients.get(cred);
            if (sshClient != null && sshClient.isConnected()) {
                sshClient.disconnect();
                sshClients.remove(cred);
                log.trace("disconnectSshClient: sshClient disconnected for " + uri);
            }
        } catch (IOException e) {
            caughtException(e, "SshjUtils:disconnectSshClient", "IOException " + uri);
        }
    }

    public synchronized SFTPClient getSFTPClient(Uri uri) throws IOException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        SFTPClient sftpClient = sftpClients.get(cred);
        if (sftpClient == null) {
            log.trace("getSFTPClient: sftpClient is null or not connected for " + sftpClient);
            // ensures that there is a valid connection and regenerate session if not connected
            getSshClient(uri);
            SSHClient sshClient = sshClients.get(cred);
            if (sshClient != null) {
                sftpClient = sshClient.newSFTPClient();
                log.trace("getSFTPClient: saving sftpClient " + sftpClient);
                sftpClients.put(cred, sftpClient);
            }
        }
        log.debug("getSFTPClient: for uri {}, sftpClient={}", uri, sftpClient);
        return sftpClient;
    }

    public static synchronized void closeSFTPClient(Uri uri)  {
        try {
            NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
            if (cred == null)
                cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
            SFTPClient sftpClient = sftpClients.get(cred);
            if (sftpClient != null) {
                sftpClient.close();
                sftpClients.remove(cred);
                log.trace("closeSFTPClient: sftpClient disconnected for " + uri);
            }
        } catch (IOException e) {
            caughtException(e, "SshjUtils:closeSFTPClient", "IOException " + uri);
        }
    }

    private static Uri buildKeyFromUri(Uri uri) {
        // use Uri without the path segment as key: for example, "sshj://blabla.com:5006/toto/titi" gives a "sshj://blabla.com:5006" key
        return uri.buildUpon().path("").build();
    }

    public static String getSftpPath(Uri uri) {
        return encodeUri(uri).getPath();
    }

}
