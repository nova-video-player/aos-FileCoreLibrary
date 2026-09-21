// Copyright 2017 Archos SA
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

package com.archos.filecorelibrary.ftp;

import java.io.IOException;
import java.net.SocketException;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.net.Uri;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.archos.filecorelibrary.AuthenticationException;

public class Session {
    private static final Logger log = LoggerFactory.getLogger(Session.class);

    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB
    private static final int DATA_SOCKET_RECEIVE_BUFFER_SIZE = 256 * 1024; // 256 KB
    private static final int DATA_SOCKET_SEND_BUFFER_SIZE = 64 * 1024; // 64 KB
    private static final int CONNECT_TIMEOUT_MS = 15000; // 15 seconds

    private static Session sSession = null;
    private static ConcurrentHashMap<Credential, FTPSClient> ftpsClients;
    private static ConcurrentHashMap<Credential, FTPClient> ftpClients;

    public Session() {
        ftpClients = new ConcurrentHashMap<Credential, FTPClient>();
        ftpsClients = new ConcurrentHashMap<Credential, FTPSClient>();
    }

    public static synchronized Session getInstance() {
        if (sSession == null)
            sSession = new Session();
        return sSession;
    }

    public synchronized void removeFTPClient(Uri cred) {
        if (cred == null || cred.getHost() == null) return;
        int port = cred.getPort() < 0 ? 21 : cred.getPort();
        if (log.isDebugEnabled()) log.debug("removeFTPClient: removing client(s) for {}", cred);
        if ("ftps".equals(cred.getScheme())) {
            Iterator<Entry<Credential, FTPSClient>> it = ftpsClients.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Credential, FTPSClient> e = it.next();
                Uri uri = Uri.parse(e.getKey().getUriString());
                int p = uri.getPort() < 0 ? 21 : uri.getPort();
                if (cred.getHost().equalsIgnoreCase(uri.getHost()) && port == p) {
                    close(e.getValue());
                    it.remove();
                }
            }
        } else {
            Iterator<Entry<Credential, FTPClient>> it = ftpClients.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Credential, FTPClient> e = it.next();
                Uri uri = Uri.parse(e.getKey().getUriString());
                int p = uri.getPort() < 0 ? 21 : uri.getPort();
                if (cred.getHost().equalsIgnoreCase(uri.getHost()) && port == p) {
                    close(e.getValue());
                    it.remove();
                }
            }
        }
    }

    public static void close(FTPClient ftp) {
        if (ftp == null) return;
        if (ftp.isConnected()) {
            try {
                if (log.isDebugEnabled()) log.debug("close: logout");
                ftp.logout();
            } catch (Exception e) {
                log.warn("close: caught Exception during logout: {}", e.getMessage());
            } finally {
                try {
                    if (log.isDebugEnabled()) log.debug("close: disconnect");
                    ftp.disconnect();
                } catch (Exception e) {
                    log.warn("close: caught Exception during disconnect: {}", e.getMessage());
                }
            }
        }
    }

    public static void closeNewFTPSClient(FTPSClient ftp) {
        close(ftp);
    }

    public static void closeNewFTPClient(FTPClient ftp) {
        close(ftp);
    }

    @SuppressWarnings("deprecation") // setControlKeepAliveTimeout(long): preserves API 23 compatibility (Duration is API 26+)
    public FTPClient getNewFTPClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException {
        // Use default port if not set
        int port = path.getPort();
        if (port < 0) port = 21; // default port

        String username = "anonymous"; // default user
        String password = ""; // default password
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(path.toString());
        if (cred != null) {
            password = cred.getPassword();
            username = cred.getUsername();
        }

        FTPClient ftp = new FTPClient();
        ftp.setDefaultTimeout(CONNECT_TIMEOUT_MS);
        ftp.setConnectTimeout(CONNECT_TIMEOUT_MS);
        ftp.setAutodetectUTF8(true); // must be done before connecting
        ftp.setBufferSize(BUFFER_SIZE);
        ftp.setReceieveDataSocketBufferSize(DATA_SOCKET_RECEIVE_BUFFER_SIZE);
        ftp.setSendDataSocketBufferSize(DATA_SOCKET_SEND_BUFFER_SIZE);

        //try to connect
        ftp.connect(path.getHost(), port);
        if (FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            if (log.isDebugEnabled()) log.debug("getNewFTPClient: connected to {}", path);
            //enter passive mode
            ftp.enterLocalPassiveMode();
            // Send keepalive to preserve control channel every 5mn
            ftp.setControlKeepAliveTimeout(300);
            //login to server
            if (!ftp.login(username, password)) {
                if (log.isDebugEnabled()) log.debug("getNewFTPClient: failed to login now logout + disconnect");
                close(ftp);
                throw new AuthenticationException();
            }
            if (mode >= 0) ftp.setFileType(mode);
            int reply = ftp.getReplyCode();
            //FTPReply stores a set of constants for FTP reply codes.
            if (!FTPReply.isPositiveCompletion(reply)) {
                if (log.isDebugEnabled()) log.debug("getNewFTPClient: cannot setFileType logout + disconnect");
                close(ftp);
                return null;
            }
        } else {
            close(ftp);
            return null;
        }
        return ftp;
    }

    @SuppressWarnings("deprecation") // setControlKeepAliveTimeout(long): preserves API 23 compatibility (Duration is API 26+)
    public FTPSClient getNewFTPSClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException {
        // Use default port if not set
        int port = path.getPort();
        if (port < 0) port = 21; // default port

        String username = "anonymous"; // default user
        String password = ""; // default password
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(path.toString());
        if (cred != null) {
            password = cred.getPassword();
            username = cred.getUsername();
        }

        FTPSClient ftp = new FTPSClient("TLS", false);
        ftp.setDefaultTimeout(CONNECT_TIMEOUT_MS);
        ftp.setConnectTimeout(CONNECT_TIMEOUT_MS);
        ftp.setAutodetectUTF8(true); // must be done before connecting
        ftp.setBufferSize(BUFFER_SIZE);
        ftp.setReceieveDataSocketBufferSize(DATA_SOCKET_RECEIVE_BUFFER_SIZE);
        ftp.setSendDataSocketBufferSize(DATA_SOCKET_SEND_BUFFER_SIZE);

        //try to connect
        ftp.connect(path.getHost(), port);
        if (FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            if (log.isDebugEnabled()) log.debug("getNewFTPSClient: connected to {}", path);
            //enter passive mode
            ftp.enterLocalPassiveMode();
            // Set protection buffer size
            ftp.execPBSZ(0);
            // Set data channel protection to private
            ftp.execPROT("P");
            // Send keepalive to preserve control channel every 5mn
            ftp.setControlKeepAliveTimeout(300);
            ftp.setControlEncoding("UTF-8");
            //login to server
            if (!ftp.login(username, password)) {
                if (log.isDebugEnabled()) log.debug("getNewFTPSClient: failed to login now logout + disconnect");
                close(ftp);
                throw new AuthenticationException();
            }
            if (mode >= 0) ftp.setFileType(mode);

            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                if (log.isDebugEnabled()) log.debug("getNewFTPSClient: cannot setFileType logout + disconnect");
                close(ftp);
                return null;
            }
        } else {
            close(ftp);
            return null;
        }
        if (log.isDebugEnabled()) log.debug("getNewFTPSClient: all went well, returning ftpsClient");
        return ftp;
    }

    // Note that ftpClient is not thread safe thus reusing is not really an option here
    public synchronized FTPClient getFTPClient(Uri uri) throws SocketException, IOException, AuthenticationException {
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(uri.toString());
        if (cred == null)
            cred = new Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        FTPClient ftpclient = ftpClients.get(cred);
        if (ftpclient != null && ftpclient.isConnected()) {
            if (log.isDebugEnabled()) log.debug("getFTPClient: reusing ftp session for {}", uri);
            return ftpclient;
        }
        FTPClient ftp = getNewFTPClient(uri, FTP.BINARY_FILE_TYPE);
        // No previous session found, open a new one
        if (log.isDebugEnabled()) log.debug("getFTPClient: create new ftp session for {}", uri);
        if (ftp == null) return null;
        Uri key = buildKeyFromUri(uri);
        if (log.isDebugEnabled()) log.debug("getFTPClient: new ftp session created with key {}", key);
        ftpClients.put(cred, ftp);
        return ftp;
    }

    // Note that ftpsClient is not thread safe thus reusing is not really an option here
    public synchronized FTPSClient getFTPSClient(Uri uri) throws SocketException, IOException, AuthenticationException {
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(uri.toString());
        if (cred == null)
            cred = new Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        FTPSClient ftpclient = ftpsClients.get(cred);
        if (ftpclient != null && ftpclient.isConnected()) {
            if (log.isDebugEnabled()) log.debug("getFTPSClient: reusing ftp session for {}", uri);
            return ftpclient;
        }
        // No previous session found, open a new one
        if (log.isDebugEnabled()) log.debug("getFTPSClient: create new ftp session for {}", uri);
        FTPSClient ftp = getNewFTPSClient(uri, FTP.BINARY_FILE_TYPE);
        if (ftp == null) return null;
        Uri key = buildKeyFromUri(uri);
        if (log.isDebugEnabled()) log.debug("getFTPSClient: new ftp session created with key {}", key);
        ftpsClients.put(cred, ftp);
        return ftp;
    }

    private Uri buildKeyFromUri(Uri uri) {
        // We use the Uri without the path segment as key: for example, "ftp://blabla.com:21/toto/titi" gives a "ftp://blabla.com:21" key
        return uri.buildUpon().path("").build();
    }
}
