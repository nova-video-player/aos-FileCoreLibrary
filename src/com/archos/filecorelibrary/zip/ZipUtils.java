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

import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.Utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by alexandre on 20/07/15.
 */
public class ZipUtils {

    /**return zip path from a uri like this : zip://path/to/zip.zip/entry

     would return /path/to/zip.zip
     */
    public static String getZipPathFromUri(Uri uri){
        String toTest = "";
        for(String seg : uri.getPathSegments()){
            if(!seg.startsWith("/"))
                toTest+="/";
            toTest+=seg;
            if(new File(toTest).isFile()) // then we have our zip file
                break;
        }
        return toTest;
    }

    public static boolean isZipMetaFile(MetaFile2 mf){
        if(mf==null) return false; //safer
        return "application/zip".equals(mf.getMimeType()) || mf instanceof ZipFile2;
    }

    public static boolean compressFile(File toCompress, File target) {
        if (target == null || toCompress == null) {
            return false;
        }
        try {
            final Uri uri = Uri.fromFile(toCompress);
            final String rootPath = Utils.getParentUrl(uri.toString());
            final int rootOffset = rootPath.length();

            ZipOutputStream zos = new ZipOutputStream(FileEditorFactory.getFileEditorForUrl(Uri.fromFile(target), null).getOutputStream());
            ZipEntry entry = new ZipEntry(uri.toString().substring(rootOffset));
            byte[] bytes = new byte[1024];
            InputStream fis = FileEditorFactory.getFileEditorForUrl(uri, null).getInputStream();
            entry.setSize(toCompress.length());
            entry.setTime(toCompress.lastModified());
            zos.putNextEntry(entry);
            int count;
            while ((count = fis.read(bytes)) > 0) {
                zos.write(bytes, 0, count);
            }
            zos.closeEntry();
            closeSilently(fis);
            closeSilently(zos);
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void closeSilently(Closeable closeme) {
        if (closeme == null) return;
        try {
            closeme.close();
        } catch (IOException e) {
            // silence
        }
    }
}
