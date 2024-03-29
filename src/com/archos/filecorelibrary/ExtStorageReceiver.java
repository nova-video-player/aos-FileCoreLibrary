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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.archos.environment.ArchosUtils;

import java.util.Arrays;
import java.util.List;

import static com.archos.filecorelibrary.FileUtils.intentToString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtStorageReceiver extends BroadcastReceiver {
    private static final Logger log = LoggerFactory.getLogger(ExtStorageReceiver.class);
    public static final String ACTION_MEDIA_MOUNTED  = "com.archos.action.MEDIA_MOUNTED";
    public static final String ACTION_MEDIA_UNMOUNTED = "com.archos.action.MEDIA_UNMOUNTED";
    public static final String VALUE_PATH_NONE = "none";
    public static final String ARCHOS_FILE_SCHEME = "archosfile";
    public static final String ACTION_MEDIA_CHANGED = "com.archos.action.MEDIA_CHANGED";
    private static final int WAIT_FOR_MOUNT_MS = 3000;

    static final List<String> mediaActions = Arrays.asList(Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_BAD_REMOVAL);

    private static HandlerThread handlerThread = null;
    private static Looper looper = null;
    private static Handler handler = null;

    public ExtStorageReceiver() {
        log.debug("ExtStorageReceiver constructor");
        if(handlerThread == null) {
            log.debug("ExtStorageReceiver: handlerThread null starting thread");
            handlerThread = new HandlerThread("ExtStorageReceiver");
            handlerThread.start();
            looper = handlerThread.getLooper();
            handler = new Handler(looper);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        log.debug("onReceive: INTENT = " + intentToString(intent));

        final Intent mIntent = intent;
        final Context mContext = context;

        handler.post(new Runnable() {
            @Override
            public void run() {
                String action = mIntent.getAction();
                String uri = null;
                String path = null;
                Intent intentManager = null;

                // Even if storageManager is not used, this triggers an updateAllVolumes() scan: keep it!
                ExtStorageManager storageManager = ExtStorageManager.getExtStorageManager(); // this can create ANR with hdd spinup delay if not put in handleThread

                if (Environment.getExternalStorageDirectory().getPath().equalsIgnoreCase(path))
                    return;

                if (mediaActions.contains(action)) {
                    uri = mIntent.getDataString();
                    // uri can be null according to sentry
                    if (uri != null) {
                        path = uri.substring(7);
                        //file:// will throw exception from android N
                        if (uri.startsWith("file://")) uri = ARCHOS_FILE_SCHEME + "://" + path;
                        log.debug("onReceive: uri is " + uri);
                    } else {
                        log.warn("onReceive: uri is null for action " + action + "!");
                    }
                }

                switch (action) {
                    case Intent.ACTION_MEDIA_MOUNTED:
                        log.debug("onReceive: media mounted " + uri);
                        //StorageVolume volume = (StorageVolume) intent.getParcelableExtra(StorageVolume.EXTRA_STORAGE_VOLUME);
                        intentManager = new Intent(ACTION_MEDIA_MOUNTED, Uri.parse(uri));
                        intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                        mContext.sendBroadcast(intentManager);
                        break;
                    case Intent.ACTION_MEDIA_UNMOUNTED:
                    case Intent.ACTION_MEDIA_EJECT:
                    case Intent.ACTION_MEDIA_BAD_REMOVAL:
                        log.debug("onReceive: media removed " + uri);
                        if (path == null || path.isEmpty()) return;
                        intentManager = new Intent(ACTION_MEDIA_UNMOUNTED, Uri.parse(uri));
                        intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                        mContext.sendBroadcast(intentManager);
                        break;
                    // more clever stuff could be done when detecting USB device attached but for now we only throw logs
                    // disabled in AndroidManifest for now since it gets triggered a lot on Sony TVs and causes full rescan
                    case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                        log.debug("onReceive: usb device attached");
                        if (mIntent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                            final UsbDevice device = mIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                            boolean isMassStorage = false;
                            // sometimes device.getInterfaceCount() = 0 causing an error
                            for (int i = 0; i < device.getInterfaceCount(); i++) {
                                final UsbInterface usbInterface = device.getInterface(i);
                                path = device.getDeviceName();
                                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE && path != null) {
                                    log.debug("onReceive: USB mass storage " + path + " attached");
                                    isMassStorage = true;
                                }
                            }
                            if (isMassStorage) {
                                intentManager = new Intent(ACTION_MEDIA_MOUNTED, Uri.parse(ARCHOS_FILE_SCHEME + "://none"));
                                intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                                //SystemClock.sleep(WAIT_FOR_MOUNT_MS); // wait in order to get the device mounted (for sdcard on pixel you do not get MEDIA_MOUNTED intent)
                                mContext.sendBroadcast(intentManager);
                            }
                        }
                        break;
                    case UsbManager.ACTION_USB_DEVICE_DETACHED:
                        log.debug("onReceive: usb device detached");
                        if (mIntent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                            final UsbDevice device = mIntent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                            boolean isMassStorage = false;
                            for (int i = 0; i < device.getInterfaceCount(); i++) {
                                final UsbInterface usbInterface = device.getInterface(i);
                                path = device.getDeviceName();
                                if (usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_MASS_STORAGE && path != null) {
                                    log.debug("onReceive: USB mass storage " + path + " detached");
                                    isMassStorage = true;
                                }
                            }
                            if (isMassStorage) {
                                intentManager = new Intent(ACTION_MEDIA_UNMOUNTED, Uri.parse(ARCHOS_FILE_SCHEME + "://none"));
                                intentManager.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                                mContext.sendBroadcast(intentManager);
                            }
                        }
                        break;
                }
            }
        });
    }
}
