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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;

import com.archos.filecorelibrary.contentstorage.DocumentUriBuilder;

import java.util.Locale;

public class Utils {
    
    final static char SEP = '/';
    final static String SEPARATOR = "/";

    public static String getParentUrl(String url) {
        int index;

        if (url.endsWith(SEPARATOR)) {
            index = url.lastIndexOf(SEPARATOR, url.length()-2);
        }
        else {
            index = url.lastIndexOf(SEPARATOR);
        }

        if (index<=0) {
            return null;
        }
        return url.substring(0, index + 1);
    }

    public static Uri getParentUrl(Uri uri) {

        if("content".equals(uri.getScheme())){
            return Uri.parse(DocumentUriBuilder.getParentUriStringAndFileName(uri).first);
        }

        return removeLastSegment(uri);
    }

    public static Uri removeLastSegment(Uri uri){
        int index;

        String str = uri.toString();

        if (str.endsWith(SEPARATOR)) {
            index = str.lastIndexOf(SEPARATOR, str.length()-2);
        }
        else {
            index = str.lastIndexOf(SEPARATOR);
        }

        if (index<=0) {
            return null;
        }
        return Uri.parse(str.substring(0, index + 1));
    }
    /**
     * Computes the BUCKET_ID (See {@link android.provider.MediaStore.Video.VideoColumns#BUCKET_ID})
     * for this file. The id is used in our and Android's database to identify the
     * folder of a file/directory. All files / subdirectories in a directory have
     * the same id. The folder in which all these files reside has the bucket id
     * of it's parent folder. <p>
     * Note: Since the bucket id is a hash over the lowercased parent path, chances
     * are that it's not 100% perfect. Hash collision and case sensitive filenames
     * can in theory lead to some confusion.
     *
     * @return the bucket id or -1 if not available
     */
    public static int getBucketId(Uri uri) {
        // Here we do use language independent toLowerCase
        String path = uri.getPath();
        return path != null ? path.toLowerCase(Locale.ROOT).hashCode() : -1;
    }

    public static boolean isLocal(Uri uri){
        return uri.getScheme()==null||"file".equals(uri.getScheme())||"content".equalsIgnoreCase(uri.getScheme())||uri.toString().startsWith("/");
    }
    public static String getFileNameWithoutExtension(Uri uri){
        String name = getName(uri);
        return stripExtensionFromName(name);
    }

    public static String stripExtensionFromName(String name){
        if (name != null) {
            int dotPos = name.lastIndexOf('.');
            if (dotPos > 0) {
                name = name.substring(0, dotPos);
            }
        }
        return name;
    }


    public static  String getExtension(String filename) {
        if (filename == null)
            return null;
        int dotPos = filename.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filename.length()) {
            return filename.substring(dotPos + 1).toLowerCase();
        }
        return null;
    }


    //useful for uris in content://
    public static Uri getRealUriFromVideoURI(Context context, Uri contentUri) {
        if(!contentUri.getScheme().equals("content"))
            return contentUri;
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Video.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
            if(cursor==null)
                return contentUri;
            int column_index = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            if(cursor.getCount()==0
                    ||column_index<0||cursor.getString(column_index)==null) {
                cursor.close();
                return contentUri;
            }
            String uri = cursor.getString(column_index);
            cursor.close();
            return Uri.parse(uri);
        } finally {

        }
    }

    public static Uri getContentUriFromFileURI(Context context, Uri dataUri) {
        if(!"file".equals(dataUri.getScheme()))
            return null;
        dataUri = Uri.parse(dataUri.getPath());
        Cursor cursor = null;
        try {

            String where= MediaStore.MediaColumns.DATA +"= ?";
            String[] whereArg = { dataUri.toString() };
            String[] proj = { BaseColumns._ID};
            cursor = context.getContentResolver().query(MediaStore.Files.getContentUri("external"),  proj, where, whereArg, null);
            if(cursor==null) {
                return null;
            }
            int column_index = cursor.getColumnIndex( BaseColumns._ID);
            cursor.moveToFirst();
            if(cursor.getCount()==0
                    ||column_index<0||cursor.getInt(column_index)==-1) {
                cursor.close();
                return null;
            }
            String uri = MediaStore.Files.getContentUri("external").toString()+"/"+cursor.getInt(column_index);
            cursor.close();
            return Uri.parse(uri);
        } finally {

        }
    }
    public static String getName(Uri uri){
        if(uri!=null) {
            String name = uri.getLastPathSegment();
            if (name == null || name.isEmpty()) {
                if (uri.toString().lastIndexOf("/") >= 0 && uri.toString().lastIndexOf("/") < (uri.toString().length() - 1))
                    name = uri.toString().substring(uri.toString().lastIndexOf("/") + 1);
                else
                    name = uri.toString();

            }
            if(name!=null&&"content".equals(uri.getScheme())){
                String[] parts = name.split(":");
                name = parts[parts.length-1];

            }
            return name;
        }
        return null;
    }
    public static boolean isSlowRemote(Uri uri) {
        return "ftps".equals(uri.getScheme())||"ftp".equals(uri.getScheme())||"sftp".equals(uri.getScheme());
    }

    /**
     * returns true if is really on a share (for example smb://quatro/sda(...) or ftp://bla.fr(...))
     * @param parent
     * @return
     */
    public static boolean isOnAShare(Uri parent) {
        return "smb".equals(parent.getScheme())&&parent.getPath()!=null
                &&!parent.getPath().equals("/")
                &&!parent.getPath().isEmpty()||
                !"smb".equals(parent.getScheme());
    }

    public static Uri encodeUri(Uri uri) {
        String uriString = uri.toString();
        String uriEncodedString ="";
        if(uri.getScheme()!=null) {
            uriEncodedString += uri.getScheme() + "://";
            uriString = uriString.substring((uri.getScheme() + "://").length());
        }
        int i= 0;
        for(String seg : uriString.split("/")){ //split instead of using uri. get path segments because when weird caracters such as # %, path segments don't work properly
            seg = seg.replace("/","");
            if(i!=0||uriString.startsWith("/"))
                uriEncodedString+="/";
            uriEncodedString+=Uri.encode(seg);
            i++;
        }
        if(uriEncodedString.startsWith("//"))
            uriEncodedString = uriEncodedString.substring(1);
        return Uri.parse(uriEncodedString);
    }

    public static Uri buildChildUri(Uri parent, String childName){
        if("content".equals(parent.getScheme())){
            return Uri.parse(DocumentUriBuilder.buildDocumentUriUsingTree(parent).toString()+Uri.encode("/")+childName);
        }
        return Uri.withAppendedPath(parent, childName);
    }
}
