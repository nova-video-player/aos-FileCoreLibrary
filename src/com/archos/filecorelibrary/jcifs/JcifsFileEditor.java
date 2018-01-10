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

package com.archos.filecorelibrary.jcifs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.UnknownHostException;

import jcifs.SmbRandomAccess;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;
import jcifs.smb.SmbRandomAccessFile;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import android.net.Uri;
import android.util.Log;

public class JcifsFileEditor extends FileEditor{

    private final static String TAG = "JcifsFileEditor";
    private static boolean DBG = false;

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
            caughtException(e, "SmbException in mkdir");
        } catch (MalformedURLException e) {
            caughtException(e, "MalformedURLException in mkdir");
        }
        return false;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new SmbFileInputStream(getSmbFile(mUri));
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        SmbRandomAccess is = new SmbRandomAccessFile(getSmbFile(mUri), "r");
        is.seek(from);
        return (InputStream) is;
    }

    @Override
    public OutputStream getOutputStream() throws SmbException, MalformedURLException, UnknownHostException {
        return  new SmbFileOutputStream(getSmbFile(mUri));
    }


    @Override
    public void delete() throws Exception {
        getSmbFile(mUri).delete();
    }

    @Override
    public boolean rename(String newName) {
        try {
            SmbFile from = getSmbFile(mUri);
            if(from!=null) {
                SmbFile to = getSmbFile(Uri.parse(from.getParent() + "/" + newName));
                if(to!=null){

                    from.renameTo(to);
                    return true;
                }
            }
        }
        catch (SmbException e) {
            caughtException(e, "SmbException in rename");
        } catch (MalformedURLException e) {
            caughtException(e, "MalformedURLException in rename");
        }
        return false;
    }

    private SmbFile getSmbFile(Uri uri) throws MalformedURLException {

        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(uri.toString());
        SmbFile smbfile;
        if(cred!=null){
            SingletonContext context = SingletonContext.getInstance();
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(context, "",cred.getUsername(), cred.getPassword());
            smbfile= new SmbFile(uri.toString(), context.withCredentials(auth));
        }
        else {
            smbfile= new SmbFile(uri.toString());
        }
        return smbfile;

    }

    @Override
    public boolean move(Uri uri) {
        return false;
    }

    @Override
    public boolean exists() {
        try {
            SmbFile sf = getSmbFile(mUri);
            if(sf!=null)
                return sf.exists();
        } catch (SmbException e) {
            caughtException(e, "SmbException in exists");
        } catch (MalformedURLException e) {
            caughtException(e, "MalformedURLException in exists");
        }
        return false;
    }

    private void caughtException(Throwable e, String exceptionType) {
        if (DBG) Log.e(TAG, "exists: caught" + exceptionType, e);
        else Log.w(TAG, "exists: caught "+ exceptionType);
    }
}
