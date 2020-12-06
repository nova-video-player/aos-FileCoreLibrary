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
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

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

    private final static String TAG = "JcifsUtils";
    private final static boolean DBG = false;

    // when enabling LIMIT_PROTOCOL_NEGO smbFile will use strict SMBv1 or SMBv2 contexts to avoid SMBv1 negotiations or SMBv2 negotiations
    // this is a hack to get around some issues seen with jcifs-ng
    // note to self: do not try to revert to false since it does not work (HP printer, livebox smbV1 broken with smbV2 enabled)
    public final static boolean LIMIT_PROTOCOL_NEGO = true;

    private static Properties prop = null;
    private static CIFSContext baseContextSmb1 = createContext(false);
    private static CIFSContext baseContextSmb2 = createContext(true);

    private static CIFSContext baseContextSmb1Only = createContextOnly(false);
    private static CIFSContext baseContextSmb2Only = createContextOnly(true);

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
    }

    private static CIFSContext createContext(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        prop.put("jcifs.smb.client.enableSMB2", String.valueOf(isSmb2));
        // must remain false to be able to talk to smbV1 only
        prop.put("jcifs.smb.client.useSMB2Negotiation", "false");
        prop.put("jcifs.smb.client.disableSMB1", "false");
        // get around https://github.com/AgNO3/jcifs-ng/issues/40 and this is required for guest login on win10 smb2
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");
        // disable dfs makes win10 shares with ms account work
        prop.put("jcifs.smb.client.dfs.disabled", "true");
        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            //Log.e(TAG, "CIFSException: ", e);
            Log.d(TAG, "CIFSException caught PropertyConfiguration");
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
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");

        // Required to make WD MyCloud work cf. https://github.com/AgNO3/jcifs-ng/issues/225
        // made guest work on Win10 https://github.com/AgNO3/jcifs-ng/issues/186
        prop.put("jcifs.smb.client.disableSpnegoIntegrity", "true");

        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            //Log.e(TAG, "CIFSException: ", e);
            Log.d(TAG, "CIFSException caught PropertyConfiguration");
        }
        return new BaseContext(propertyConfiguration);
    }

    public static CIFSContext getBaseContext(boolean isSmb2) {
        return isSmb2 ? baseContextSmb2 : baseContextSmb1;
    }

    public static CIFSContext getBaseContextOnly(boolean isSmb2) {
        return isSmb2 ? baseContextSmb2Only : baseContextSmb1Only;
    }

    private static HashMap<String, Boolean> ListServers = new HashMap<>();

    public static void declareServerSmbV2(String server, boolean isSmbV2) {
        if (DBG) Log.d(TAG, "declareServerSmbV2 for " + server + " " + isSmbV2);
        ListServers.put(server, isSmbV2);
    }

    private static CIFSContext getCifsContext(Uri uri, Boolean isSmbV2) {
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
    public static Boolean isServerSmbV2(String server) throws MalformedURLException {
        Boolean isSmbV2 = ListServers.get(server);
        if (DBG) Log.d(TAG, "isServerSmbV2 for " + server + " previous state is " + isSmbV2);
        if (isSmbV2 == null) { // let's probe server root
            Uri uri = Uri.parse("smb://" + server + "/");
            SmbFile smbFile = null;
            try {
                if (DBG) Log.d(TAG, "isServerSmbV2: probing " + uri + " to check if smbV2");
                CIFSContext ctx = getCifsContext(uri, true);
                smbFile = new SmbFile(uri.toString(), ctx);
                smbFile.listFiles(); // getType is pure smbV1, exists identifies smbv2 even smbv1, only list provides a result
                declareServerSmbV2(server, true);
                if (DBG) Log.d(TAG, "isServerSmbV2 for " + server + " returning true");
                return true;
            } catch (SmbAuthException authE) {
                if (DBG) Log.d(TAG, "isServerSmbV2: caught SmbAutException in probing, state for " + server + "  returning null");
                return null;
            } catch (SmbException smbE) {
                if (DBG) Log.d(TAG, "isServerSmbV2: caught SmbException " + smbE);
                try {
                    if (DBG) Log.d(TAG, "isServerSmbV2: it is not smbV2 probing " + uri + " to check if smbV1");
                    CIFSContext ctx = getCifsContext(uri, false);
                    smbFile = new SmbFile(uri.toString(), ctx);
                    smbFile.listFiles(); // getType is pure smbV1, exists identifies smbv2 even smbv1, only list provides a result
                    declareServerSmbV2(server, false);
                    if (DBG) Log.d(TAG, "isServerSmbV2 for " + server + " returning false");
                    return false;
                } catch (SmbException ce2) {
                    if (DBG) Log.d(TAG, "isServerSmbV2: caught SmbAutException in probing, returning null");
                    return null;
                }
            }
        } else
            return isSmbV2;
    }

    public static SmbFile getSmbFile(Uri uri) throws MalformedURLException {
        if (DBG) Log.d(TAG, "getSmbFile: for " + uri);
        if (isSMBv2Enabled() && LIMIT_PROTOCOL_NEGO)
            return getSmbFileStrictNego(uri);
        else
            return getSmbFileAllProtocols(uri, isSMBv2Enabled());
    }

    public static SmbFile getSmbFileStrictNego(Uri uri) throws MalformedURLException {
        Boolean isSmbV2 = isServerSmbV2(uri.getHost());
        CIFSContext context = null;
        if (isSmbV2 == null) { // server type not identified, default to smbV2
            context = getBaseContext(true);
            if (DBG) Log.d(TAG, "getSmbFileStrictNego: server NOT identified passing smbv2/smbv1 capable context for uri " + uri);
        } else if (isSmbV2) { // provide smbV2 only
            context = getBaseContextOnly(true);
            if (DBG) Log.d(TAG, "getSmbFileStrictNego: server already identified as smbv2 processing uri " + uri);
        } else { // if dont't know (null) or smbV2 provide smbV2 only to try out. Fallback needs to be implemented in each calls
            context = getBaseContextOnly(false);
            if (DBG) Log.d(TAG, "getSmbFileStrictNego: server already identified as smbv1 processing uri " + uri);
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
        if (DBG) Log.d(TAG, "isSMBv2Enabled=" + PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", true));
        return PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean("pref_smbv2", true);
    }

}
