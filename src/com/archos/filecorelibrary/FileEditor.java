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

package com.archos.filecorelibrary;

import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.net.Uri;
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
public abstract class FileEditor {
    protected Uri mUri;
    private static final int MAX_COUNT = 32768;
    public FileEditor(Uri uri){
        mUri = uri;
    }
    public abstract boolean touchFile();
    public abstract boolean mkdir();
    public abstract InputStream getInputStream() throws Exception;
    public abstract InputStream getInputStream(long from) throws Exception;
    public abstract OutputStream getOutputStream() throws Exception;
    public abstract void delete() throws Exception;
    public abstract boolean rename(String newName);
    public abstract boolean move(Uri uri);
    public abstract boolean exists();

    /**
     * Use it for file (and just file) copy
     * @param target
     * @return
     * @throws Exception
     */
    public boolean copyFileTo(Uri target, Context ct) throws Exception {
        FileEditor targetEditor = FileEditorFactory.getFileEditorForUrl(target, ct);
        OutputStream out = targetEditor.getOutputStream();
        InputStream in = getInputStream();
        if(in!=null&&out!=null) {
            long position = 0;
            byte buf[] = new byte[MAX_COUNT];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
                position += (long) len;
            }
            out.close();
            in.close();
            return true;
        }
        return false;
    }
}
