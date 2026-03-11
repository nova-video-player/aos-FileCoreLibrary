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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

import com.archos.filecorelibrary.FileEditor;

import android.net.Uri;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.archos.filecorelibrary.FileUtils.caughtException;
import static com.archos.filecorelibrary.jcifs.JcifsUtils.getSmbFile;

public class JcifsFileEditor extends FileEditor{

    private static final Logger log = LoggerFactory.getLogger(JcifsFileEditor.class);

    public JcifsFileEditor(Uri uri) {
        super(uri);
    }

    private InputStream instrumentInputStream(InputStream inputStream, long from, long openStartedNs) {
        long openDoneNs = System.nanoTime();
        if (log.isDebugEnabled()) {
            log.debug("jcifs stream open: uri={} from={} open_ms={}",
                    mUri, from, (openDoneNs - openStartedNs) / 1_000_000.0);
        }
        return new FilterInputStream(inputStream) {
            private final long firstReadStartedNs = System.nanoTime();
            private boolean firstReadLogged;
            private long bytesRead;

            private void logReadResult(int count) {
                if (!firstReadLogged) {
                    firstReadLogged = true;
                    if (log.isDebugEnabled()) {
                        log.debug("jcifs stream first-read: uri={} from={} first_read_ms={} count={}",
                                mUri, from, (System.nanoTime() - firstReadStartedNs) / 1_000_000.0, count);
                    }
                }
                if (count > 0) {
                    bytesRead += count;
                }
            }

            @Override
            public int read() throws IOException {
                int result = super.read();
                logReadResult(result >= 0 ? 1 : result);
                return result;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int result = super.read(b, off, len);
                logReadResult(result);
                return result;
            }

            @Override
            public void close() throws IOException {
                long closeStartedNs = System.nanoTime();
                try {
                    super.close();
                } finally {
                    if (log.isDebugEnabled()) {
                        log.debug("jcifs stream close: uri={} from={} bytes_read={} lifetime_ms={} close_ms={}",
                                mUri, from, bytesRead,
                                (System.nanoTime() - openDoneNs) / 1_000_000.0,
                                (System.nanoTime() - closeStartedNs) / 1_000_000.0);
                    }
                }
            }
        };
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            getSmbFile(mUri).smbFile.mkdir();
            return true;
        } catch (SmbException e) {
            caughtException(e, "JcifsFileEditor:mkdir", "SmbException in mkdir " + mUri);
        } catch (MalformedURLException e) {
            caughtException(e, "JcifsFileEditor:mkdir", "MalformedURLException in mkdir " + mUri);
        }
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        long openStartedNs = System.nanoTime();
        return instrumentInputStream(new SmbFileInputStream(getSmbFile(mUri).smbFile), 0, openStartedNs);
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        long openStartedNs = System.nanoTime();
        InputStream is = new SmbFileInputStream(getSmbFile(mUri).smbFile);
        long skipStartedNs = System.nanoTime();
        is.skip(from);
        if (log.isDebugEnabled()) {
            log.debug("jcifs stream skip: uri={} from={} skip_ms={}",
                    mUri, from, (System.nanoTime() - skipStartedNs) / 1_000_000.0);
        }
        return instrumentInputStream(is, from, openStartedNs);
    }

    @Override
    public OutputStream getOutputStream() throws SmbException, MalformedURLException, UnknownHostException {
        return new SmbFileOutputStream(getSmbFile(mUri).smbFile);
    }

    @Override
    public Boolean delete() throws Exception {
        SmbFile smbFile = getSmbFile(mUri).smbFile;
        if (smbFile.isFile() || smbFile.isDirectory())
            getSmbFile(mUri).smbFile.delete();
        return null;
    }

    @Override
    public boolean rename(String newName) {
        try {
            SmbFile from = getSmbFile(mUri).smbFile;
            if (from != null) {
                SmbFile to = getSmbFile(Uri.parse(from.getParent() + newName)).smbFile;
                if (log.isDebugEnabled()) log.debug("rename: {} to {}", from, to);
                if (to != null) {
                    from.renameTo(to);
                    return true;
                }
            }
        } catch (SmbException e) {
            caughtException(e, "JcifsFileEditor:rename", "SmbException in rename " + mUri + " into " + newName);
        } catch (MalformedURLException e) {
            caughtException(e, "JcifsFileEditor:rename", "MalformedURLException in rename "  + mUri + " into " + newName);
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        return false;
    }

    @Override
    public boolean exists() {
        if (log.isTraceEnabled()) log.trace("exists: check {}", mUri);
        try {
            SmbFile sf = getSmbFile(mUri).smbFile;
            if (sf != null) {
                boolean doesItExist = sf.exists();
                if (log.isTraceEnabled()) {
                    if (doesItExist) log.trace("exists: {} exists", mUri);
                    else if (log.isTraceEnabled()) log.trace("exists: {} does not exist", mUri);
                }
                return doesItExist;
                //return sf.exists();
            } else {
                log.warn("exists: getSmbFile returned null!");
            }
        } catch (SmbException e) {
            caughtException(e, "JcifsFileEditor:exists", "SmbException in exists for " + mUri);
        } catch (MalformedURLException e) {
            caughtException(e, "JcifsFileEditor:exists", "MalformedURLException in exists " + mUri);
        }
        return false;
    }

}
