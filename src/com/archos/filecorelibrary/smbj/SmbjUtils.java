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

import static com.archos.filecorelibrary.FileUtils.getFilePath;
import static com.archos.filecorelibrary.FileUtils.getShareName;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2Dialect;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

public class SmbjUtils {

    private static final Logger log = LoggerFactory.getLogger(SmbjUtils.class);
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, Session> smbjSessions = new ConcurrentHashMap<>();
    static private ConcurrentHashMap<NetworkCredentialsDatabase.Credential, DiskShare> smbjShares = new ConcurrentHashMap<>();
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
        /*
        smbConfig = SmbConfig.builder()
                .withTimeout(120, TimeUnit.SECONDS) // read/write transactions timeout
                .withSoTimeout(180, TimeUnit.SECONDS) // socket timeout
                .withDialects(SMB2Dialect.SMB_2_1).build(); // default SMB protocol
         */
    }

    public synchronized Session getSmbSession(Uri uri) throws IOException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        String password = cred.getPassword();
        String username = cred.getUsername();
        String domain = cred.getDomain();
        String server = uri.getHost();
        Session smbSession = smbjSessions.get(cred);
        if (smbSession == null) {
            //SMBClient smbClient = new SMBClient(smbConfig);
            SMBClient smbClient = new SMBClient();
            Connection connection = smbClient.connect(server);
            AuthenticationContext ac = new AuthenticationContext(username, password.toCharArray(), domain);
            smbSession = connection.authenticate(ac);
            smbjSessions.put(cred, smbSession);
        }
        return smbSession;
    }

    public synchronized DiskShare getSmbShare(Uri uri) throws IOException {
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        if (cred == null)
            cred = new NetworkCredentialsDatabase.Credential("anonymous", "", buildKeyFromUri(uri).toString(), "", true);
        String shareName = getShareName(uri);
        DiskShare smbShare = smbjShares.get(cred);
        if (smbShare == null) {
            Session smbSession = getSmbSession(uri);
            if (smbSession != null) {
                smbShare = (DiskShare) smbSession.connectShare(shareName);
                smbjShares.put(cred, smbShare);
            }
        }
        return smbShare;
    }

    private static Uri buildKeyFromUri(Uri uri) {
        // use Uri without the path segment as key: for example, "smbj://blabla.com:5006/toto/titi" gives a "smbj://blabla.com:5006" key
        return uri.buildUpon().path("").build();
    }

    // TODO MARC move elswhere
    public static boolean isDirectory(FileIdBothDirectoryInformation fileEntry) {
        return EnumWithValue.EnumUtils.isSet(fileEntry.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
    }

}
