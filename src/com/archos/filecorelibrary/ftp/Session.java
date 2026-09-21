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

    public Session() {
    }

    public static synchronized Session getInstance() {
        if (sSession == null)
            sSession = new Session();
        return sSession;
    }

    /**
     * Kept for compatibility with external callers (Video and MediaLib modules)
     * which call removeFTPClient on authentication failure or session reset.
     */
    public synchronized void removeFTPClient(Uri cred) {
        if (cred == null || cred.getHost() == null) return;
        if (log.isDebugEnabled()) log.debug("removeFTPClient: client reset requested for {}", cred);
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

    private void setupClientLogging(FTPClient ftp) {
        if (log.isTraceEnabled()) {
            ftp.addProtocolCommandListener(new org.apache.commons.net.ProtocolCommandListener() {
                @Override
                public void protocolCommandSent(org.apache.commons.net.ProtocolCommandEvent event) {
                    if (log.isTraceEnabled()) {
                        String msg = "PASS".equalsIgnoreCase(event.getCommand()) ? "******" : event.getMessage();
                        log.trace("FTP command: {}", msg != null ? msg.trim() : "");
                    }
                }

                @Override
                public void protocolReplyReceived(org.apache.commons.net.ProtocolCommandEvent event) {
                    if (log.isTraceEnabled()) {
                        log.trace("FTP reply: {} {}", event.getReplyCode(), event.getMessage() != null ? event.getMessage().trim() : "");
                    }
                }
            });
        }
    }

    private FTPClient connectNewFTPClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException {
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
        setupClientLogging(ftp);
        ftp.setDefaultTimeout(CONNECT_TIMEOUT_MS);
        ftp.setConnectTimeout(CONNECT_TIMEOUT_MS);
        ftp.setAutodetectUTF8(true); // must be done before connecting
        ftp.setStrictMultilineParsing(true); // enforce matching reply codes on multiline replies
        // Default to UNIX parser to avoid unhandled SYST responses (e.g. "End of list")
        ftp.configure(new org.apache.commons.net.ftp.FTPClientConfig(org.apache.commons.net.ftp.FTPClientConfig.SYST_UNIX));
        ftp.setBufferSize(BUFFER_SIZE);
        ftp.setReceieveDataSocketBufferSize(DATA_SOCKET_RECEIVE_BUFFER_SIZE);
        ftp.setSendDataSocketBufferSize(DATA_SOCKET_SEND_BUFFER_SIZE);

        // try to connect
        ftp.connect(path.getHost(), port);
        if (FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            if (log.isDebugEnabled()) log.debug("connectNewFTPClient: connected to {}", path);
            // enter passive mode
            ftp.enterLocalPassiveMode();
            // Send keepalive to preserve control channel every 5mn
            ftp.setControlKeepAliveTimeout(300);
            // login to server
            if (!ftp.login(username, password)) {
                if (log.isDebugEnabled()) log.debug("connectNewFTPClient: failed to login now logout + disconnect");
                close(ftp);
                throw new AuthenticationException();
            }
            if (mode >= 0) ftp.setFileType(mode);
            int reply = ftp.getReplyCode();
            // FTPReply stores a set of constants for FTP reply codes.
            if (!FTPReply.isPositiveCompletion(reply)) {
                if (log.isDebugEnabled()) log.debug("connectNewFTPClient: cannot setFileType logout + disconnect");
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
    public FTPClient getNewFTPClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException {
        try {
            return connectNewFTPClient(path, mode);
        } catch (AuthenticationException e) {
            // Never retry on authentication failure
            throw e;
        } catch (IOException e) {
            log.warn("getNewFTPClient: connection to {} failed ({}), retrying once", path, e.getMessage());
            try {
                return connectNewFTPClient(path, mode);
            } catch (AuthenticationException ae) {
                throw ae;
            } catch (IOException retryEx) {
                log.error("getNewFTPClient: retry connection to {} failed: {}", path, retryEx.getMessage());
                throw retryEx;
            }
        }
    }

    private FTPSClient connectNewFTPSClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException {
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
        setupClientLogging(ftp);
        ftp.setDefaultTimeout(CONNECT_TIMEOUT_MS);
        ftp.setConnectTimeout(CONNECT_TIMEOUT_MS);
        ftp.setAutodetectUTF8(true); // must be done before connecting
        ftp.setStrictMultilineParsing(true); // enforce matching reply codes on multiline replies
        // Default to UNIX parser to avoid unhandled SYST responses (e.g. "End of list")
        ftp.configure(new org.apache.commons.net.ftp.FTPClientConfig(org.apache.commons.net.ftp.FTPClientConfig.SYST_UNIX));
        ftp.setBufferSize(BUFFER_SIZE);
        ftp.setReceieveDataSocketBufferSize(DATA_SOCKET_RECEIVE_BUFFER_SIZE);
        ftp.setSendDataSocketBufferSize(DATA_SOCKET_SEND_BUFFER_SIZE);

        // try to connect
        ftp.connect(path.getHost(), port);
        if (FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
            if (log.isDebugEnabled()) log.debug("connectNewFTPSClient: connected to {}", path);
            // enter passive mode
            ftp.enterLocalPassiveMode();
            // Set protection buffer size
            ftp.execPBSZ(0);
            // Set data channel protection to private
            ftp.execPROT("P");
            // Send keepalive to preserve control channel every 5mn
            ftp.setControlKeepAliveTimeout(300);
            ftp.setControlEncoding("UTF-8");
            // login to server
            if (!ftp.login(username, password)) {
                if (log.isDebugEnabled()) log.debug("connectNewFTPSClient: failed to login now logout + disconnect");
                close(ftp);
                throw new AuthenticationException();
            }
            if (mode >= 0) ftp.setFileType(mode);

            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                if (log.isDebugEnabled()) log.debug("connectNewFTPSClient: cannot setFileType logout + disconnect");
                close(ftp);
                return null;
            }
        } else {
            close(ftp);
            return null;
        }
        if (log.isDebugEnabled()) log.debug("connectNewFTPSClient: all went well, returning ftpsClient");
        return ftp;
    }

    @SuppressWarnings("deprecation") // setControlKeepAliveTimeout(long): preserves API 23 compatibility (Duration is API 26+)
    public FTPSClient getNewFTPSClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException {
        try {
            return connectNewFTPSClient(path, mode);
        } catch (AuthenticationException e) {
            // Never retry on authentication failure
            throw e;
        } catch (IOException e) {
            log.warn("getNewFTPSClient: connection to {} failed ({}), retrying once", path, e.getMessage());
            try {
                return connectNewFTPSClient(path, mode);
            } catch (AuthenticationException ae) {
                throw ae;
            } catch (IOException retryEx) {
                log.error("getNewFTPSClient: retry connection to {} failed: {}", path, retryEx.getMessage());
                throw retryEx;
            }
        }
    }
}
