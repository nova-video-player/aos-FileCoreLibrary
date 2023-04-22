// Copyright 2021 Courville Software
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

import static com.archos.filecorelibrary.samba.SambaDiscovery.dumpShareNameResolver;
import static com.archos.filecorelibrary.samba.SambaDiscovery.getIpFromShareName;

import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;

import jcifs.CIFSContext;
import jcifs.smb.SmbFile;

// this class contains both the original URI with shareName and not IP and smbFile derived from IP based uri
// purpose is to preserve shareName based listing for indexing since IP for a shareName can change

public class NovaSmbFile {
    SmbFile smbFile;
    Uri originalUri;
    String shareIP;
    String shareName;

    private static final Logger log = LoggerFactory.getLogger(NovaSmbFile.class);

    public static String getIpUriString(Uri uri) {
        String shareName = uri.getHost();
        log.trace("getIpUriString: uri " + uri + ", shareName " + shareName + ", shareNameIp " + getIpFromShareName(shareName));
        log.trace("getIpUriString: shareNameResolver hashtable " + dumpShareNameResolver());
        String shareNameIP = getIpFromShareName(shareName);
        if (shareNameIP == null) return uri.toString();
        else return uri.toString().replaceFirst(shareName, shareNameIP);
    }

    public String getCanonicalPath() {
        return smbFile.getCanonicalPath().replaceFirst(shareIP, shareName);
    }

    public String getName() {
        return smbFile.getName().replaceFirst(shareIP, shareName);
    }

    public NovaSmbFile(Uri uri, CIFSContext cifsContext) throws MalformedURLException {
        originalUri = uri;
        shareName = uri.getHost();
        log.trace("NovaSmbFile: shareNameResolver hashtable " + dumpShareNameResolver());
        String shareNameIP = getIpFromShareName(shareName);
        log.trace("NovaSmbFile: uri " + uri + ", shareName " + shareName + ", shareNameIP " + shareNameIP);
        if (shareNameIP == null) {
            smbFile = new SmbFile(uri.toString(), cifsContext);
            shareIP = shareName;
        } else {
            shareIP = shareNameIP;
            smbFile = new SmbFile(uri.toString().replaceFirst(shareName, shareIP), cifsContext);
        }
    }
}
