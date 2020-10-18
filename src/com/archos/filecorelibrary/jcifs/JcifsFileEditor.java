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

import static com.archos.filecorelibrary.jcifs.JcifsUtils.getSmbFile;

public class JcifsFileEditor extends FileEditor{

    private static final String TAG = "JcifsFileEditor";
    private static final boolean DBG = false;
    
    public JcifsFileEditor(Uri uri) {
        super(uri);
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        try {
            getSmbFile(mUri).mkdir();
            return true;
        } catch (SmbException e) {
            caughtException(e, "mkdir", "SmbException in mkdir " + mUri);
        } catch (MalformedURLException e) {
            caughtException(e, "mkdir", "MalformedURLException in mkdir " + mUri);
        }
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new SmbFileInputStream(getSmbFile(mUri));
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        InputStream is = new SmbFileInputStream(getSmbFile(mUri));
        is.skip(from);
        return is;
    }

    @Override
    public OutputStream getOutputStream() throws SmbException, MalformedURLException, UnknownHostException {
        return  new SmbFileOutputStream(getSmbFile(mUri));
    }

    @Override
    public void delete() throws Exception {
        SmbFile smbFile = getSmbFile(mUri);
        if (smbFile.isFile() || smbFile.isDirectory())
            getSmbFile(mUri).delete();
    }

    @Override
    public boolean rename(String newName) {
        try {
            SmbFile from = getSmbFile(mUri);
            if (from != null) {
                SmbFile to = getSmbFile(Uri.parse(from.getParent() + "/" + newName));
                if (to != null) {
                    from.renameTo(to);
                    return true;
                }
            }
        } catch (SmbException e) {
            caughtException(e, "rename", "SmbException in rename " + mUri + " into " + newName);
        } catch (MalformedURLException e) {
            caughtException(e, "rename", "MalformedURLException in rename "  + mUri + " into " + newName);
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        return false;
    }

    @Override
    public boolean exists() {
        if (DBG) Log.d(TAG, "exists: check " + mUri);
        try {
            SmbFile sf = getSmbFile(mUri);
            if (sf != null) return sf.exists();
        } catch (SmbException e) {
            caughtException(e, "exists", "SmbException in exists for " + mUri);
        } catch (MalformedURLException e) {
            caughtException(e, "exists", "MalformedURLException in exists " + mUri);
        }
        return false;
    }

    private void caughtException(Throwable e, String method, String exceptionType) {
        if (DBG) Log.e(TAG, method + ": caught" + exceptionType, e);
        else Log.w(TAG, method + ": caught "+ exceptionType);
    }
}
