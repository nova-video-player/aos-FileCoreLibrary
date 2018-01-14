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

import android.util.Log;

import jcifs.smb.SmbFile;

public class SmbItemData implements Comparable<SmbItemData>{
    private final static String TAG = "SmbItemData";

    // Add here all the available item types (each item type can have its own layout)
    public static final int ITEM_VIEW_TYPE_FILE = 0;        // File or folder
    public static final int ITEM_VIEW_TYPE_SERVER = 1;      // Media server on the network
    public static final int ITEM_VIEW_TYPE_SHORTCUT = 2;    // Network share shortcut
    public static final int ITEM_VIEW_TYPE_TITLE = 3;       // Title (a single line of text aligned to the left)
    public static final int ITEM_VIEW_TYPE_TEXT = 4;        // Text (a single line of text shifted to the right)
    public static final int ITEM_VIEW_TYPE_LONG_TEXT = 5;   // Long text (several lines of centered text displayed in a much higher tile)
    public static final int ITEM_VIEW_TYPE_COUNT = 6;

    private int type;
    private Object data;
    private String path;
    private String shareName = null;
    private String name;
    private Object extraData;
    private boolean available;

    public SmbItemData(int type, Object data, String path, String name) {
        this.type = type;
        this.data = data;
        this.path = path;
        this.name = name;
        this.available = true;
    }
    public SmbItemData(int type, Object data, String path, String name, Object extraData) {
        this.type = type;
        this.data = data;
        this.path = path;
        this.name = name;
        this.extraData = extraData;
        this.available = true;
    }

    public int getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public String getTextData() {
        if (data instanceof String) {
            return (String)data;
        }
        return null;
    }

    public SmbFile getSmbFile() {
        if (data instanceof SmbFile) {
            return (SmbFile)data;
        }
        return null;
    }

    public boolean isTextItem() {
        return (type == ITEM_VIEW_TYPE_TITLE || type == ITEM_VIEW_TYPE_TEXT || type == ITEM_VIEW_TYPE_LONG_TEXT);
    }

    public String getName() {
        if (data instanceof String) {
            return (String)data;
        }
        else {
            return name;
        }
    }

    public String getPath(){
        return path;
    }

    public String getShareName() {
        return shareName;
    }
    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getShortcutPath(){
        // Regular expression for a valid IP address
        final String IP_ADDRESS_PATTERN = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\."
                                        + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." + "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

        final String sambaPrefix = "smb://";
        final int sambaPrefixLength = sambaPrefix.length();
        boolean appendShareName = true;

        String path = getPath();
        String partialPath;

        // Check the position of the first '@' and "/" if any
        int atPosition = path.indexOf('@');
        int slashPosition = path.indexOf('/', sambaPrefixLength);

        if (atPosition < 0) {
            //----------------------------------------------------------------------------
            // The path does not contain any '@'
            // => there are no login/password info
            // (Ex: smb://192.168.1.1/my_folder)
            //----------------------------------------------------------------------------
            // Make sure the substring between the prefix and the first '/' is an IP address
            partialPath = path.substring(sambaPrefixLength, slashPosition);
            if (!partialPath.matches(IP_ADDRESS_PATTERN)) {
                // Error : what can we do???

                Log.e(TAG, "getShortcutPath error: unknown path format " + path);
                return path;
            }
        }
        else if (slashPosition < atPosition) {
            //----------------------------------------------------------------------------
            // The path contains first a '/' and then a '@'
            // => there are no login/password info but at least one of the folders contains a '@'
            // (Ex: smb://192.168.1.1/my@folder)
            //----------------------------------------------------------------------------
            // Make sure the substring between the prefix and the first '/' is an IP address
            partialPath = path.substring(sambaPrefixLength, slashPosition);
            if (!partialPath.matches(IP_ADDRESS_PATTERN)) {
                // Error : what can we do???
                Log.e(TAG, "getShortcutPath error: unknown path format " + path);
                Log.e(TAG, "Maybe the user name or password contains a '/'?");
                return path;
            }
        }
        else {
            //----------------------------------------------------------------------------
            // The path contains first a '@' and then a '/'
            // => there are login/password info
            // (Ex: smb://login:password@192.168.1.1/my_folder)
            //----------------------------------------------------------------------------
            // Make sure the substring between the first '@' and the first '/' is an IP address
            partialPath = path.substring(atPosition + 1, slashPosition);
            if (!partialPath.matches(IP_ADDRESS_PATTERN)) {
                // Sharename address
                appendShareName = false;
            }
        }

        // Build the shortcut path by adding the prefix + the share name + what follows the first '/' of the full path
        // (this gives a wrong path when one of the previous errors occured but there is no easy solution in that case)
        StringBuilder sb = new StringBuilder(sambaPrefix);
        if (appendShareName){
            sb.append(getShareName());
            sb.append(path.substring(slashPosition));
        } else {
            sb.append(path.substring(atPosition+1));
        }
        return sb.toString();
    }

    public boolean isDirectory(){
        return getPath().endsWith("/");
    }

    public Object getExtraData() {
        return extraData;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return available;
    }

    public int compareTo(SmbItemData another) {
        int ret = name.toLowerCase().compareTo(another.getName().toLowerCase());
        if (ret == 0) {
            // Same filename so compare the name with the extension.
            String pathOther = another.getPath();
            char pathSeparatorChar = System.getProperty("path.separator", ":").charAt(0);
            int index = path.lastIndexOf(pathSeparatorChar) + 1;
            int indexOther = pathOther.lastIndexOf(pathSeparatorChar) + 1;
            ret = path.substring(index).toLowerCase()
                    .compareTo(pathOther.substring(indexOther).toLowerCase());
        }
        return ret;
    }
}
