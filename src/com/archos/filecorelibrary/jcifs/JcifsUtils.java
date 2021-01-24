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

import android.content.Context;
import android.net.Uri;

import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jcifs.CIFSException;
import jcifs.context.BaseContext;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Properties;


public class JcifsUtils {

    private static final Logger log = LoggerFactory.getLogger(JcifsUtils.class);

    // when enabling LIMIT_PROTOCOL_NEGO smbFile will use strict SMBv1 or SMBv2 contexts to avoid SMBv1 negotiations or SMBv2 negotiations
    // this is a hack to get around some issues seen with jcifs-ng
    // note to self: do not try to revert to false since it does not work (HP printer, livebox smbV1 broken with smbV2 enabled) but jcifs.smb.useRawNTLM=true solves this!
    // update note to self: true creates protocol identification issues it seems (not threadsafe with multiple parallel requests?)
    public final static boolean LIMIT_PROTOCOL_NEGO = false;

    private static Properties prop = null;
    private static CIFSContext baseContextSmb1, baseContextSmb2, baseContextSmb1Only, baseContextSmb2Only;

    private static Context mContext;

    // singleton, volatile to make double-checked-locking work correctly
    private static volatile JcifsUtils sInstance;

    // get the instance, context is used for initial context injection
    public static JcifsUtils getInstance(Context context) {
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
        log.debug("JcifsUtils: initializing contexts");
        baseContextSmb1 = createContext(false);
        baseContextSmb2 = createContext(true);
        baseContextSmb1Only = createContextOnly(false);
        baseContextSmb2Only = createContextOnly(true);
    }

    private static CIFSContext createContext(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        prop.put("jcifs.smb.client.enableSMB2", String.valueOf(isSmb2));
        // must remain false to be able to talk to smbV1 only
        prop.put("jcifs.smb.client.useSMB2Negotiation", "false");
        prop.put("jcifs.smb.client.disableSMB1", "false");
        // resolve in this order to avoid netbios name being also a foreign DNS entry resulting in bad resolution
        // do not change resolveOrder for now
        // with jcifs-old, resolveOrder was not changed i.e. LMHOSTS,DNS,WINS,BCAST, jcifs-ng author recommends no change
        if (isResolverBcastFirst()) {
            log.debug("createContext: resolver set to BCAST,DNS");
            prop.put("jcifs.resolveOrder", "BCAST,DNS");
        }
        // get around https://github.com/AgNO3/jcifs-ng/issues/40 and this is required for guest login on win10 smb2
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");
        // disable dfs makes win10 shares with ms account work
        prop.put("jcifs.smb.client.dfs.disabled", "true");

        // Needed for Huawei router https://github.com/AgNO3/jcifs-ng/issues/225
        // see also https://github.com/AgNO3/jcifs-ng/issues/226
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

        if (isSmb2) {
            prop.put("jcifs.smb.client.disableSMB1", "true");
            prop.put("jcifs.smb.client.enableSMB2", "true");
            // note that connectivity with smbV1 will not be working
            prop.put("jcifs.smb.client.useSMB2Negotiation", "true");
            // disable dfs makes win10 shares with ms account work
            prop.put("jcifs.smb.client.dfs.disabled", "true");
        } else {
            prop.put("jcifs.smb.client.disableSMB1", "false");
            prop.put("jcifs.smb.client.enableSMB2", "false");
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
            log.debug("createContext: resolver set to BCAST,DNS");
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
        if (isSmb2) {
            if (baseContextSmb2Only == null) baseContextSmb2Only = createContextOnly(true);
            return baseContextSmb2Only;
        } else {
            if (baseContextSmb1Only == null) baseContextSmb1Only = createContextOnly(false);
            return baseContextSmb1Only;
        }
    }

    private static HashMap<String, Boolean> ListServers = new HashMap<>();

    public static void declareServerSmbV2(String server, boolean isSmbV2) {
        log.debug("declareServerSmbV2 for " + server + " " + isSmbV2);
        ListServers.put(server, isSmbV2);
    }

    private static CIFSContext getCifsContext(Uri uri, Boolean isSmbV2) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        CIFSContext context = null;
        if (cred != null) {
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
            context = getBaseContext(isSmbV2).withCredentials(auth);
        } else
            context = getBaseContext(isSmbV2).withGuestCrendentials();
        return context;
    }

    private static CIFSContext getCifsContextOnly(Uri uri, Boolean isSmbV2) {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        CIFSContext context = null;
        if (cred != null) {
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
            context = getBaseContextOnly(isSmbV2).withCredentials(auth);
        } else
            context = getBaseContextOnly(isSmbV2).withGuestCrendentials();
        return context;
    }

    // isServerSmbV2 returns true/false/null, null is do not know
    public static Boolean isServerSmbV2(String server, int port) throws MalformedURLException {
        Boolean isSmbV2 = ListServers.get(server);
        log.debug("isServerSmbV2 for " + server + " previous state is " + isSmbV2);
        if (isSmbV2 == null) { // let's probe server root
            Uri uri;
            if (port != -1) uri = Uri.parse("smb://" + server + ":" + port + "/");
            else uri = Uri.parse("smb://" + server);
            SmbFile smbFile = null;
            try {
                log.debug("isServerSmbV2: probing " + uri + " to check if smbV2");
                CIFSContext ctx = getCifsContextOnly(uri, true);
                smbFile = new SmbFile(uri.toString(), ctx);
                smbFile.listFiles(); // getType is pure smbV1, exists identifies smbv2 even smbv1, only list provides a result
                declareServerSmbV2(server, true);
                log.debug("isServerSmbV2 for " + server + " returning true");
                return true;
            } catch (SmbAuthException authE) {
                log.warn("isServerSmbV2: caught SmbAutException in probing SMB2, state for " + server + "  returning null");
                return null;
            } catch (SmbException smbE) {
                log.debug("isServerSmbV2: caught SmbException in probing SMB2 " + smbE);
                try {
                    log.debug("isServerSmbV2: it is not smbV2 probing " + uri + " to check if smbV1");
                    CIFSContext ctx = getCifsContextOnly(uri, false);
                    smbFile = new SmbFile(uri.toString(), ctx);
                    smbFile.listFiles(); // getType is pure smbV1, exists identifies smbv2 even smbv1, only list provides a result
                    declareServerSmbV2(server, false);
                    log.debug("isServerSmbV2 for " + server + " returning false");
                    return false;
                } catch (SmbAuthException authE2) {
                    log.warn("isServerSmbV2: caught SmbAutException in probing SMB1, state for " + server + "  returning null");
                    return null;
                } catch (SmbException smbE2) {
                    log.warn("isServerSmbV2: caught SmbException in probing SMB1, returning null");
                    return null;
                }
            }
        } else
            return isSmbV2;
    }

    public static SmbFile getSmbFile(Uri uri) throws MalformedURLException {
        log.debug("getSmbFile: for " + uri);
        if (LIMIT_PROTOCOL_NEGO)
            return getSmbFileStrictNego(uri);
        else
            return getSmbFileAllProtocols(uri, isSMBv2Enabled());
    }

    public static SmbFile getSmbFileStrictNego(Uri uri) throws MalformedURLException {
        Boolean isSmbV2 = isServerSmbV2(uri.getHost(), uri.getPort());
        CIFSContext context = null;
        if (isSmbV2 == null) { // server type not identified, default to smbV2&1 auto
            context = getBaseContext(true);
            log.debug("getSmbFileStrictNego: server NOT identified passing smbv2/smbv1 capable context for uri " + uri);
        } else {
            if (isSmbV2) { // provide smbV2 only
                context = getBaseContextOnly(true);
                log.debug("getSmbFileStrictNego: server already identified as smbv2 processing uri " + uri);
            } else { // if dont't know (null) or smbV2 provide smbV2 only to try out. Fallback needs to be implemented in each calls
                context = getBaseContextOnly(false);
                log.debug("getSmbFileStrictNego: server already identified as smbv1 processing uri " + uri);
            }
        }
        CIFSContext ctx = null;
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred != null) {
            NtlmPasswordAuthenticator auth = new NtlmPasswordAuthenticator("", cred.getUsername(), cred.getPassword());
            ctx = context.withCredentials(auth);
        } else
            ctx = context.withGuestCrendentials();
        return new SmbFile(uri.toString(), ctx);
    }

    public static SmbFile getSmbFileAllProtocols(Uri uri, Boolean isSMBv2) throws MalformedURLException {
        CIFSContext context = getCifsContext(uri, isSMBv2);
        return new SmbFile(uri.toString(), context);
    }

    public static boolean isSMBv2Enabled() {
        log.debug("isSMBv2Enabled=" + PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", true));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", true);
    }

    public static boolean isResolverBcastFirst() {
        log.debug("isResolverBcastFirst=" + PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smb_resolv", false));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smb_resolv", false);
    }

}
