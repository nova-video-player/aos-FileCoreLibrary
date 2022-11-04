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
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import com.archos.environment.ArchosUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by noury on 22/06/15.
 */
public class ExtStorageManager {

    private static final int TYPE_PRIVATE = 1;
    private static final Logger log = LoggerFactory.getLogger(ExtStorageManager.class);

    private static ExtStorageManager mExtStorageManager = new ExtStorageManager();

    public static ExtStorageManager getExtStorageManager() {
        log.debug("getExtStorageManager: updateAllVolumes");
        mExtStorageManager.updateAllVolumes();
        return mExtStorageManager;
    }

    public String getUuid(String s) {
        return volumesIdMap.get(s);
    }

    public Integer getStorageId(String s) { return volumesHashMap.get(s);}
    public Integer getStorageId2(String s) { // NOT WORKING since s.startWith(key) is needed not the opposite
        SortedMap<String, Integer> searchResult = volumesHashTreeMap.subMap(s, s + Character.MAX_VALUE);
        if (! searchResult.isEmpty()) {
            return searchResult.get(searchResult.firstKey());
        } else {
            return 1;
        }
    }
    public Integer getStorageId3(String s) {
        Integer volumeId = 1;
        for (Map.Entry<String, Integer> volume : volumesHashMap.entrySet()) {
            String volumeName = volume.getKey();
            if (s.startsWith(volumeName)) {
                volumeId = volume.getValue();
                log.debug("getStorageId3: " + s + "->" + volumeName + ", volumeId:" + volumeId);
            }
        }
        return volumeId;
    }

    public enum ExtStorageType {SDCARD, USBHOST, OTHER};
    private static EnumMap<ExtStorageType, List<String>> volumesMap = new EnumMap<>(ExtStorageType.class);
    private static Map<String, String> volumesIdMap = new HashMap<>();
    private static Map<String, String> volumesDescMap = new HashMap<>();
    private static Map<String, String> volumesLabelMap = new HashMap<>();
    private static Map<String, Integer> volumesHashMap = new HashMap<>();
    private static TreeMap<String, Integer> volumesHashTreeMap = new TreeMap<String, Integer>();

    static {
        volumesMap.put(ExtStorageType.SDCARD, new CopyOnWriteArrayList<String>());
        volumesMap.put(ExtStorageType.USBHOST, new CopyOnWriteArrayList<String>());
        volumesMap.put(ExtStorageType.OTHER, new CopyOnWriteArrayList<String>());
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
            volumesDescMap.clear();
            volumesLabelMap.clear();
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

            final Class noparams[] = {};

            @SuppressWarnings("unchecked")
            Method getService = ServiceManager.getMethod("getService", paramTypes);

            paramTypes[0] = IBinder.class;
            @SuppressWarnings("unchecked")
            Method asInterface = Stub.getMethod("asInterface", paramTypes);

            Method getPath = null;
            if(Build.VERSION.SDK_INT < 30) { // R/30/API11
                // reflection grey listed denied on API30
                getPath = StorageVolume.getMethod("getPath", noparams);
            }

            Method isPrimary = null;
            Method getStorageId = null;
            Method getUuid = null;
            Method findVolumeByUuid = null;

            isPrimary = StorageVolume.getMethod("isPrimary", noparams);
            getUuid = StorageVolume.getMethod("getUuid", noparams);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                findVolumeByUuid = StorageManager.class.getMethod("findVolumeByUuid", new Class[]{String.class});
            }

            Method isMountedReadable = null;
            Method getDisk = null;
            Method isSd = null;
            Method isUsb = null;
            Method getPathfromInfo = null;
            Method getFsUuid = null;
            Method getDescription = null;
            Field type = null;
            Field fsLabel = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getFsUuid = VolumeInfo.getMethod("getFsUuid", noparams);
                isMountedReadable = VolumeInfo.getMethod("isMountedReadable", noparams);
                getDisk = VolumeInfo.getMethod("getDisk", noparams);
                getPathfromInfo = VolumeInfo.getMethod("getPath", noparams);
                isSd = DiskInfo.getMethod("isSd", noparams);
                isUsb = DiskInfo.getMethod("isUsb", noparams);
                type = VolumeInfo.getDeclaredField("type");
                fsLabel = VolumeInfo.getDeclaredField("fsLabel");
                getDescription = DiskInfo.getMethod("getDescription", noparams);
            }

            //Parameters
            Object[] params = new Object[1];
            params[0] = "mount";

            Object mountService = asInterface.invoke(Stub, getService.invoke(ServiceManager, params));

            Context context = ArchosUtils.getGlobalContext();
            StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) { // >=4.2 StorageVolume returned from getVolumeList
                // retrieve StorageVolume[]
                Object[] storageVolumesArray = (StorageVolume[]) IMountService.getMethod("getVolumeList", noparams).invoke(mountService, noparams);
                // getVolumeList present for <O in MountService but @removed from StorageManger >=P
                for (int i = 0; i < storageVolumesArray.length; i++) {
                    if ((isPrimary != null) && (boolean) isPrimary.invoke(storageVolumesArray[i], noparams)) continue;
                    // storage ID is 0x00010001 for primary storage now StorageVolume.STORAGE_ID_PRIMARY
                    if ((getStorageId != null) && (int) getStorageId.invoke(storageVolumesArray[i], noparams) == 0x00010001) continue;
                    if (getPath != null) {
                        String volName = (String) getPath.invoke(storageVolumesArray[i], noparams);
                        String volState = getVolumeState(volName);
                        if ((Environment.MEDIA_MOUNTED.equals(volState) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(volState))) {
                            ExtStorageType volumeType = getVolumeType(volName);
                            volumesMap.get(volumeType).add(volName);
                            if (getUuid != null) {
                                volumesIdMap.put(volName, (String) getUuid.invoke(storageVolumesArray[i], noparams));
                                log.debug("updateAllVolumes: volumes scan result (<N): " + volName + " " + volState);
                            }
                        }
                    } else {
                        log.warn("updateAllVolumes: cannot get volName and volState");
                    }
                }
            }
            //final List<String> mediaReady = Arrays.asList(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY, Environment.MEDIA_UNMOUNTED);
            //final List<String> mediaBorked = Arrays.asList(Environment.MEDIA_UNMOUNTABLE, Environment.MEDIA_BAD_REMOVAL, Environment.MEDIA_NOFS, Environment.MEDIA_UNKNOWN, Environment.MEDIA_REMOVED, Environment.MEDIA_EJECTING);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                List<StorageVolume> storageVolumesList = storageManager.getStorageVolumes(); // >=7.0.0/N/24
                for (StorageVolume storageVolume : storageVolumesList) {
                    if (!storageVolume.isPrimary()) { // >=4.2
                        // retrieve volInfo via uuid and then disk via volInfo
                        // TODO in the future use these instead of introspections that are required for low API levels
                        String uuid = storageVolume.getUuid(); // >=4.4
                        Integer hashCode = storageVolume.hashCode(); // API24(N)+
                        log.debug("updateAllVolumes: NOT USED YET storagevolume uuid=" + uuid + ", hascode=" + hashCode + ", description=" + storageVolume.getDescription(context));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            log.debug("updateAllVolumes: NOT USED YET storagevolume directory=" + storageVolume.getDirectory() + ", mediaStoreVolumeName=" + storageVolume.getMediaStoreVolumeName());
                        }
                        //storageVolume.getMediaStoreVolumeName(); // >=30
                        //String directory = storageVolume.getDirectory().getPath(); // >=30
                        if (uuid != null) {
                            // wait for media to be ready
                            int count = 0;
                            final int maxTries = 10;
                            log.debug("updateAllVolumes: media state " + storageVolume.getState());
                            // Only retry if media is checking
                            //while (!mediaReady.contains(storageVolume.getState()) && !mediaBorked.contains(storageVolume.getState()) && count < maxTries) {
                            // to avoid ANR needs to be based on handlers in UI thread
                            /*
                            while (storageVolume.getState().equals(Environment.MEDIA_CHECKING) && count < maxTries) {
                                log.debug("updateAllVolumes: media checking and not ready yet " + storageVolume.getState() + " try " + String.valueOf(count) + " out of " + String.valueOf(maxTries));
                                SystemClock.sleep(100);
                                count++;
                            }
                            */
                            Object volInfo = findVolumeByUuid.invoke(storageManager, uuid); // >=6.0
                            if ((isMountedReadable != null) && (getPathfromInfo != null) && (boolean) isMountedReadable.invoke(volInfo, noparams)) {
                                String volName = ((File) getPathfromInfo.invoke(volInfo, noparams)).getAbsolutePath(); // >=4.1 (getPath)
                                Object disk = getDisk.invoke(volInfo, noparams); // getDisks is dark greylist but not getDisk >=4.4
                                if (disk != null) {
                                    ExtStorageType volType = null;
                                    String volDescr = (String) getDescription.invoke(disk, noparams); // getDescription is public >=4.4
                                    String volLabel = (String) fsLabel.get(volInfo);
                                    Integer volHash = volName.hashCode();
                                    volType = ((boolean) isSd.invoke(disk, noparams)) ?
                                            ExtStorageType.SDCARD
                                            : ((boolean) isUsb.invoke(disk, noparams)) ?
                                            ExtStorageType.USBHOST : ExtStorageType.OTHER; // isSd and isUsb are public >=4.4
                                    // avoid private type VolumeInfo
                                    if (volType != null && (int) type.get(volInfo) != TYPE_PRIVATE) {
                                        volumesMap.get(volType).add(volName);
                                        if (getFsUuid != null) {
                                            volumesIdMap.put(volName, (String) getFsUuid.invoke(volInfo, noparams));
                                            volumesDescMap.put(volName, volDescr);
                                            volumesLabelMap.put(volName, volLabel);
                                            // for now keep the two
                                            volumesHashMap.put(volName, volHash);
                                            volumesHashTreeMap.put(volName, volHash);
                                            log.debug("updateAllVolumes: volumes scan result (>=N): " + volName + " of type " + volType + " descr: " + volDescr + " label: " + volLabel + " hash: " + volHash);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("updateAllVolumes: caught exception ", e);
        }
    }

    /**
     * Poor's man solution for before N identification of SDCard or USB storage based on volumeName parsing
     */
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
        return copyOf(volumesMap.get(ExtStorageType.SDCARD));
    }

    public List<String> getExtUsbStorages() {
        return copyOf(volumesMap.get(ExtStorageType.USBHOST));
    }

    public List<String> getExtOtherStorages() {
        return copyOf(volumesMap.get(ExtStorageType.OTHER));
    }

    public String getVolumeDesc(String volName) {
        return volumesDescMap.get(volName);
    }

    public String getVolumeLabel(String volName) {
        return volumesLabelMap.get(volName);
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

    /** Creates a copy of a list */
    private static List<String> copyOf(List<String> list) {
        if (list == null)
            return null;
        return new ArrayList<>(list);
    }
}
