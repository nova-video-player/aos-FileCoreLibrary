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

package com.archos.filecorelibrary.contentstorage;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.support.v4.provider.DocumentFile;
import android.support.v4.util.Pair;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.ExtStorageManager;
import com.archos.filecorelibrary.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Created by alexandre on 02/05/16.
 */
public class DocumentUriBuilder {
    private static final String PATH_DOCUMENT = "document";
    private static final String PATH_CHILDREN = "children";
    private static final String PATH_SEARCH = "search";
    private static final String PATH_TREE = "tree";
    public static Uri buildDocumentUriUsingTree(Uri treeUri) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(treeUri.getAuthority()).appendPath(PATH_TREE)
                .appendPath(getTreeDocumentId(treeUri)).appendPath(PATH_DOCUMENT)
                .appendPath(treeUri.getLastPathSegment()).build();
    }

    public static Uri convertFileUriToContentUri(Context context, Uri fileUri){
        //find root
        String root = "";
        Uri rootUri = null;
        String uuid = null;
        if(fileUri.getPath().startsWith(Environment.getExternalStorageDirectory().getPath())){
            uuid="primary";
            root = Environment.getExternalStorageDirectory().getPath();
            rootUri = getUriFromRootPath(context, root);
        }else {
            for (String seg : fileUri.getPathSegments()) {
                root += "/" + seg;
                uuid = ExtStorageManager.getExtStorageManager().getUuid(root);
                rootUri = getUriFromRootPath(context, root);
                if (uuid != null)
                    break;
            }
        }
        if(uuid!=null){
            String fileString = fileUri.getPath().substring((root+"/").length());
            return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                    .authority(rootUri.getAuthority()).appendPath(PATH_TREE)
                    .appendPath(getTreeDocumentId(rootUri)).appendPath(PATH_DOCUMENT).appendPath(uuid+":"+fileString).build();
        }
        return null;
    }

    public static String getTreeDocumentId(Uri documentUri) {
        final List<String> paths = documentUri.getPathSegments();
        if (paths.size() >= 2 && PATH_TREE.equals(paths.get(0))) {
            return paths.get(1);
        }
        throw new IllegalArgumentException("Invalid URI: " + documentUri);
    }

    public static Uri getUriFromRootPath(Context context,String rootPath) {


        //root id
        //android provider uses uuid as rootid
        String uuid = ExtStorageManager.getExtStorageManager().getUuid(rootPath);
        if(uuid==null)
            uuid="primary";

        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority("com.android.externalstorage.documents").appendPath(PATH_TREE)
                .appendPath(uuid+":").build();
    }
    public static Uri prepareTreeUri(Uri treeUri) {
        return buildDocumentUriUsingTree(treeUri);
    }

    public static DocumentFile getDocumentFileForUri(Context context, Uri uri) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, ClassNotFoundException {
        Class<?> c = Class.forName("android.support.v4.provider.TreeDocumentFile");
        Constructor<?> constructor = c.getDeclaredConstructor(DocumentFile.class, Context.class, Uri.class);
        constructor.setAccessible(true);
        Uri docUri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            docUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, uri.getLastPathSegment());
        else
            docUri = buildDocumentUriUsingTree(uri);
        return (DocumentFile) constructor.newInstance(null, context, docUri);
    }

    public static Pair<String, String>  getParentUriStringAndFileName(Uri uri){
        //try to get parent
        String last = uri.getLastPathSegment();
        String[] parts = last.split(":");
        int i = 0;
        String parentLastPart = "";
        String filename="";
        parentLastPart+=parts[0]+ Uri.encode(":");
        if(parts.length>1&&parts[1]!=null) {
            parts = parts[1].split("/");
            for (String part : parts) {
                i++;
                if (i != parts.length) {
                    parentLastPart += Uri.encode(part);
                    if (i != parts.length-1)
                        parentLastPart += Uri.encode("/");

                } else
                    filename = part;
            }
        }
        else
            filename = parts[0] ;
        Uri withoutLastPath = Utils.removeLastSegment(uri);
        return new Pair<>(Uri.withAppendedPath(withoutLastPath, parentLastPart).toString(),filename);

    }
    public static Pair<DocumentFile, String> getParentDocumentFileAndFileName(Uri uri)throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, ClassNotFoundException {
        Pair<String, String> pair = getParentUriStringAndFileName(uri);
        String filename=pair.second;
        String parentUri =pair.first;
        try {
            DocumentFile parent = getDocumentFileForUri(ArchosUtils.getGlobalContext(), Uri.parse(parentUri));
            return new Pair<>(parent, filename);
        }
        catch(IllegalArgumentException e){

        }
        return null;
    }
    public static DocumentFile getDocumentFileForUri( Uri uri) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, ClassNotFoundException {
        Pair<DocumentFile, String> pair = getParentDocumentFileAndFileName(uri);
        if (pair!=null){
            DocumentFile child = pair.first.findFile(pair.second);
            if(child!=null) {
                return child;
            }
        }
        return getDocumentFileForUri(ArchosUtils.getGlobalContext(), uri);
    }

    public static String getNameFromContentProvider(Uri uri){
        Cursor c= ArchosUtils.getGlobalContext().getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},null,null, null);
        if(c!=null&&c.getCount()>0&&c.getColumnIndex(OpenableColumns.DISPLAY_NAME)>=0){
            c.moveToFirst();
            return c.getString(  c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        }
        return null;
    }

    public static String getTypeFromContentProvider(Uri uri){
        return ArchosUtils.getGlobalContext().getContentResolver().getType(uri);

    }
}
