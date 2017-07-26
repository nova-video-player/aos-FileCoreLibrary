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

package com.archos.filecorelibrary.ftp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.net.ftp.FTPClient;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;


/**
 * returns 
 * @author alexandre
 *
 */
public class FTPRawLister extends RawLister {
    public FTPRawLister(Uri uri) {
        super(uri);
    }

    public List<MetaFile2> getFileList() throws IOException, AuthenticationException {
        FTPClient ftp = Session.getInstance().getFTPClient(mUri);
        ftp.cwd(mUri.getPath()); 
        org.apache.commons.net.ftp.FTPFile[] listFiles = ftp.listFiles(); 

        if(listFiles==null)
            return null;
        ArrayList<MetaFile2> list = new ArrayList<MetaFile2>();
        for(org.apache.commons.net.ftp.FTPFile f : listFiles){
            if(!f.getName().equals("..")|| !f.getName().equals(".")){
                FTPFile2 sf = new FTPFile2(f , Uri.withAppendedPath(mUri, f.getName()));
                list.add(sf);   
            }
        }
        return list;
    }
}
