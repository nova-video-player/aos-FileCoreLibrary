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

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.util.Log;

import com.android.dx.stock.ProxyBuilder;
import com.archos.environment.ArchosUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by noury on 22/06/15.
 */
public class ExtStorageManager {

    private static final String TAG = "ExtStorageManager";
    private static final    int TYPE_PRIVATE = 1;
    private  StorageManager mStorageManager;

    private ExtStorageManager() {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // when M, register listener to storage manager
            mStorageManager = (StorageManager)ArchosUtils.getGlobalContext().getSystemService(StorageManager.class);
            try {

                Class StorageEventListener = cl.loadClass("android.os.storage.StorageEventListener");
                Class[]classes = new Class[1];
                classes[0] = StorageEventListener;

                Object instance = ProxyBuilder.forClass(StorageEventListener).handler(
                        new InvocationHandler() {

                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                //Handle the invocations
                                if(method.getName().equals("onVolumeStateChanged")){
                                    Intent intent = new Intent();
                                    intent.setData(Uri.parse(ExtStorageReceiver.ARCHOS_FILE_SCHEME+"://none"));
                                    intent.setAction(ExtStorageReceiver.ACTION_MEDIA_CHANGED);
                                    intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
                                    ArchosUtils.getGlobalContext().sendBroadcast(intent);
                                    return 1;
                                }
                                else return -1;
                            }
                        }
                ).dexCache(ArchosUtils.getGlobalContext().getCacheDir()).build();

                Method method = mStorageManager.getClass().getMethod("registerListener",   classes);
                method.setAccessible(true);
                method.invoke(mStorageManager, instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }
    private static ExtStorageManager mExtStorageManager = new ExtStorageManager();
    public static ExtStorageManager getExtStorageManager() {
        mExtStorageManager.updateAllVolumes();
        return mExtStorageManager;
    }

    public String getUuid(String s) {
        return volumesIdMap.get(s);
    }

    public enum ExtStorageType {SDCARD, USBHOST, OTHER};
    private static EnumMap<ExtStorageType, List<String>> volumesMap = new EnumMap<>(ExtStorageType.class);
    private static Map<String, String> volumesIdMap = new HashMap<>();

    static {
        volumesMap.put(ExtStorageType.SDCARD, new ArrayList<String>(1));
        volumesMap.put(ExtStorageType.USBHOST, new ArrayList<String>(4));
        volumesMap.put(ExtStorageType.OTHER, new ArrayList<String>(2));
    }

    private static final File EXTERNAL_STORAGE_USBHOST_PTP_DIRECTORY = new File("/mnt/ext_camera");

    /**
     * Gets the Android external storage USB PTP directory.
     */
    public static File getExternalStorageUsbHostPtpDirectory() {
        return EXTERNAL_STORAGE_USBHOST_PTP_DIRECTORY;
    }

    /**
     * Gets the current state of the external USB host PTP device.
     */
    public static String getExternalStorageUsbHostPtpState() {
        return getVolumeState(getExternalStorageUsbHostPtpDirectory().toString());
    }

    private void updateAllVolumes() {
        try {
            volumesIdMap.clear();
            for (List<String> vol : volumesMap.values()) {
                vol.clear();
            }
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            @SuppressWarnings("rawtypes")
            Class ServiceManager = cl.loadClass("android.os.ServiceManager");
            Class IMountService;
            Class Stub;
             if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                IMountService = cl.loadClass("android.os.storage.IStorageManager");
                Stub = cl.loadClass("android.os.storage.IStorageManager$Stub");
            }else{
                IMountService = cl.loadClass("android.os.storage.IMountService");
                Stub = cl.loadClass("android.os.storage.IMountService$Stub");
            }
            Class StorageVolume = cl.loadClass("android.os.storage.StorageVolume");
            Class VolumeInfo = null;
            Class DiskInfo = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                VolumeInfo = cl.loadClass("android.os.storage.VolumeInfo");
                DiskInfo = cl.loadClass("android.os.storage.DiskInfo");
            }

            //Parameters Types
            @SuppressWarnings("rawtypes")
            Class[] paramTypes = new Class[1];
            paramTypes[0] = String.class;

            Class noparams[] = {};

            @SuppressWarnings("unchecked")
            Method getService = ServiceManager.getMethod("getService", paramTypes);

            paramTypes[0] = IBinder.class;
            @SuppressWarnings("unchecked")
            Method asInterface = Stub.getMethod("asInterface", paramTypes);

            @SuppressWarnings("unchecked")
            Method getPath = StorageVolume.getMethod("getPath", noparams);

            Method isPrimary = null;
            Method getStorageId = null;
            Method getUuid = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                isPrimary = StorageVolume.getMethod("isPrimary", noparams);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                    getUuid = StorageVolume.getMethod("getUuid", noparams);
            } else {
                getStorageId = StorageVolume.getMethod("getStorageId", noparams);
            }

            Method isMountedReadable = null;
            Method getDisk = null;
            Method isSd = null;
            Method isUsb = null;
            Method getPathfromInfo = null;
            Method getFsUuid = null;
            Field type= null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getFsUuid = VolumeInfo.getMethod("getFsUuid", noparams);
                isMountedReadable = VolumeInfo.getMethod("isMountedReadable", noparams);
                getDisk = VolumeInfo.getMethod("getDisk", noparams);
                getPathfromInfo = VolumeInfo.getMethod("getPath", noparams);
                isSd = DiskInfo.getMethod("isSd", noparams);
                isUsb = DiskInfo.getMethod("isUsb", noparams);
                type = VolumeInfo.getDeclaredField("type");
            }

            //Parameters
            Object[] params = new Object[1];
            params[0] = "mount";

            Object mountService = asInterface.invoke(Stub, getService.invoke(ServiceManager, params));

            Method getVolumeList;
            Object[] volumes = null;
            Object[] volumesInfo = null;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                getVolumeList = IMountService.getMethod("getVolumeList", noparams);
                volumes = (Object[]) getVolumeList.invoke(mountService, noparams);
            }
            else {
                Class[] paramsType2 = new Class[1];
                paramsType2[0] = int.class;
                Method getVolumes = IMountService.getMethod("getVolumes", paramsType2);
                Object[] params2 = new Object[1];
                params2[0] = 0;
                volumesInfo = (Object[]) getVolumes.invoke(mountService, params2);
            }
            if (volumes != null)
                for (int i = 0 ; i < volumes.length; i++) {
                    if ((isPrimary != null) && (boolean) isPrimary.invoke(volumes[i], noparams)) {
                        continue;
                    }
                    if ((getStorageId != null) && (int) getStorageId.invoke(volumes[i], noparams) == 0x00010001) {
                        continue;
                    }

                    String volumeName = (String) getPath.invoke(volumes[i], noparams);

                    String volumeState = getVolumeState(volumeName);
                    Log.d(TAG, volumeName + " " + volumeState);
                    if ((Environment.MEDIA_MOUNTED.equals(volumeState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(volumeState))) {
                        ExtStorageType volumeType = getVolumeType(volumeName);
                        volumesMap.get(volumeType).add(volumeName);
                        if(getUuid!=null)
                            volumesIdMap.put(volumeName,(String) getUuid.invoke(volumes[i], noparams));
                    }
                }
            else if (volumesInfo != null) {
                for (int i = 0 ; i < volumesInfo.length; i++) {

                    if ((isMountedReadable != null) && (getPathfromInfo != null) && (boolean) isMountedReadable.invoke(volumesInfo[i], noparams)) {
                        String volumeName = ((File) getPathfromInfo.invoke(volumesInfo[i], noparams)).getAbsolutePath();

                        Object disk = getDisk.invoke(volumesInfo[i], noparams);
                        ExtStorageType volumeType = null;
                        if (disk != null) {
                            volumeType = ((boolean) isSd.invoke(disk, noparams)) ?
                                    ExtStorageType.SDCARD
                                    : ((boolean) isUsb.invoke(disk, noparams)) ?
                                    ExtStorageType.USBHOST : ExtStorageType.OTHER;
                            Log.d(TAG, volumeName + " " + volumeType);
                            if(type!=null&&(int)type.get(volumesInfo[i])!=TYPE_PRIVATE){
                                volumesMap.get(volumeType).add(volumeName);

                                if (getFsUuid != null)
                                    volumesIdMap.put(volumeName, (String) getFsUuid.invoke(volumesInfo[i], noparams));

                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ExtStorageType getVolumeType(String volumeName) {
        if (volumeName.toLowerCase().contains("sd") && !volumeName.toLowerCase().contains("usb"))
            return ExtStorageType.SDCARD;
        else if (volumeName.toLowerCase().contains("usb"))
            return ExtStorageType.USBHOST;
        else
            return ExtStorageType.OTHER;
    }

    public boolean hasExtStorage() {
        return !getExtSdcards().isEmpty() || !getExtUsbStorages().isEmpty() || !getExtOtherStorages().isEmpty();
    }

    public List<String> getExtSdcards() {
        return volumesMap.get(ExtStorageType.SDCARD);
    }

    public List<String> getExtUsbStorages() {
        return volumesMap.get(ExtStorageType.USBHOST);
    }

    public List<String> getExtOtherStorages() {
        return volumesMap.get(ExtStorageType.OTHER);
    }

    /**
     * Gets the current state of a volume by mountPoint
     */
    public static String getVolumeState(String mountPoint) {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();

            @SuppressWarnings("rawtypes")
            Class ServiceManager = cl.loadClass("android.os.ServiceManager");
            Class IMountService = cl.loadClass("android.os.storage.IMountService");
            Class Stub = cl.loadClass("android.os.storage.IMountService$Stub");

            //Parameters Types
            @SuppressWarnings("rawtypes")
            Class[] paramTypes = new Class[1];
            paramTypes[0] = String.class;

            @SuppressWarnings("unchecked")
            Method getService = ServiceManager.getMethod("getService", paramTypes);
            @SuppressWarnings("unchecked")
            Method getVolumeState = IMountService.getMethod("getVolumeState", paramTypes);

            paramTypes[0] = IBinder.class;
            @SuppressWarnings("unchecked")
            Method asInterface = Stub.getMethod("asInterface", paramTypes);

            //Parameters
            Object[] params = new Object[1];
            params[0] = "mount";

            Object[] params2 = new Object[1];
            params2[0] = mountPoint;

            return (String) getVolumeState.invoke(asInterface.invoke(Stub, getService.invoke(ServiceManager, params)), params2);
        } catch (Exception e) {
            return Environment.MEDIA_REMOVED;
        }
    }
}
