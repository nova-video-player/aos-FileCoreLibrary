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
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;

public class Session {
    private static final Logger log = LoggerFactory.getLogger(Session.class);
    private static Session sSession = null;
    private static HashMap<Credential, FTPSClient> ftpsClients;
    private static HashMap<Credential, FTPClient> ftpClients;

    public Session() {
        ftpClients = new HashMap<Credential, FTPClient>();
        ftpsClients = new HashMap<Credential, FTPSClient>();
    }

    public static Session getInstance() {
        if (sSession == null)
            sSession = new Session();
        return sSession;
    }

    public void removeFTPClient(Uri cred) {
        if (cred.getScheme().equals("ftps")) {
            for (Entry<Credential, FTPSClient> e : ftpsClients.entrySet())
                if ((e.getKey()).getUriString().equals(cred.toString())) {
                    if (e.getValue().isConnected()) {
                        try {
                            log.debug("removeFTPSClient: logout + disconnect ");
                            e.getValue().logout();
                            e.getValue().disconnect();
                        } catch (IOException ioe) {
                            log.error("removeFTPClient: caught IOException ", ioe);
                        }
                    }
                    ftpsClients.remove(e.getKey());
                }
        } else {
            for (Entry<Credential, FTPClient> e : ftpClients.entrySet())
                if ((e.getKey()).getUriString().equals(cred.toString())) {
                    if (e.getValue().isConnected()) {
                        try {
                            log.debug("removeFTPClient: logout + disconnect");
                            e.getValue().logout();
                            e.getValue().disconnect();
                        } catch (IOException ioe) {
                            log.error("removeFTPClient: caught IOException ", ioe);
                        }
                    }
                    ftpClients.remove(e.getKey());
                }
        }
    }

    public static void closeNewFTPSClient(FTPSClient ftp) {
        if (ftp == null) return;
        if (ftp.isConnected())
            try {
                log.debug("closeNewFTPSClient: logout + disconnect ");
                ftp.logout();
                ftp.disconnect();
            } catch (IOException ioe) {
                log.error("closeNewFTPSClient: caught IOException ", ioe);
            }
    }

    public static void closeNewFTPClient(FTPClient ftp) {
        if (ftp == null) return;
        if (ftp.isConnected())
            try {
                log.debug("closeNewFTPClient: logout + disconnect ");
                ftp.logout();
                ftp.disconnect();
            } catch (IOException ioe) {
                log.error("closeNewFTPClient: caught IOException ", ioe);
            }
    }

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
        ftp.setAutodetectUTF8(true); // must be done before connecting
        //ftp.setControlEncoding("UTF-8");
        //try to connect
        ftp.connect(path.getHost(), port);
        if (FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            log.debug("getNewFTPClient: connected to " + path);
            //enter passive mode
            ftp.enterLocalPassiveMode();
            // Send keepalive to preserve control channel every 5mn
            ftp.setControlKeepAliveTimeout(300);
            //login to 	server
            if (!ftp.login(username, password)) {
                log.debug("getNewFTPClient: failed to login now logout + disconnect");
                try {
                    ftp.logout();
                    ftp.disconnect();
                } catch (IOException e) {
                    log.error("getNewFTPClient: caught IOException during disconnect ", e);
                }
                throw new AuthenticationException();
            }
            if (mode >= 0) ftp.setFileType(mode);
            int reply = ftp.getReplyCode();
            //FTPReply stores a set of constants for FTP reply codes.
            if (!FTPReply.isPositiveCompletion(reply)) {
                log.debug("getNewFTPClient: cannot setFileType logout + disconnect");
                try {
                    ftp.disconnect();
                } catch (IOException e) {
                    log.error("getNewFTPClient: caught IOException disconnecting ", e);
                }
                return null;
            }
        }
        return ftp;
    }

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
        ftp.setAutodetectUTF8(true); // must be done before connecting
        //ftp.setControlEncoding("UTF-8");
        //try to connect
        ftp.connect(path.getHost(), port);
        if (FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            log.debug("getNewFTPSClient: connected to " + path);
            //enter passive mode
            ftp.enterLocalPassiveMode();
            // Set protection buffer size
            ftp.execPBSZ(0);
            // Set data channel protection to private
            ftp.execPROT("P");
            // Send keepalive to preserve control channel every 5mn
            ftp.setControlKeepAliveTimeout(300);
            ftp.setControlEncoding("UTF-8");
            //login to 	server
            if (!ftp.login(username, password)) {
                log.debug("getNewFTPSClient: failed to login now logout + disconnect");
                ftp.logout();
                if (ftp.isConnected()) {
                    try {
                        ftp.disconnect();
                    } catch (IOException ioe) {
                        log.error("getNewFTPSClient: caught IOException during disconnect ", ioe);
                        Log.e("tag", "getNewFTPSClient: caught IOException during disconnect ", ioe);
                    }
                }
                throw new AuthenticationException();
            }
            if (mode >= 0) ftp.setFileType(mode);

            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                log.debug("getNewFTPSClient: cannot setFileType logout + disconnect");
                try {
                    ftp.disconnect();
                } catch (IOException e) {
                    log.error("getNewFTPSClient: caught IOException disconnecting ", e);
                    Log.e("session", "getNewFTPSClient: caught IOException disconnecting ", e);
                }
                return null;
            }
        } else {
            ftp.disconnect();
        }
        log.debug("getNewFTPSClient: all went well, returning ftpsClient");
        return ftp;
    }

    // Note that ftpClient is not thread safe thus reusing is not really an option here
    public FTPClient getFTPClient(Uri uri) throws SocketException, IOException, AuthenticationException {
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(uri.toString());
        if (cred == null)
            cred = new Credential("anonymous", "", buildKeyFromUri(uri).toString(),"",true);
        FTPClient ftpclient = ftpClients.get(cred);
        if (ftpclient != null && ftpclient.isConnected()) {
            log.debug("getFTPClient: reusing ftp session for " + uri);
            return ftpclient;
        }
        FTPClient ftp = getNewFTPClient(uri, FTP.BINARY_FILE_TYPE);
        // Not previous session found, open a new one
        log.debug("getFTPClient: create new ftp session for " + uri);
        if (ftp == null) return null;
        Uri key = buildKeyFromUri(uri);
        log.debug("getFTPClient: new ftp session created with key " + key);
        ftpClients.put(cred, ftp);
        return ftp;
    }

    // Note that ftpsClient is not thread safe thus reusing is not really an option here
    public FTPSClient getFTPSClient(Uri uri) throws SocketException, IOException, AuthenticationException{
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(uri.toString());
        if (cred == null)
            cred = new Credential("anonymous","", buildKeyFromUri(uri).toString(),"",true);
        FTPSClient ftpclient = ftpsClients.get(cred);
        if (ftpclient!=null && ftpclient.isConnected()) {
            log.debug("getFTPSClient: reusing ftp session for " + uri);
            return ftpclient;
        }
        // Not previous session found, open a new one
        log.debug("getFTPSClient: create new ftp session for "+uri);
        FTPSClient ftp = getNewFTPSClient(uri, FTP.BINARY_FILE_TYPE);
        if (ftp == null) return null;
        Uri key = buildKeyFromUri(uri);
        log.debug("getFTPSClient: new ftp session created with key " + key);
        ftpsClients.put(cred, ftp);
        return ftp;
    }

    private Uri buildKeyFromUri(Uri uri) {
        // We use the Uri without the path segment as key: for example, "ftp://blabla.com:21/toto/titi" gives a "ftp://blabla.com:21" key
        return uri.buildUpon().path("").build();
    }
}