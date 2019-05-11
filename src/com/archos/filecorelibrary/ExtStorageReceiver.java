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
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.archos.environment.ArchosUtils;

import java.util.Arrays;
import java.util.List;

import static com.archos.filecorelibrary.FileUtils.intentToString;

public class ExtStorageReceiver extends BroadcastReceiver {
    private static final String TAG = "ExtStorageReceiver";
    private static boolean DBG = false;
    public static final String ACTION_MEDIA_MOUNTED  = "com.archos.action.MEDIA_MOUNTED";
    public static final String ACTION_MEDIA_UNMOUNTED = "com.archos.action.MEDIA_UNMOUNTED";
    public static final String VALUE_PATH_NONE = "none";
    public static final String ARCHOS_FILE_SCHEME = "archosfile";
    public static final String ACTION_MEDIA_CHANGED = "com.archos.action.MEDIA_CHANGED";
    private static final int WAIT_FOR_MOUNT_MS = 3000;

    static final List<String> mediaActions = Arrays.asList(Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_BAD_REMOVAL);

    @Override
    public void onReceive(Context context, Intent intent) {
        if (DBG) Log.d(TAG, "INTENT = " + intentToString(intent) );
        String action = intent.getAction();
        String uri = null;
        String path = null;
        Intent intentManager = null;

        // Even if storageManager is not used, this triggers an updateAllVolumes() scan: keep it!
        ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager();

        if (Environment.getExternalStorageDirectory().getPath().equalsIgnoreCase(path))
            return;

        if (mediaActions.contains(action)) {
            uri = intent.getDataString();
            path = uri.substring(7);
            //file:// will throw exception from android N
            if(uri.startsWith("file://")) uri = ARCHOS_FILE_SCHEME+"://"+path;
            if (DBG) Log.d(TAG,"uri is " + uri);
        }

        switch (action) {
            case Intent.ACTION_MEDIA_MOUNTED:
                if (DBG) Log.d(TAG,"media mounted " + uri);
                //StorageVolume volume = (StorageVolume) intent.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
                intentManager = new Intent(ACTION_MEDIA_MOUNTED, Uri.parse(uri));
                intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                context.sendBroadcast(intentManager);
                break;
            case Intent.ACTION_MEDIA_UNMOUNTED:
            case Intent.ACTION_MEDIA_EJECT:
            case Intent.ACTION_MEDIA_BAD_REMOVAL:
                if (DBG) Log.d(TAG,"media removed " + uri);
                if (path == null || path.isEmpty()) return;
                intentManager = new Intent(ACTION_MEDIA_UNMOUNTED, Uri.parse(uri));
                intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                context.sendBroadcast(intentManager);
                break;
            // more clever stuff could be done when detecting USB device attached but for now we only throw logs
            case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                // TODO: find which StorageVolume from UsbDevice to simplify. Not sure it is possible
                if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    UsbInterface usbInterface = device.getInterface(0);
                    path = device.getDeviceName();
                    if(usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE && path != null) {
                        Log.d(TAG, "USB mass storage " + path + " attached");
                        intentManager = new Intent(ACTION_MEDIA_MOUNTED, Uri.parse(ARCHOS_FILE_SCHEME+"://none"));
                        intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                        //SystemClock.sleep(WAIT_FOR_MOUNT_MS); // wait in order to get the device mounted (for sdcard on pixel you do not get MEDIA_MOUNTED intent)
                        context.sendBroadcast(intentManager);
                        break;
                    }
                }
                break;
            case UsbManager.ACTION_USB_DEVICE_DETACHED:
                if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    UsbInterface usbInterface = device.getInterface(0);
                    path = device.getDeviceName();
                    if(usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE && path != null) {
                        Log.d(TAG, "USB mass storage " + path + " removed");
                        intentManager = new Intent(ACTION_MEDIA_UNMOUNTED, Uri.parse(ARCHOS_FILE_SCHEME+"://none"));
                        intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                        context.sendBroadcast(intentManager);
                    }
                }
                break;
        }
    }
}
