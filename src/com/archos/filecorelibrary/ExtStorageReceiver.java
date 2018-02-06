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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import com.archos.environment.ArchosUtils;

public class ExtStorageReceiver extends BroadcastReceiver {
    private static final String TAG = "ExtStorageReceiver";
    public static final String ACTION_MEDIA_MOUNTED  = "com.archos.action.MEDIA_MOUNTED";
    public static final String ACTION_MEDIA_UNMOUNTED = "com.archos.action.MEDIA_UNMOUNTED";
    public static final String VALUE_PATH_NONE = "none";
    public static final String ARCHOS_FILE_SCHEME = "archosfile";
    public static final String ACTION_MEDIA_CHANGED = "com.archos.action.MEDIA_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String uri = intent.getDataString();
        final String path = uri.substring(7);

        if(uri.startsWith("file://")){//file:// will throw exception from android N
            uri = ARCHOS_FILE_SCHEME+"://"+path;
        }

        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();
        if (Environment.getExternalStorageDirectory().getPath().equalsIgnoreCase(path))
            return;
        if (action.equals(Intent.ACTION_MEDIA_MOUNTED)){
            Intent intent1 = new Intent(ACTION_MEDIA_MOUNTED, Uri.parse(uri));
            intent1.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            context.sendBroadcast(intent1);

        } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED) || action.equals(Intent.ACTION_MEDIA_EJECT) || action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)){
            if (path == null || path.isEmpty())
                return;
            Intent intent1 = new Intent(new Intent(ACTION_MEDIA_UNMOUNTED, Uri.parse(uri)));
            intent1.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            context.sendBroadcast(intent1);
        }
    }

}
