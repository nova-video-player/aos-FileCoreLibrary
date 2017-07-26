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

package com.archos.filecorelibrary.localstorage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;


/**
 * returns 
 * @author alexandre
 *
 */
public class LocalStorageRawLister extends RawLister {
    public LocalStorageRawLister(Uri uri) {
        super(uri);
    }

    public List<MetaFile2> getFileList(){
        String path = mUri.getPath();
        if(path==null)
            return null;
        File[] listFiles = new File(path).listFiles();
        if(listFiles==null)
            return null;
        ArrayList<MetaFile2> files = new ArrayList<MetaFile2>();
        for(File file : listFiles){
            files.add(new JavaFile2(file));
        }
        return files;
    }
}
