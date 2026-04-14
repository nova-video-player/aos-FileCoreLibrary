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
import com.archos.filecorelibrary.samba.SambaDiscovery;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;

import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.security.bc.BCSecurityProvider;
import com.hierynomus.protocol.transport.TransportException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.common.SMBRuntimeException;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.SMB2GuestSigningRequiredException;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;

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

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private SmbjUtils(Context context) {
        mContext = context;
        if (log.isDebugEnabled()) log.debug("SmbjUtils: initializing contexts");
        smbConfig = SmbConfig.builder()
                .withSecurityProvider(new BCSecurityProvider())
                .withMultiProtocolNegotiate(true)
                .withSigningRequired(false)
                .withReadBufferSize(1024 * 1024)
                .withWriteBufferSize(1024 * 1024)
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
            if (log.isTraceEnabled()) log.trace("getSmbConnection: smbConnection is null or not connected for {}, connecting to {}", uri, server);
            SMBClient smbClient;
            if (smbConfig != null) smbClient = new SMBClient(smbConfig);
            else smbClient = new SMBClient();
            String serverIP = SambaDiscovery.getIpFromShareName(server);
            if (serverIP == null)
                serverIP = JcifsUtils.getInstance(mContext).getBaseContextOnly(true).getNameServiceClient().getByName(server).getHostAddress();
            if (log.isTraceEnabled()) log.trace("getSmbConnection: {} -> {}", server, serverIP);
            smbConnection = (port != -1) ? smbClient.connect(serverIP, port) : smbClient.connect(serverIP);
            smbjConnections.put(cred, smbConnection);
            // check that auth information is valid
            if (username == null || password == null) {
                log.error("getSmbConnection: username or password is null for uri {}", uri);
                throw new IOException("Invalid credentials: username or password is null");
            }
            // need to regenerate smbSession in this case too
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), domain);
            if (ac == null) {
                log.error("getSmbConnection: AuthenticationContext is null for uri {}", uri);
                throw new IOException("Failed to create AuthenticationContext");
            }
            Session smbSession;
            try {
                smbSession = smbConnection.authenticate(ac);
            } catch (SMB2GuestSigningRequiredException e) {
                log.error("getSmbConnection: caught SMB2GuestSigningRequiredException {} for uri {} -> throwing IOException instead", e.getMessage(), uri);
                throw new IOException("getSmbConnection: SMB2GuestSigningRequiredException");
            }
            smbjSessions.put(cred, smbSession);
        }
    }

    /**
     * Reset cached share/session/connection for a URI and close existing resources.
     */
    public synchronized void resetConnection(Uri uri) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        DiskShare share = smbjShares.remove(cred);
        if (share != null) {
            try {
                share.close();
            } catch (Exception e) {
                log.warn("resetConnection: failed closing share for {}: {}", uri, e.getMessage());
            }
        }
        Session session = smbjSessions.remove(cred);
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("resetConnection: failed closing session for {}: {}", uri, e.getMessage());
            }
        }
        Connection connection = smbjConnections.remove(cred);
        if (connection != null) {
            try {
                connection.close(true);
            } catch (Exception e) {
                log.warn("resetConnection: failed closing connection for {}: {}", uri, e.getMessage());
            }
        }
    }

    private boolean isOutOfCredits(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SMBRuntimeException && current.getMessage() != null &&
                    current.getMessage().contains("Not enough credits")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTransportError(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof TransportException || current instanceof SocketException || current instanceof java.io.EOFException) {
                return true;
            }
            if (current instanceof SMBRuntimeException && current.getCause() != null) {
                if (current.getCause() instanceof TransportException || current.getCause() instanceof SocketException || current.getCause() instanceof java.io.EOFException) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Execute an SMBJ operation, resetting the connection and retrying once if the server reports
     * that we ran out of credits. Safe for all operations (including mutating ones).
     */
    public <T> T withRetry(Uri uri, CheckedSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception e) {
            if (isOutOfCredits(e)) {
                log.warn("withRetry: out of credits for {}, resetting connection and retrying", uri);
                resetConnection(uri);
                return supplier.get();
            }
            throw e;
        }
    }

    /**
     * Execute an SMBJ operation, resetting the connection and retrying once if the server reports
     * that we ran out of credits or if a transport error occurred.
     * ONLY use this for idempotent operations (directory listing, exists, etc.).
     */
    public <T> T withReadRetry(Uri uri, CheckedSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception e) {
            if (isRetryableReadError(e)) {
                log.warn("withReadRetry: error (out of credits or transport) for {}, resetting connection and retrying", uri);
                resetConnection(uri);
                return supplier.get();
            }
            throw e;
        }
    }

    public boolean isRetryableReadError(Throwable t) {
        if (isOutOfCredits(t) || isTransportError(t)) {
            return true;
        }
        Throwable current = t;
        while (current != null) {
            if (current instanceof SMBApiException) {
                NtStatus status = ((SMBApiException) current).getStatus();
                if (status == NtStatus.STATUS_INTERNAL_ERROR
                        || status == NtStatus.STATUS_FILE_CLOSED
                        || status == NtStatus.STATUS_CONNECTION_DISCONNECTED
                        || status == NtStatus.STATUS_CONNECTION_RESET
                        || status == NtStatus.STATUS_NETWORK_NAME_DELETED
                        || status == NtStatus.STATUS_NETWORK_SESSION_EXPIRED
                        || status == NtStatus.STATUS_USER_SESSION_DELETED) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }


    public synchronized DiskShare getSmbShare(Uri uri) throws IOException, SMBApiException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        String shareName = getShareName(uri);
        getSmbConnection(uri); // be sure to be connected
        // shareName can be null when asking for smbj://server/
        if (shareName == null) {
            log.warn("getSmbShare: returning null shareName for uri {}", uri);
            return null;
        }
        DiskShare smbShare = smbjShares.get(cred);
        if (smbShare == null || !shareName.equals(getShareName(Uri.parse(cred.getUriString()))) || !smbShare.isConnected()) {
            if (log.isTraceEnabled()) log.trace("getSmbShare: smbShare is null or not connected for {}", shareName);
            // ensures that there is a valid connection and regenerate session if not connected
            getSmbConnection(uri);
            Session smbSession = smbjSessions.get(cred);
            if (smbSession != null) {
                try {
                    smbShare = (DiskShare) smbSession.connectShare(shareName);
                    if (log.isTraceEnabled()) log.trace("getSmbShare: saving smbShare {}, smbshare={}", shareName, smbShare);
                    smbjShares.put(cred, smbShare);
                } catch (SMBRuntimeException e) {
                    if (e.getCause() instanceof SocketException) {
                        // Try to reconnect
                        getSmbConnection(uri);
                        smbSession = smbjSessions.get(cred);
                        if (smbSession != null) {
                            smbShare = (DiskShare) smbSession.connectShare(shareName);
                            smbjShares.put(cred, smbShare);
                        } else {
                            log.error("getSmbShare: caught SMBRuntimeException but smbSession is null after reconnecting! {} for uri {}, sharename={} -> throwing IOException instead", e.getMessage(), uri, shareName);
                            throw new IOException("getSmbShare: smbSession is null after reconnecting!");
                        }
                    } else {
                        log.error("getSmbShare: caught exception {} for uri {}, sharename={} -> throwing IOException instead", e.getMessage(), uri, shareName);
                        throw new IOException("getSmbShare: smbSession is null after reconnecting!");
                    }
                }
            } else log.warn("getSmbShare: smbSession is null!");
        }
        if (log.isDebugEnabled()) log.debug("getSmbShare: for uri {}, sharename={}, smbShare={}, isConnected={}", uri, shareName, smbShare, (smbShare != null)?smbShare.isConnected():"false");
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
        if (mContext == null) {
            log.error("isSMBjEnabled: mContext is null! Returning false");
            return false;
        }
        if (log.isTraceEnabled()) log.trace("isSMBjEnabled={}", PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbj", false));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbj", false);
    }

    /**
     * Invalidate cached DiskShare for a URI, forcing reconnection on next access.
     * Used when a protocol error (TransportException) indicates connection is compromised.
     */
    public synchronized void invalidateShare(Uri uri) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred != null) {
            DiskShare share = smbjShares.remove(cred);
            if (share != null) {
                if (log.isDebugEnabled()) log.debug("invalidateShare: removed cached share for {}", uri);
            }
        }
    }

}
