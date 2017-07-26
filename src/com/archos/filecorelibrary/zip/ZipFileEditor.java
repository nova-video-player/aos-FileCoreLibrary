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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.localstorage.LocalStorageFileEditor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Provides methods to edit files, and everything about metafile2 that requires a connection
 * @author alexandre
 * 
 * every action can rise an exception such as
 * FileNotFoundException
 * AuthenticationException
 * UnknownHostException
 * IOException, 
 * and a custom Exception (used in my case for permission exception)
 */
public class ZipFileEditor extends FileEditor {

    public ZipFileEditor(Uri uri) {
        super(uri);
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        return false;
    }

    @Override
    public InputStream getInputStream() throws Exception {
        ZipInputStream zis = null;
        FileInputStream fis = null;
        BufferedInputStream buf = null;
        String toTest = ZipUtils.getZipPathFromUri(mUri);

        fis = new FileInputStream(new File(toTest));
        buf = new BufferedInputStream(fis);
        zis = new ZipInputStream(buf);
        String path = mUri.getPath();
        String remains ="";
        Log.d("zipdebug", "mUri " + mUri);
        if(path.length()>toTest.length()+1)
            remains = path.substring(toTest.length()+1);//remove first "/"
        if(remains.equals(""))// input is on the whole file
            return zis;
        ZipEntry entry;
        while((entry=zis.getNextEntry())!=null){
            Log.d("zipdebug", "name " + entry.getName());
            Log.d("zipdebug","remains "+remains);
            if(entry.getName().startsWith(remains)||remains.equals("")){
                String name = entry.getName().substring(remains.length());
                Log.d("zipdebug","input found");
                    return zis;

            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        return getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws Exception {
        return null;
    }

    @Override
    public void delete() throws Exception {
        throw new LocalStorageFileEditor.DeleteFailException();
    }

    @Override
    public boolean rename(String newName) {
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        return false;
    }

    @Override
    public boolean exists() {
        return false;
    }
}
