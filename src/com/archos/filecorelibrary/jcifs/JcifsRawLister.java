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

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;


/**
 * returns 
 * @author alexandre
 *
 */
public class JcifsRawLister extends RawLister {
    public JcifsRawLister(Uri uri) {
        super(uri);
    }

    public List<MetaFile2> getFileList() throws SmbException, MalformedURLException{
        NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
        SmbFile[] listFiles;
        if(cred!=null){
            SingletonContext context = SingletonContext.getInstance();
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(context, "",cred.getUsername(), cred.getPassword());
            listFiles = new SmbFile(mUri.toString(), context.withCredentials(auth)).listFiles();
        }
        else
            listFiles = new SmbFile(mUri.toString()).listFiles();
        if(listFiles!=null){
            ArrayList<MetaFile2> files = new ArrayList<>();
            for(SmbFile f : listFiles){
                files.add(new JcifsFile2(f));
            }
            return files;
        }
        return null;
    }
}
