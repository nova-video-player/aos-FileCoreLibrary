// Copyright 2017 Archos SA
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

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import static com.archos.filecorelibrary.jcifs.JcifsUtils.getSmbFile;
import static com.archos.filecorelibrary.FileUtils.isDotDirectoryEntry;


/**
 * returns 
 * @author alexandre
 *
 */
public class JcifsRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(JcifsRawLister.class);

    // not exposed by jcifs.smb.NtStatus but returned by servers when a pooled tree/file
    // handle was closed concurrently (e.g. jcifs-ng's "Disconnected tree while still in
    // use" case): retrying with a freshly resolved SmbFile recovers from it.
    private static final int NT_STATUS_FILE_CLOSED = 0xC0000128;
    private static final int NT_STATUS_CONNECTION_DISCONNECTED = 0xC000020C;
    private static final int NT_STATUS_CONNECTION_RESET = 0xC000020D;
    private static final int NT_STATUS_NETWORK_NAME_DELETED = 0xC00000c9;

    public JcifsRawLister(Uri uri) {
        super(uri);
    }

    public List<MetaFile2> getFileList() throws SmbException, MalformedURLException {
        String uriString = mUri.toString();
        if (! uriString.endsWith("/")) {
            uriString += "/";
            mUri = Uri.parse(uriString);
        }
        NovaSmbFile nSmbFile = getSmbFile(mUri);
        if (nSmbFile == null || nSmbFile.smbFile == null) {
            return null;
        }
        try {
            return list(nSmbFile);
        } catch (SmbException e) {
            if (!isRetryable(e)) throw e;
            log.warn("getFileList: retryable error for {}, retrying with a fresh handle", mUri, e);
            NovaSmbFile retryFile = getSmbFile(mUri);
            if (retryFile == null || retryFile.smbFile == null) throw e;
            return list(retryFile);
        } catch (Exception e) {
            log.error("getFileList: caught exception for {}", mUri, e);
        }
        return null;
    }

    private List<MetaFile2> list(NovaSmbFile nSmbFile) throws SmbException {
        SmbFile[] listFiles = nSmbFile.smbFile.listFiles();
        if (listFiles != null) {
            ArrayList<MetaFile2> files = new ArrayList<>();
            for(SmbFile f : listFiles){
                String filename = f.getName();
                if (isDotDirectoryEntry(filename)) {
                    continue;
                }
                // better verify that it is a file or directory before adding
                if(f.isFile() || f.isDirectory()) {
                    if (log.isTraceEnabled()) log.trace("found {}", f.getPath());
                    files.add(new JcifsFile2(f, nSmbFile.shareName, nSmbFile.shareIP));
                }
            }
            return files;
        }
        return null;
    }

    private static boolean isRetryable(SmbException e) {
        int status = e.getNtStatus();
        return status == NT_STATUS_FILE_CLOSED
                || status == NT_STATUS_CONNECTION_DISCONNECTED
                || status == NT_STATUS_CONNECTION_RESET
                || status == NT_STATUS_NETWORK_NAME_DELETED;
    }
}
