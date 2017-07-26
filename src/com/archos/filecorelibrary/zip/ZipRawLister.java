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

package com.archos.filecorelibrary.zip;

import android.net.Uri;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.ftp.AuthenticationException;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * returns 
 * @author alexandre
 *
 */
public class ZipRawLister extends RawLister {
    public ZipRawLister(Uri uri) {
        super(uri);
    }

    public List<MetaFile2> getFileList() throws IOException, AuthenticationException {
        String toTest = ZipUtils.getZipPathFromUri(mUri);
        String path = mUri.getPath();
        String remains = "";
        if (path.length() > toTest.length() + 1)
            remains = path.substring(toTest.length() + 1);//remove first "/"

        ZipFile zf = new ZipFile(toTest);
        ArrayList<ZipEntry> entries = (ArrayList<ZipEntry>) Collections.list(zf.entries());
        if (entries == null) {

            return null;
        }
        ArrayList<MetaFile2> list = new ArrayList<>();
        for (ZipEntry entry : entries) {
            if (entry.getName().startsWith(remains) || remains.equals("")) {
                String name = entry.getName().substring(remains.length());
                boolean rightLevel = !name.equalsIgnoreCase("") && (name.indexOf('/') == name.length() - 1 || name.indexOf('/') == -1);
                if (rightLevel) {

                    ZipFile2 zf2 = new ZipFile2(toTest, entry);
                    list.add(zf2);
                }
            }
        }
        return list;


    }
}
