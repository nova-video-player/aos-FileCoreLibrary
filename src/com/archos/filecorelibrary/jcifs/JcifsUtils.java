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

package com.archos.filecorelibrary.jcifs;

import static com.archos.filecorelibrary.jcifs.NovaSmbFile.getIpUriString;

import android.content.Context;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jcifs.CIFSException;
import jcifs.CIFSContext;
import jcifs.SmbTransport;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbTransportInternal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;


public class JcifsUtils {

    private static final Logger log = LoggerFactory.getLogger(JcifsUtils.class);

    // Kept for source compatibility. Protocol selection now uses exclusive transport negotiation
    // instead of the old listFiles()-based strict negotiation path.
    @Deprecated
    public final static boolean LIMIT_PROTOCOL_NEGO = false;
    public final static boolean RESOLUTION_CACHE_INJECTION = false;
    @Deprecated
    public final static boolean PREVENT_MULTIPLE_TIME_SERVER_PROBING = true;

    // SMB2 multi-credit transport buffer size (1 MiB) for large read/write operations
    private static final int SMB2_TRANSPORT_BUFFER_SIZE = 1048576;
    private static final long COMPATIBILITY_MODE_CACHE_MS = 5 * 60 * 1000L;
    private static final long UNKNOWN_PROTOCOL_CACHE_MS = 30 * 1000L;

    private static Properties prop = null;
    private static CIFSContext baseContextSmb1, baseContextSmb2, baseContextSmb1Only, baseContextSmb2Only;

    public enum SmbProtocolMode {
        SMB2_OR_LATER,
        SMB2_COMPATIBILITY,
        SMB1,
        SMB1_COMPATIBILITY,
        UNKNOWN
    }

    private static final class SmbEndpoint {
        private static final int DEFAULT_SMB_PORT = 445;

        final String server;
        final int port;

        SmbEndpoint(String server, int port) {
            this.server = server.toLowerCase(Locale.ROOT);
            this.port = port > 0 ? port : DEFAULT_SMB_PORT;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SmbEndpoint)) return false;
            SmbEndpoint endpoint = (SmbEndpoint) other;
            return port == endpoint.port && server.equals(endpoint.server);
        }

        @Override
        public int hashCode() {
            return 31 * server.hashCode() + port;
        }

        @Override
        public String toString() {
            return server + ':' + port;
        }
    }

    private static final ConcurrentHashMap<SmbEndpoint, SmbProtocolMode> serverProtocolModes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SmbEndpoint, Long> serverProtocolModeExpirations = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SmbEndpoint, FutureTask<SmbProtocolMode>> serverProtocolProbes = new ConcurrentHashMap<>();

    private static Context mContext;

    private static volatile boolean prefChanged = false;

    // singleton, volatile to make double-checked-locking work correctly
    private static volatile JcifsUtils sInstance;

    // get the instance, context is used for initial context injection
    public static JcifsUtils getInstance(Context context) {
        if (context == null) log.warn("getInstance: context passed is null!!!");
        else if (mContext == null) mContext = context;
        if (sInstance == null) {
            synchronized(JcifsUtils.class) {
                if (sInstance == null) sInstance = new JcifsUtils(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    /** may return null but no Context required */
    public static JcifsUtils peekInstance() {
        return sInstance;
    }

    private JcifsUtils(Context context) {
        mContext = context;
        if (log.isDebugEnabled()) log.debug("JcifsUtils: initializing contexts");
        reCreateAllContexts();
    }

    public static void reCreateAllContexts() {
        if (log.isDebugEnabled()) log.debug("JcifsUtils: reCreateAllContexts");
        baseContextSmb1 = createContext(false);
        baseContextSmb2 = createContext(true);
        baseContextSmb1Only = createContextOnly(false);
        baseContextSmb2Only = createContextOnly(true);
        clearServerProtocolCache();
    }

    private static CIFSContext createContext(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        // reduce network calls for attributes
        prop.put("jcifs.smb.client.attrExpirationPeriod", "30000"); // 30 seconds

        if (isSmb2) {
            prop.put("jcifs.smb.client.maxVersion", "SMB311");
            prop.put("jcifs.smb.client.snd_buf_size", String.valueOf(SMB2_TRANSPORT_BUFFER_SIZE));
            prop.put("jcifs.smb.client.rcv_buf_size", String.valueOf(SMB2_TRANSPORT_BUFFER_SIZE));
        } else {
            prop.put("jcifs.smb.client.maxVersion", "SMB1");
        }

        // must remain false to be able to talk to smbV1 only
        prop.put("jcifs.smb.client.useSMB2Negotiation", "false");
        if (isSmb2) prop.put("jcifs.smb.client.minVersion", "SMB1");
        // resolve in this order to avoid netbios name being also a foreign DNS entry resulting in bad resolution
        // do not change resolveOrder for now
        // with jcifs-old, resolveOrder was not changed i.e. LMHOSTS,DNS,WINS,BCAST, jcifs-ng author recommends no change
        if (isResolverBcastFirst()) {
            if (log.isDebugEnabled()) log.debug("createContext: resolver set to BCAST,DNS");
            prop.put("jcifs.resolveOrder", "BCAST,DNS");
        }
        // get around https://github.com/AgNO3/jcifs-ng/issues/40 and this is required for guest login on win10 smb2
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");
        // disable dfs makes win10 shares with ms account work
        prop.put("jcifs.smb.client.dfs.disabled", "true");

        // needed for Huawei router https://github.com/AgNO3/jcifs-ng/issues/225 using SMB1
        // see also https://github.com/AgNO3/jcifs-ng/issues/226, not clear it does not interfere with SMB2 only servers
        prop.put("jcifs.smb.useRawNTLM", "true");

        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            //Log.e(TAG, "CIFSException: ", e);
            log.warn("createContext: CIFSException caught PropertyConfiguration");
        }
        return new BaseContext(propertyConfiguration);
    }

    private static CIFSContext createContextOnly(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        // reduce network calls for attributes
        prop.put("jcifs.smb.client.attrExpirationPeriod", "30000"); // 30 seconds

        if (isSmb2) {
            prop.put("jcifs.smb.client.maxVersion", "SMB311");
            prop.put("jcifs.smb.client.minVersion", "SMB202");
            // note that connectivity with smbV1 will not be working
            prop.put("jcifs.smb.client.useSMB2Negotiation", "true");
            // disable dfs makes win10 shares with ms account work
            prop.put("jcifs.smb.client.dfs.disabled", "true");
            // enable multi-credit large I/O for SMB2
            prop.put("jcifs.smb.client.snd_buf_size", String.valueOf(SMB2_TRANSPORT_BUFFER_SIZE));
            prop.put("jcifs.smb.client.rcv_buf_size", String.valueOf(SMB2_TRANSPORT_BUFFER_SIZE));
        } else {
            prop.put("jcifs.smb.client.maxVersion", "SMB1");
            prop.put("jcifs.smb.client.minVersion", "SMB1");
            prop.put("jcifs.smb.client.useSMB2Negotiation", "false");
            // see https://github.com/AgNO3/jcifs-ng/issues/226
            prop.put("jcifs.smb.useRawNTLM", "true");
        }

        // get around https://github.com/AgNO3/jcifs-ng/issues/40 and this is required for guest login on win10 smb2
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");

        // resolve in this order to avoid netbios name being also a foreign DNS entry resulting in bad resolution
        // do not change resolveOrder for now
        // with jcifs-old, resolveOrder was not changed i.e. LMHOSTS,DNS,WINS,BCAST, jcifs-ng author recommends no change
        if (isResolverBcastFirst()) {
            if (log.isDebugEnabled()) log.debug("createContext: resolver set to BCAST,DNS");
            prop.put("jcifs.resolveOrder", "BCAST,DNS");
        }
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");

        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            //Log.e(TAG, "CIFSException: ", e);
            log.warn("CIFSException caught PropertyConfiguration");
        }
        return new BaseContext(propertyConfiguration);
    }

    public static CIFSContext getBaseContext(boolean isSmb2) {
        //return isSmb2 ? baseContextSmb2 : baseContextSmb1;
        checkPrefChange();
        if (isSmb2) {
            if (baseContextSmb2 == null) baseContextSmb2 = createContext(true);
            return baseContextSmb2;
        } else {
            if (baseContextSmb1 == null) baseContextSmb1 = createContext(false);
            return baseContextSmb1;
        }
    }

    public static CIFSContext getBaseContextOnly(boolean isSmb2) {
        //return isSmb2 ? baseContextSmb2Only : baseContextSmb1Only;
        checkPrefChange();
        if (isSmb2) {
            if (baseContextSmb2Only == null) baseContextSmb2Only = createContextOnly(true);
            return baseContextSmb2Only;
        } else {
            if (baseContextSmb1Only == null) baseContextSmb1Only = createContextOnly(false);
            return baseContextSmb1Only;
        }
    }

    public static void notifyPrefChange() {
        if (log.isDebugEnabled()) log.debug("notifyPrefChange: preference changed");
        prefChanged = true;
        clearServerProtocolCache();
    }

    private static void checkPrefChange() {
        if (prefChanged) {
            if (log.isDebugEnabled()) log.debug("JcifsUtils: reCreateAllContexts after preference change");
            prefChanged = false;
            reCreateAllContexts();
        }
    }

    public static void clearServerProtocolCache() {
        serverProtocolModes.clear();
        serverProtocolModeExpirations.clear();
        serverProtocolProbes.clear();
    }

    /** @deprecated protocol modes are now populated from transport negotiation. */
    @Deprecated
    public static void declareServerSmbV2(String server, boolean isSmbV2) {
        if (server == null || server.isEmpty()) return;
        SmbProtocolMode mode = isSmbV2 ? SmbProtocolMode.SMB2_OR_LATER : SmbProtocolMode.SMB1;
        SmbEndpoint endpoint = new SmbEndpoint(server, -1);
        serverProtocolModes.put(endpoint, mode);
        serverProtocolModeExpirations.remove(endpoint);
    }

    /** @deprecated probes are coalesced internally per server and port. */
    @Deprecated
    public static void declareServerBeingProbed(String server, boolean probed) {
        // No-op retained for source compatibility.
    }

    private static CIFSContext getCifsContext(Uri uri, Boolean isSmbV2) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        CIFSContext context = null;
        if (cred != null && ! cred.getUsername().isEmpty()) {
            if (log.isDebugEnabled()) log.debug("getCifsContext using credentials for {}", uri);
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(cred.getDomain(), cred.getUsername(), cred.getPassword());
            context = getBaseContext(isSmbV2).withCredentials(auth);
        } else {
            if (log.isDebugEnabled()) log.debug("getCifsContext using NO credentials for {}", uri);
            context = getBaseContext(isSmbV2).withGuestCrendentials();
        }
        return context;
    }

    private static CIFSContext getCifsContextOnly(Uri uri, boolean isSmbV2) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        CIFSContext context = null;
        if (cred != null && !cred.getUsername().isEmpty()) {
            if (log.isDebugEnabled()) log.debug("getCifsContextOnly using credentials for {}", uri);
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator(cred.getDomain(), cred.getUsername(), cred.getPassword());
            context = getBaseContextOnly(isSmbV2).withCredentials(auth);
        } else {
            if (log.isDebugEnabled()) log.debug("getCifsContextOnly using NO credentials for {}", uri);
            context = getBaseContextOnly(isSmbV2).withGuestCrendentials();
        }
        return context;
    }

    private static SmbEndpoint getEndpoint(Uri uri) throws MalformedURLException {
        Uri resolvedUri = Uri.parse(getIpUriString(uri));
        String server = resolvedUri.getHost();
        if (server == null || server.isEmpty()) {
            throw new MalformedURLException("SMB URI has no server: " + uri);
        }
        return new SmbEndpoint(server, resolvedUri.getPort());
    }

    private static boolean probeTransport(CIFSContext context, SmbEndpoint endpoint) throws IOException {
        try (SmbTransport transport = context.getTransportPool().getSmbTransport(
                context, endpoint.server, endpoint.port, true, false)) {
            SmbTransportInternal internal = transport.unwrap(SmbTransportInternal.class);
            internal.ensureConnected();
            return internal.isSMB2();
        }
    }

    private static SmbProtocolMode probeServerProtocol(SmbEndpoint endpoint) {
        IOException mixedFailure;
        try {
            boolean isSmb2 = probeTransport(getBaseContext(true), endpoint);
            return isSmb2 ? SmbProtocolMode.SMB2_OR_LATER : SmbProtocolMode.SMB1;
        } catch (IOException e) {
            mixedFailure = e;
            if (log.isDebugEnabled()) log.debug("Mixed SMB negotiation failed for {}", endpoint, e);
        }

        // Some non-compliant servers reject the SMB1 multi-protocol negotiate packet.
        // Check direct SMB2 before falling back to direct SMB1 so a transient mixed-mode
        // failure cannot immediately classify an SMB2-capable endpoint as SMB1.
        try {
            if (probeTransport(getBaseContextOnly(true), endpoint)) {
                return SmbProtocolMode.SMB2_COMPATIBILITY;
            }
        } catch (IOException e) {
            if (log.isDebugEnabled()) log.debug("SMB2-only negotiation failed for {}", endpoint, e);
        }

        try {
            if (!probeTransport(getBaseContextOnly(false), endpoint)) {
                return SmbProtocolMode.SMB1_COMPATIBILITY;
            }
        } catch (IOException e) {
            if (log.isTraceEnabled()) log.warn("Unable to determine SMB dialect for {}", endpoint, mixedFailure);
            else log.warn("Unable to determine SMB dialect for {}", endpoint);
            return SmbProtocolMode.UNKNOWN;
        }

        return SmbProtocolMode.UNKNOWN;
    }

    private static SmbProtocolMode getServerProtocolMode(SmbEndpoint endpoint) {
        SmbProtocolMode cached = serverProtocolModes.get(endpoint);
        if (cached != null) {
            Long expiration = serverProtocolModeExpirations.get(endpoint);
            if (expiration == null || System.currentTimeMillis() < expiration) {
                return cached;
            }
            serverProtocolModes.remove(endpoint, cached);
            serverProtocolModeExpirations.remove(endpoint, expiration);
        }

        FutureTask<SmbProtocolMode> newProbe = new FutureTask<>(() -> probeServerProtocol(endpoint));
        FutureTask<SmbProtocolMode> probe = serverProtocolProbes.putIfAbsent(endpoint, newProbe);
        if (probe == null) {
            probe = newProbe;
            probe.run();
        }

        try {
            SmbProtocolMode result = probe.get();
            serverProtocolModes.put(endpoint, result);
            if (result == SmbProtocolMode.SMB1_COMPATIBILITY || result == SmbProtocolMode.SMB2_COMPATIBILITY) {
                // A compatibility result follows a failed mixed negotiation. Recheck it
                // periodically instead of making a transient failure permanent.
                serverProtocolModeExpirations.put(endpoint,
                        System.currentTimeMillis() + COMPATIBILITY_MODE_CACHE_MS);
            } else if (result == SmbProtocolMode.UNKNOWN) {
                // Prevent sequential file operations from immediately repeating three
                // connection attempts against an unavailable endpoint.
                serverProtocolModeExpirations.put(endpoint,
                        System.currentTimeMillis() + UNKNOWN_PROTOCOL_CACHE_MS);
            } else {
                serverProtocolModeExpirations.remove(endpoint);
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SmbProtocolMode.UNKNOWN;
        } catch (ExecutionException e) {
            if (log.isTraceEnabled()) log.warn("SMB protocol probe failed for {}", endpoint, e.getCause());
            else log.warn("SMB protocol probe failed for {}", endpoint);
            return SmbProtocolMode.UNKNOWN;
        } finally {
            serverProtocolProbes.remove(endpoint, probe);
        }
    }

    public static SmbProtocolMode getServerProtocolMode(Uri uri) throws MalformedURLException {
        return getServerProtocolMode(getEndpoint(uri));
    }

    // Compatibility API: true means SMB2+, false means SMB1, null means unknown.
    public static Boolean isServerSmbV2(String server, int port) throws MalformedURLException {
        if (server == null || server.isEmpty()) {
            throw new MalformedURLException("SMB server is empty");
        }
        SmbProtocolMode mode = getServerProtocolMode(new SmbEndpoint(server, port));
        if (mode == SmbProtocolMode.SMB2_OR_LATER || mode == SmbProtocolMode.SMB2_COMPATIBILITY) return true;
        if (mode == SmbProtocolMode.SMB1 || mode == SmbProtocolMode.SMB1_COMPATIBILITY) return false;
        return null;
    }

    public static NovaSmbFile getSmbFile(Uri uri) throws MalformedURLException {
        if (!isSMBv2Enabled()) {
            return new NovaSmbFile(uri, getCifsContextOnly(uri, false));
        }
        return getSmbFileStrictNego(uri);
    }

    public static NovaSmbFile getSmbFileStrictNego(Uri uri) throws MalformedURLException {
        SmbProtocolMode mode = getServerProtocolMode(uri);
        CIFSContext context;
        if (mode == SmbProtocolMode.SMB2_OR_LATER) {
            // Keep multi-protocol negotiation for normal SMB2 servers. Besides being
            // the established path, this preserves server-root share enumeration.
            context = getCifsContext(uri, true);
        } else if (mode == SmbProtocolMode.SMB2_COMPATIBILITY) {
            context = getCifsContextOnly(uri, true);
        } else if (mode == SmbProtocolMode.SMB1 || mode == SmbProtocolMode.SMB1_COMPATIBILITY) {
            context = getCifsContextOnly(uri, false);
        } else {
            context = getCifsContext(uri, true);
        }
        if (log.isDebugEnabled()) log.debug("Using SMB protocol mode {} for {}", mode, uri);
        return new NovaSmbFile(uri, context);
    }

    public static NovaSmbFile getSmbFileAllProtocols(Uri uri, Boolean isSMBv2) throws MalformedURLException {
        CIFSContext context = getCifsContext(uri, isSMBv2);
        return new NovaSmbFile(uri, context);
    }

    public static boolean isSMBv2Enabled() {
        if (log.isDebugEnabled()) log.debug("isSMBv2Enabled={}", PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", true));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", true);
    }

    public static boolean isResolverBcastFirst() {
        if (log.isDebugEnabled()) log.debug("isResolverBcastFirst={}", PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smb_resolv", false));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smb_resolv", false);
    }

}
