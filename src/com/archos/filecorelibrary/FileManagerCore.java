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

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.MetaFile.FileType;
import com.archos.filecorelibrary.samba.SambaConfiguration;
import com.archos.environment.ArchosUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.NetworkOnMainThreadException;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import jcifs2.netbios.Name;
import jcifs2.netbios.NbtAddress;
import jcifs2.netbios.NodeStatusRequest;
import jcifs2.netbios.NodeStatusResponse;
import jcifs2.smb.SmbAuthException;
import jcifs2.smb.SmbException;
import jcifs2.smb.SmbFile;
import jcifs2.smb.SmbFileInputStream;
import jcifs2.smb.SmbFileOutputStream;

public class FileManagerCore extends Observable {
    private static final String TAG = "FileManagerCore";

    private static final String ZIP_FW_UPDATE_FILE_NAME = "update.zip"; // Used to guess correct mime type for zip update files from 3rd party suppliers

    public static final int LISTING_ACTION = 0;
    public static final int DELETING_ACTION = 1;
    public static final int PASTING_ACTION = 2;
    public static final int PASTING_PROGRESS = 3;
    public static final int PASTING_CANCELLED = 4;
    public static final int DELETING_CANCELLED = 5;
    public static final int XML_PARSE_OK = 6;
    public static final int UPNP_LISTING = 7;
    public static final int INIT_ACTION = 8;
    public static final int SMB_TIMEOUT = 9;
    public static final int LISTING_SAMBA_DONE = 10;
    public static final int LISTING_SSH_DONE = 11;
    public static final int PASTING_DETAILED_PROGRESS = 12;
    public static final int LISTING_ERROR = 0;
    public static final int LISTING_SUCCESS = 1;
    public static final int LISTING_CANNOT_LIST = 2;
    public static final int LISTING_NO_PERMISSION = 3;
    public static final int LISTING_SAMBA = 4;
    public static final int LISTING_ZIP = 5;
    public static final int LISTING_SSH = 6;
    public static final int LISTING_HOST_ERROR = 7;
    public static final int LISTING_AUTH_FAIL = 8;

    private static final int DELETE = 0;
    private static final int COPY = 1;
    private static final int CUT = 2;

    private static final int SMB_NS_PORT = 137;
    private static final int SMB_NS_TIMEOUT = 2000;
    private static final int SMB_LISTING_TIMEOUT = 30000;

    private static final String MUSIC_PATH = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC).getPath();

    private static final String PICTURES_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath();
    private static final String SDCARD_PATH = "TODO REMOVE";
    private static final String USBHOST_PTP_PATH = ExtStorageManager.getExternalStorageUsbHostPtpDirectory().getPath();

    private static final String USB_PATH = "TODO REMOVE";
    private static final String VIDEO_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).getPath();

    private static final File STORAGE = Environment.getExternalStorageDirectory();

    public static final int FOLDER_STATUS_OK = 0;
    public static final int FOLDER_STATUS_PROTECTED = 1;
    public static final int FOLDER_STATUS_ERROR = 2;

    private static final String[] DIR_PATHS_FILTER = {
        (new File(STORAGE, "System")).getPath(), SDCARD_PATH, USB_PATH,
        USBHOST_PTP_PATH
    };

    private static final String[] FILE_PATHS_FILTER = {
        (new File(Environment.getExternalStorageDirectory(), "DevIcon.fil")).getPath()
    };

    private static final String[] DIR_NAMES_FILTER = {
        "_arcthumb", "lost+found", "_search"
    };
    private static HashMap<String, SmbItemData> mWorkgroupsList;

    /**
     * These directories cannot be deleted, renamed, moved, ...
     */
    public static final String[] DO_NOT_TOUCH = {
        (new File(STORAGE, "dcim")).getPath(), MUSIC_PATH, PICTURES_PATH, VIDEO_PATH,
        SDCARD_PATH, USB_PATH, USBHOST_PTP_PATH
    };

    private final Context context;
    private CxCcCv cxCcCv;
    private final HashSet<String> dirPathsFiltered;
    private final HashSet<String> dirNamesFiltered;
    private final HashSet<String> doNotTouch;
    private final HashSet<String> filePathsFiltered;
    private ListThread listThread;
    private SmbListThread smbListThread;
    private ListZipThread zipListThread;
    private static String mFileCopyString;

    /**
     * Calling notifyObserver in a thread causes issues, so I use a handler.
     * Besides, I am sure to call setChanged.
     */
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == SMB_TIMEOUT)
                stopSMBListing();
            removeMessages(SMB_TIMEOUT);
            setChanged();
            notifyObservers(msg);
        }
    };
    public static class CopyMessage{
        public int currentFile;
        public MetaFile currentMetaFile;
        public long currentFileProgress;
        public long currentFileSize;
        public long totalProgress;
        public long totalSize;
        public CopyMessage(int currentFile, MetaFile currentMetaFile,long currentFileProgress,long currentFileSize, long totalProgress, long totalSize){
            this.currentFile = currentFile;
            this.currentMetaFile = currentMetaFile;
            this.currentFileProgress = currentFileProgress;
            this.currentFileSize = currentFileSize;
            this.totalProgress = totalProgress;
            this.totalSize = totalSize;
        }
    }

    private final class SmbListThread extends Thread {
        private final SmbFile file;
        private final String filter;
        private boolean stopThread = false;
        private final boolean refresh;

        public SmbListThread(SmbFile f, String s, boolean refresh){
            super();
            file = f;
            filter = s;
            this.refresh = refresh;
        }

        private void smbDiscovery(){
            mWorkgroupsList = new HashMap<String, SmbItemData>();
            String ipAddress = getIpAddress();
            String name, address;
            StringBuilder sb;
            final ArrayList<SmbItemData> directories = new ArrayList<SmbItemData>();
            NbtAddress nbtAddress = null;
            String netRange = ipAddress.substring(0,ipAddress.lastIndexOf(".")+1);
            String IP;
            byte[] snd_buf = new byte[576], rcv_buf = new byte[576];
            DatagramSocket socket = null;
            DatagramPacket out,in = new DatagramPacket(rcv_buf, rcv_buf.length);
            NodeStatusRequest request = null;
            NodeStatusResponse response = null;
            NbtAddress[] addrs = null;
            boolean useIp = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SambaConfiguration.PREF_USEIP_KEY, SambaConfiguration.DEFAULT_USE_IP);
            try {
                socket = new DatagramSocket();

                //Send node status request to each IP un available rang
                for (int i = 1; i < 255; i++) {
                    try {
                        IP = netRange.concat(String.valueOf(i));
                        nbtAddress = NbtAddress.getByName(IP);
                        out = new DatagramPacket(snd_buf, snd_buf.length, nbtAddress.getInetAddress(), SMB_NS_PORT);
                        response = new NodeStatusResponse(nbtAddress);
                        request = new NodeStatusRequest(new Name(NbtAddress.ANY_HOSTS_NAME, 0x00, null));
                        request.addr = nbtAddress.getInetAddress();
                        request.nameTrnId = 1; //fixed to 1, no retry.
                        out.setLength(request.writeWireFormat(snd_buf, 0));
                        response.received = false;
                        socket.send(out);
                    } catch (UnknownHostException e) {}
                    catch (IOException e) {}
                }
                // Listen to responses, these are the alive samba shares
                socket.setSoTimeout(SMB_NS_TIMEOUT);
                while (true) {
                    hasToStop();
                    in.setLength(rcv_buf.length);
                    socket.receive(in);
                    response.readWireFormat( rcv_buf, 0 );
                    addrs = response.addressArray;
                    String workgroup = "nogroup";
                    //first loop to find workgroup
                    for (NbtAddress addr : addrs){
                        if(addr.isGroupAddress() && !addr.getHostName().equalsIgnoreCase(NbtAddress.MASTER_BROWSER_NAME)){
                            workgroup = addr.getHostName();
                            break;
                        }
                    }
                    // check server name and add to workgroup
                    for (NbtAddress addr : addrs){
                        if(!addr.isGroupAddress()){
                            try {
                                name = addr.getHostName();
                                if (useIp){
                                    sb = new StringBuilder("smb:/").append(in.getAddress()).append('/');
                                }else {
                                    sb = new StringBuilder("smb://").append(addr.getHostName()).append('/');
                                }
                                address = sb.toString();
                                SmbItemData share = new SmbItemData(SmbItemData.ITEM_VIEW_TYPE_SERVER, new SmbFile(address), address, name);
                                share.setShareName(name);
                                //add share to existing workgroup
                                if (mWorkgroupsList.containsKey(workgroup)){
                                    ((ArrayList<SmbItemData>)mWorkgroupsList.get(workgroup).getExtraData()).add(share);
                                    //create new workgroup with this share
                                } else {
                                    sb = new StringBuilder("smb://").append(workgroup).append('/');
                                    ArrayList<SmbItemData> extraData = new ArrayList<SmbItemData>();
                                    extraData.add(share);
                                    mWorkgroupsList.put(workgroup, new SmbItemData(SmbItemData.ITEM_VIEW_TYPE_SERVER, new SmbFile(sb.toString()), sb.toString(), workgroup, extraData));
                                }
                                directories.clear();
                                directories.addAll(mWorkgroupsList.values());
                                final ArrayList<SmbItemData> directoriesCopy = new ArrayList<SmbItemData>(directories); // avoid ConcurrentModificationException in FileManager2...
                                if (!stopThread && !refresh) {
                                    mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_SAMBA, directoriesCopy).sendToTarget();
                                }
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    }
                    hasToStop();
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (SocketTimeoutException e) {
                // normal discovery finish
                if (!stopThread) {
                    if (refresh) {
                        final ArrayList<SmbItemData> directoriesCopy = new ArrayList<SmbItemData>(directories); // avoid ConcurrentModificationException in FileManager2...
                        mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_SAMBA, directoriesCopy).sendToTarget();
                    }
                    mHandler.obtainMessage(LISTING_SAMBA_DONE).sendToTarget();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeSilently(socket);
            }
            if (!stopThread && !refresh) {
                if (directories.isEmpty()) {
                    Log.d(TAG, "smbDiscovery:no workgroup found, sending empty list");
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_SAMBA, new ArrayList<SmbItemData>()).sendToTarget();
                }
            }
        }

        public void run(){
            mHandler.sendEmptyMessageDelayed(SMB_TIMEOUT, SMB_LISTING_TIMEOUT);
            if (file.getCanonicalPath().equalsIgnoreCase("smb://")){ // We do our own smb discovery
                smbDiscovery();
                return;
            }
            String name = file.getName().substring(0, file.getName().length()-1);
            final ArrayList<SmbItemData> directories = new ArrayList<SmbItemData>();
            final ArrayList<SmbItemData> files = new ArrayList<SmbItemData>();
            final ArrayList<SmbItemData> allFiles = new ArrayList<SmbItemData>();
            try {
                //Special process for refreshing a workgroup
                if (refresh && mWorkgroupsList != null && mWorkgroupsList.containsKey(name)) {
                    smbDiscovery();
                }
                //this is a workgroup we have saved
                if (mWorkgroupsList != null && mWorkgroupsList.containsKey(name)) {
                    directories.addAll((ArrayList<SmbItemData>)mWorkgroupsList.get(name).getExtraData());
                    Collections.sort(directories);
                    hasToStop();
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_SAMBA, directories).sendToTarget();
                    mHandler.obtainMessage(LISTING_SAMBA_DONE).sendToTarget();
                    // normal jcifs browsing for other directories
                } else if (file.exists() && file.canRead()){

                    SmbFile[] listFiles = file.listFiles();
                    String fileName;
                    String mimeType;
                    // Error in reading the directory.
                    if (listFiles == null) {
                        mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_ERROR).sendToTarget();
                        return;
                    }
                    long lastTime = System.currentTimeMillis();
                    for (SmbFile f : listFiles) {
                        if (f.isFile()) {
                            fileName = f.getName();
                            if (filter == null || filter.equalsIgnoreCase(""))
                                mimeType = null;
                            else
                                mimeType = MimeUtils.guessMimeTypeFromExtension(fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase());
                            if (!fileName.startsWith(".") && filter == null || (mimeType != null && mimeType.startsWith(filter))){
                                files.add(new SmbItemData(SmbItemData.ITEM_VIEW_TYPE_FILE, f, f.getPath(), f.getName()));
                            } else
                                continue;
                        }
                        else if (f.isDirectory()) {
                            fileName = f.getName();
                            if (fileName.startsWith(".") || fileName.equalsIgnoreCase("IPC$/") || fileName.equalsIgnoreCase("print$/"))
                                continue;
                            directories.add(new SmbItemData(SmbItemData.ITEM_VIEW_TYPE_FILE, f, f.getPath(), fileName));
                        }
                        else
                            continue;
                        hasToStop();
                        if (refresh) {
                            if (System.currentTimeMillis() - lastTime > 500) {
                                mHandler.removeMessages(LISTING_ACTION);
                                allFiles.clear();
                                if (!directories.isEmpty()) {
                                    Collections.sort(directories);
                                    allFiles.addAll(directories);
                                }
                                if (!files.isEmpty()) {
                                    Collections.sort(files);
                                    allFiles.addAll(files);
                                }
                                mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_SAMBA, allFiles).sendToTarget();
                                lastTime = System.currentTimeMillis();
                            }
                        }
                    }
                    if (allFiles.isEmpty() || allFiles.size() < directories.size() + files.size()) {
                        allFiles.clear();
                        if (!directories.isEmpty()) {
                            Collections.sort(directories);
                            allFiles.addAll(directories);
                        }
                        if (!files.isEmpty()) {
                            Collections.sort(files);
                            allFiles.addAll(files);
                        }
                        mHandler.removeMessages(LISTING_ACTION);
                        mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_SAMBA, allFiles).sendToTarget();
                        mHandler.obtainMessage(LISTING_SAMBA_DONE).sendToTarget();
                    }
                } else {
                    Log.e(TAG, "SmbListThread: File access error, sending LISTING_CANNOT_LIST");
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
                }
            } catch (SmbAuthException e) {
                Log.e(TAG, "SmbListThread: SmbAuthException, sending LISTING_NO_PERMISSION");
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_NO_PERMISSION).sendToTarget();
                e.printStackTrace();
            } catch (SmbException e) {
                Log.e(TAG, "SmbListThread: SmbException, sending LISTING_CANNOT_LIST");
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
                e.printStackTrace();
            } catch (InterruptedException e) {}
        }

        private synchronized void hasToStop() throws InterruptedException {
            if (stopThread) {
                throw new InterruptedException();
            }
        }

        public synchronized void stopThread(){
            mHandler.removeMessages(LISTING_ACTION);
            mHandler.removeMessages(SMB_TIMEOUT);
            stopThread = true;
        }
    }

    private final class ListZipThread extends Thread {

        private final ZippedFile mZippedFile;
        private final String zipEntryLevel;
        private boolean stopThread = false;

        public ListZipThread(ZippedFile f) {
            super();
            mZippedFile = f;
            if (mZippedFile.getEntry() != null)
                zipEntryLevel = mZippedFile.getEntry().getName();
            else
                zipEntryLevel = "";
        }

        public void run(){
            ArrayList<ZippedFile> files = new ArrayList<ZippedFile>();
            ArrayList<ZippedFile> directories = new ArrayList<ZippedFile>();
            ZipInputStream zis = null;
            FileInputStream fis = null;
            BufferedInputStream buf = null;
            String name = "";
            try {
                fis = new FileInputStream(mZippedFile.getFile());
                buf = new BufferedInputStream(fis);
                zis = new ZipInputStream(buf);
                ZipEntry ze = null;
                while ((ze = zis.getNextEntry()) != null) {
                    if (zipEntryLevel.equalsIgnoreCase("") || ze.getName().startsWith(zipEntryLevel)){
                        name = ze.getName().substring(zipEntryLevel.length());
                        boolean rightLevel = !name.equalsIgnoreCase("") && (name.indexOf('/') == name.length()-1 || name.indexOf('/') == -1);
                        if (rightLevel){
                            if (ze.isDirectory())
                                directories.add(new ZippedFile(mZippedFile.getFile(), ze));
                            else
                                files.add(new ZippedFile(mZippedFile.getFile(), ze));
                        }
                    }
                }
                Collections.sort(directories);
                if (!files.isEmpty()){
                    Collections.sort(files);
                    directories.addAll(files);
                }
                hasToStop();
                mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, LISTING_ZIP, directories).sendToTarget();
            } catch (FileNotFoundException e) {
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
                e.printStackTrace();
            } catch (IOException e) {
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
                e.printStackTrace();
            } catch (InterruptedException e) {
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
                e.printStackTrace();
            } finally {
                IOUtils.closeSilently(zis);
                IOUtils.closeSilently(buf);
                IOUtils.closeSilently(fis);
            }
        }

        private synchronized void hasToStop() throws InterruptedException {
            if (stopThread) {
                throw new InterruptedException();
            }
        }

        public synchronized void stopThread() {
            stopThread = true;
        }
    }

    private final class ListThread extends Thread {

        private final File file;
        private final String filter;
        private boolean hasFilter = false;
        private boolean stopThread = false;

        public ListThread(File f, String s) {
            super();
            file = f;
            filter = s;
            if (filter != null)
                hasFilter = true;
        }

        public void run() {
            if (file.exists() && file.canRead()) {
                final ArrayList<JavaFile> directories = new ArrayList<JavaFile>();
                final ArrayList<JavaFile> files = new ArrayList<JavaFile>();

                File[] listFile;
                try {
                    listFile = file.listFiles();
                } catch (SecurityException e) {
                    // Unreadable directory.
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_NO_PERMISSION).sendToTarget();
                    return;
                }

                // Error in reading the directory.
                if (listFile == null) {
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_ERROR).sendToTarget();
                    return;
                }

                try {
                    for (File f : listFile) {
                        if (f.isHidden())
                            continue;
                        String fullName = f.getName();
                        String path = f.getPath();
                        if (f.isFile()) {
                            if (filePathsFiltered.contains(path))
                                continue;

                            String name = null, mimeType = null;
                            int dotPos = fullName.lastIndexOf('.');
                            if (fullName.endsWith(".zip")) {
                                ZipFile zf = new ZipFile(f);
                                if (zf.getEntry("META-INF/com/google/android/updater-script") != null) {
                                    mimeType = MimeUtils.guessMimeTypeFromExtension(ZIP_FW_UPDATE_FILE_NAME);
                                    name = fullName.substring(0, dotPos);
                                }
                            }
                            if (mimeType == null) {
                                if (dotPos >= 0 && dotPos < fullName.length()) {
                                    name = fullName.substring(0, dotPos);
                                    mimeType = MimeUtils.guessMimeTypeFromExtension(fullName
                                            .substring(
                                                    dotPos + 1).toLowerCase());
                                    if (mimeType == null)
                                        mimeType = "";
                                } else {
                                    name = fullName;
                                    mimeType = "";
                                }
                            }
                            boolean add = !hasFilter;

                            if (hasFilter){
                                String [] filters = filter.split("\\|\\|");
                                for(String filt: filters){
                                    if(mimeType.startsWith(filt)&&!filt.isEmpty()){
                                        add=true;
                                    }

                                }


                            }
                            if (add)
                                files.add(
                                        (JavaFile) new JavaFile(f)
                                        .setFileType(FileType.File)
                                        .setFakeName(name)
                                        .setMimeType(mimeType)
                                        );
                        } else if (!dirNamesFiltered.contains(fullName)
                                && !dirPathsFiltered.contains(path)) {
                            directories.add(
                                    (JavaFile) new JavaFile(f)
                                    .setFileType(FileType.Directory)
                                    .setFakeName(fullName)
                                    .setMimeType(null)
                                    );
                        }
                        hasToStop();
                    }
                    Collections.sort(directories);
                    Collections.sort(files);
                    directories.addAll(files);
                    hasToStop();
                    mHandler.obtainMessage(LISTING_ACTION, LISTING_SUCCESS, 0, directories).sendToTarget();
                } catch (Exception e) {
                }
            } else {
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
            }
        }

        private synchronized void hasToStop() throws InterruptedException {
            if (stopThread) {
                throw new InterruptedException();
            }
        }

        public synchronized void stopThread() {
            stopThread = true;
        }
    }

    /**
     * This thread manages all the cut, copy, paste actions.
     */
    private final class CxCcCv extends Thread {

        // 32 * 1024 = 32768 as the cp unix command
        private static final int MAX_COUNT = 32768;
        private static final int MAX_STEPS = 17;

        private boolean stopThread = false;
        private boolean cleanRelatives = false;
        private int copySteps = MAX_STEPS;
        private int mode;
        private int nbFiles;
        private int currentFileProgress;
        private long totalSize;
        private long currentSizeProgress;
        boolean overwrite = false;
        private final ArrayList<MetaFile> srcFiles;
        private final ArrayList<MetaFile> targetFiles;

        private String mFirstCopyPattern; // Full pattern for the first copy
        private String mCopyPatternLeft; // Part of the pattern before the index
        // for the Nth copy
        private String mCopyPatternRight; // Part of the pattern after the index
        // for the Nth copy

        /**
         * Call this constructor for deleting a file, and all its related files like subs, etc...
         * 
         * @param srcFile
         * @param clean
         */
        public CxCcCv(MetaFile srcFile, boolean clean) {
            super();
            srcFiles = new ArrayList<MetaFile>(1);
            srcFiles.add(srcFile);
            cleanRelatives = true;
            targetFiles = null;
            mode = DELETE;
        }
        /**
         * Call this constructor for deleting a file.
         * 
         * @param srcFile
         */
        public CxCcCv(MetaFile srcFile) {
            super();
            srcFiles = new ArrayList<MetaFile>(1);
            srcFiles.add(srcFile);
            targetFiles = null;
            mode = DELETE;
        }

        /**
         * Call this constructor for deleting several files.
         * 
         * @param srcFiles
         */
        public CxCcCv(ArrayList<MetaFile> srcFiles) {
            super();
            this.srcFiles = srcFiles;
            targetFiles = null;
            mode = DELETE;
        }

        /**
         * Call this constructor for cutting or copying a file.
         * 
         * @param srcFile
         * @param targetFile
         * @param delete sets to true if srcFile must be deleted.
         */
        public CxCcCv(MetaFile srcFile, MetaFile targetFile, boolean delete, String fileCopyString) {
            super();
            mFileCopyString = fileCopyString;
            srcFiles = new ArrayList<MetaFile>(1);
            srcFiles.add(srcFile);
            targetFiles = new ArrayList<MetaFile>(1);
            targetFiles.add(targetFile);
            mode = delete ? CUT : COPY;
        }

        /**
         * Call this constructor for cutting or copying several files.
         * 
         * @param srcFile
         * @param targetFile
         * @param delete sets to true if srcFile must be deleted.
         */
        public CxCcCv(ArrayList<MetaFile> srcFiles, ArrayList<MetaFile> targetFiles, boolean delete, String fileCopyString) {
            super();
            mFileCopyString = fileCopyString;
            this.srcFiles = srcFiles;
            this.targetFiles = targetFiles;
            mode = delete ? CUT : COPY;
        }

        public CxCcCv(ArrayList<MetaFile> srcFiles, ArrayList<MetaFile> targetFiles, boolean delete, String fileCopyString, boolean overwrite) {
            super();
            this.overwrite = overwrite;
            mFileCopyString = fileCopyString;
            this.srcFiles = srcFiles;
            this.targetFiles = targetFiles;
            mode = delete ? CUT : COPY;
        }

        public void run() {
            boolean res = true;
            int fileCount;

            //            mFileCopyString = context.getString(R.string.file_copy_pattern);
            mFirstCopyPattern = " (" + mFileCopyString + ")";
            mCopyPatternLeft = " (" + mFileCopyString + " ";
            mCopyPatternRight = ")";

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
                    | PowerManager.ON_AFTER_RELEASE, "FileManagerCore");
            wl.acquire();
            if (cleanRelatives){
                addRelatives();
            }
            if (mode != DELETE) {
                for (MetaFile srcFile : srcFiles) {
                    if (srcFile.isDirectory()) {
                        getDirectorySize(srcFile);
                    } else {
                        nbFiles++;
                        totalSize += srcFile.length();
                    }
                }
                mHandler.obtainMessage(PASTING_PROGRESS, 0, nbFiles, Long.valueOf(totalSize)).sendToTarget();
            }
            switch (mode) {
                case DELETE:
                    for (MetaFile srcFile : srcFiles) {
                        res = recursiveDelete(srcFile.getJavaFile());
                        if (!res)
                            break;
                    }
                    break;
                case COPY:
                    fileCount = srcFiles.size();
                    for (int index = 0; index < fileCount && res; index++) {
                        MetaFile srcFile = srcFiles.get(index);
                        MetaFile targetFile = targetFiles.get(index);
                        res = copy(srcFile, targetFile);
                    }
                    break;
                case CUT:
                    fileCount = srcFiles.size();
                    for (int index = 0; index < fileCount && res; index++) {
                        // Try first to move the file, which is possible if both
                        // the source
                        // and the target directories are on the same disk.
                        MetaFile srcFile = srcFiles.get(index);
                        MetaFile targetFile = targetFiles.get(index);
                        res = move(srcFile.getJavaFile(), targetFile.getJavaFile());
                        if (!res) {
                            // Move failed => source and target directories are
                            // not on the same disk
                            res = copy(srcFile, targetFile);
                            if (res) {
                                res &= recursiveDelete(srcFile.getJavaFile());
                            }
                        }
                    }
            }

            int ret = res ? 1 : 0;
            if (!stopThread) {
                // File operation finished or aborted by the system (after an
                // error occured)
                // => send a DONE notification with an error code to the client
                Message msg;

                if (mode == DELETE)
                    msg = mHandler.obtainMessage(DELETING_ACTION, ret, 0);
                else
                    msg = mHandler.obtainMessage(PASTING_ACTION, ret, 0);
                mHandler.sendMessage(msg);
            } else {
                // File operation cancelled by the user
                // => send a CANCELLED notification to the client
                if (mode == DELETE)
                    mHandler.sendEmptyMessage(DELETING_CANCELLED);
                else
                    mHandler.sendEmptyMessage(PASTING_CANCELLED);
            }
            wl.release();
        }
        private void addRelatives() {
            MetaFile[] allDir = null;
            ArrayList<MetaFile> filesToAdd = new ArrayList<MetaFile>();
            ArrayList<String> whiteList = new ArrayList<String>();
            String fileName, nameWithoutExtension, extension, mimetype,  parent = "";
            for (MetaFile srcFile : srcFiles) {
                nameWithoutExtension = ArchosUtils.getNameWithoutExtension(srcFile.getName());
                if (!parent.equals(srcFile.getParent())) {
                    allDir = srcFile.getParentFile().listFiles();
                    parent = srcFile.getParent();
                }
                if (allDir == null){
                    Log.e(TAG, "fail, files list is null");
                    return;
                }
                for (MetaFile file : allDir){
                    fileName = file.getName();
                    if (fileName.startsWith(nameWithoutExtension)){
                        //Do not remove other video with related title, like "moviename The Return"
                        extension = ArchosUtils.getExtension(fileName);
                        if (extension == null) {
                            if(!srcFiles.contains(file))
                                filesToAdd.add(file);
                            continue;
                        }
                        mimetype = MimeUtils.guessMimeTypeFromExtension(extension);
                        if (mimetype == null) {
                            if(!srcFiles.contains(file))
                                filesToAdd.add(file);
                            continue;
                        }
                        if (mimetype.startsWith("video")){ //we whitelist other videos
                            if (fileName.equals(srcFile.getName()))
                                continue;
                            whiteList.add(ArchosUtils.getNameWithoutExtension(fileName));
                        } else { //candidates for deletion
                            if(!srcFiles.contains(file))
                                filesToAdd.add(file);
                        }
                    }
                }
                //Second pass, to remove files related to whitelisted videos
                if (!whiteList.isEmpty()) {
                    ArrayList<MetaFile> filesToRemove = new ArrayList<MetaFile>();
                    outerloop:
                        for (MetaFile file : filesToAdd) {
                            for (String whitelisted : whiteList){
                                if (file.getName().startsWith(whitelisted)){
                                    filesToRemove.add(file);
                                    continue outerloop;
                                }
                            }
                        }
                    for (MetaFile file : filesToRemove){

                        filesToAdd.remove(file);
                    }
                }
            }
            srcFiles.addAll(filesToAdd);

        }

        private void getDirectorySize(MetaFile file) {
            MetaFile[] files = file.listFiles();
            if (files != null) {
                try {
                    for (MetaFile f : files) {
                        hasToStop();
                        if (f.isDirectory()) {
                            getDirectorySize(f);
                        } else {
                            nbFiles++;
                            totalSize += f.length();
                        }
                    }
                } catch (InterruptedException e) {
                }
            }
        }

        /**
         * java.io.File.delete works like rm in Unix
         */
        private boolean recursiveDelete(File byeBye) {
            boolean res = true;

            if (byeBye.exists() && !doNotTouch.contains(byeBye.getPath())) {
                if (byeBye.isDirectory()) {
                    File[] files = byeBye.listFiles();
                    try {
                        for (File file : files) {
                            hasToStop();
                            if (file.isDirectory())
                                res &= recursiveDelete(file);
                            else
                                res &= deleteFile(file);
                        }
                    } catch (InterruptedException e) {

                        res = false;
                    }
                    if (res) {
                        try {
                            hasToStop();
                            res &= deleteFile(byeBye);
                        } catch (InterruptedException e) {
                            res = false;
                        }
                    }
                } else  {
                    try {
                        hasToStop();
                        res &= deleteFile(byeBye);
                    } catch (InterruptedException e) {
                        res = false;
                    }
                }
            } else {
                // Error or operation not allowed
                res = false;
            }
            return res;
        }

        private boolean deleteFile(File file){
            ContentResolver cr = context.getContentResolver();
            Uri uri = MediaStore.Files.getContentUri("external");
            String where = MediaColumns.DATA + "=?";
            String[] selectionArgs = { file.getPath() };
            boolean ret = cr.delete(uri, where, selectionArgs) > 0;
            ret |= file.delete();
            return ret;
        }
        private boolean copy(MetaFile source, MetaFile target) {
            boolean res = true;
            boolean scan = true;

            if (target.exists() && !overwrite) {
                // The expected target file/folder already exists => use a new
                // name
                String newFilePath = getNextCopyName(target);
                target = MetaFile.from(newFilePath);
                Log.d(TAG, "Target file already exists => rename target as " + newFilePath);
            }
            if (source.isDirectory()) {
                // Copy a folder
                if(target.isJavaFile())
                    res = target.getJavaFile().mkdir();

                else if(target.isSmbFile()){
                    scan = false;
                    try { 
                        target.getSmbFile().mkdir();
                        res = true;
                    } catch (SmbException e) {
                        res = false;
                    }
                }
                if (res) {
                    MetaFile[] children = source.listFiles();
                    try {
                        for (MetaFile child : children) {
                            hasToStop();
                            if(target.isJavaFile())
                                res &= copy(child, MetaFile.from(new File(target.getJavaFile(), child.getName())));
                            else if(target.isSmbFile())
                                res &= copy(child, MetaFile.from(target.getSmbFile().getCanonicalPath()+(target.getSmbFile().getCanonicalPath().endsWith("/")?"":"/")+child.getName()));
                            else
                                res &= copy(child, MetaFile.from(target.getAccessPath()+(target.getAccessPath().endsWith("/")?"":"/")+child.getName()));
                        }
                    } catch (InterruptedException e) {
                        res = false;
                    }
                }
            } else if (source.isJavaFile() && target.isJavaFile()&&source.isFile()) {
                // Check on target.parent because target doesn't exist yet.
                if (target.getParentFile().getJavaFile().getUsableSpace() < source.length()) {
                    // Not enough space to copy.
                    return false;
                }

                // Copy a file
                FileChannel in = null;
                FileOutputStream out = null;
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(source.getJavaFile());
                    in = fis.getChannel();
                    out = new FileOutputStream(target.getJavaFile());

                    long size = in.size();
                    long position = 0;
                    Method transferTo = null;
                    try {
                        transferTo = FileChannel.class.getMethod("transferTo", long.class, long.class, FileOutputStream.class);
                    } catch (NoSuchMethodException e) {
                        // Keep silent, we are just not on an archos device
                    }
                    while (position < size) {
                        hasToStop();
                        if (transferTo != null){
                            try {
                                position += (Long)transferTo.invoke(in, position, (long)MAX_COUNT, out);
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        } else {
                            long ret = 0;
                            ret = ArchosFileChannel.transfer(position, (long)MAX_COUNT, fis, out);
                            if (ret <= 0) {
                                if (ret == 0)
                                    throw new Exception("File transfert stopped without finishing");
                                else
                                    throw new Exception("Error with File descriptor error="+ret);
                            } else
                                position += ret;
                        }
                        // Don't overflow the progress dialog with
                        // PASTING_PROGRESS messages, so only send the message
                        // after copying MAX_COUNT bytes or once every MAX_STEPS
                        // iterations
                        copySteps--;
                        if (size < MAX_COUNT) {
                            mHandler.obtainMessage(PASTING_DETAILED_PROGRESS,new CopyMessage(currentFileProgress, source, position, size, Long.valueOf(currentSizeProgress + position), totalSize)).sendToTarget();
                            mHandler.obtainMessage(PASTING_PROGRESS, 1, currentFileProgress, Long.valueOf(currentSizeProgress + position)).sendToTarget();
                        } else if (copySteps <= 0) {
                            mHandler.obtainMessage(PASTING_DETAILED_PROGRESS,new CopyMessage(currentFileProgress, source, position, size, Long.valueOf(currentSizeProgress + position), totalSize)).sendToTarget();
                            mHandler.obtainMessage(PASTING_PROGRESS, 1, currentFileProgress, Long.valueOf(currentSizeProgress + position)).sendToTarget();
                            copySteps = MAX_STEPS;
                        }
                    }
                    mHandler.obtainMessage(PASTING_DETAILED_PROGRESS,new CopyMessage(currentFileProgress, source, size, size, Long.valueOf(currentSizeProgress + position), totalSize)).sendToTarget();
                    currentSizeProgress += position;
                    res = true;
                } catch (Exception e) {
                    Log.d(TAG, "copy error : exception=" + e);
                    res = false;
                } finally {
                    IOUtils.closeSilently(fis);
                    IOUtils.closeSilently(in);
                    IOUtils.closeSilently(out);
                    if (!res) {
                        target.delete();
                    }
                }

                currentFileProgress++;
            } 

            else if(source.isJavaFile() && source.isFile()){

                 if(target.isSmbFile()){
                    scan = false;
                    // Copy a SMB file
                    FileInputStream fis = null;
                    BufferedInputStream bis = null;
                    SmbFileOutputStream out = null;
                    try {
                        fis = new FileInputStream(source.getJavaFile());
                        bis = new BufferedInputStream(fis);
                        out = new SmbFileOutputStream(target.getSmbFile());

                        long size = source.length();
                        long position = 0;

                        byte buf[] = new byte[MAX_COUNT];
                        int len;
                        while ((len = bis.read(buf)) != -1) {
                            hasToStop();
                            out.write(buf, 0, len);
                            position += (long)len;
                            // Don't overflow the progress dialog with
                            // PASTING_PROGRESS messages, so only send the message
                            // after copying MAX_COUNT bytes or once every MAX_STEPS
                            // iterations
                            copySteps--;
                            if (size < MAX_COUNT) {
                                mHandler.obtainMessage(PASTING_PROGRESS, 1, currentFileProgress, Long.valueOf(currentSizeProgress + position)).sendToTarget();
                            } else if (copySteps <= 0) {
                                mHandler.obtainMessage(PASTING_PROGRESS, 1, currentFileProgress, Long.valueOf(currentSizeProgress + position)).sendToTarget();
                                copySteps = MAX_STEPS;
                            }
                        }
                        currentSizeProgress += position;
                        res = true;
                    } catch (Exception e) {
                        res = false;
                    } finally {
                        IOUtils.closeSilently(bis);
                        IOUtils.closeSilently(out);
                    }
                    currentFileProgress++;
                }
            }

            else if (source.isSmbFile() && source.isFile()) {
                // Check on target.parent because target doesn't exist yet.
                if (target.getParentFile().getJavaFile().getUsableSpace() < source.length()) {
                    // Not enough space to copy.
                    return false;
                }

                // Copy a SMB file
                SmbFileInputStream fis = null;
                BufferedInputStream bis = null;
                FileOutputStream out = null;
                try {
                    fis = new SmbFileInputStream(source.getSmbFile());
                    bis = new BufferedInputStream(fis);
                    out = new FileOutputStream(target.getJavaFile());

                    long size = source.length();
                    long position = 0;

                    byte buf[] = new byte[MAX_COUNT];
                    int len;
                    while ((len = bis.read(buf)) != -1) {
                        hasToStop();
                        out.write(buf, 0, len);
                        position += (long)len;
                        // Don't overflow the progress dialog with
                        // PASTING_PROGRESS messages, so only send the message
                        // after copying MAX_COUNT bytes or once every MAX_STEPS
                        // iterations
                        copySteps--;
                        if (size < MAX_COUNT) {
                            mHandler.obtainMessage(PASTING_PROGRESS, 1, currentFileProgress, Long.valueOf(currentSizeProgress + position)).sendToTarget();
                        } else if (copySteps <= 0) {
                            mHandler.obtainMessage(PASTING_PROGRESS, 1, currentFileProgress, Long.valueOf(currentSizeProgress + position)).sendToTarget();
                            copySteps = MAX_STEPS;
                        }
                    }
                    currentSizeProgress += position;
                    res = true;
                } catch (Exception e) {
                    res = false;
                } finally {
                    IOUtils.closeSilently(bis);
                    IOUtils.closeSilently(out);
                }
                currentFileProgress++;
            } else {
                // Not a folder and not a file => error
                res = false;
            }


            if (res&&scan) {
                // Add the new file to the medialib
                scanFile(target.getJavaFile());
            }

            if (stopThread) {
                target.delete();
            }

            return res;
        }

        private synchronized void hasToStop() throws InterruptedException {
            if (stopThread) {
                throw new InterruptedException();
            }
        }

        public synchronized void stopThread() {
            stopThread = true;
        }

        public synchronized int getMode() {
            return mode;
        }

        private String getNextCopyName(MetaFile file) {
            String name; // myvideo
            String extension; // avi
            // Unused.
            // String path = file.getPath(); // /mnt/storage/data/myvideo.avi
            MetaFile currentFolder = file.getParentFile(); // /mnt/storage/data

            // Extract the filename and the extension from the complete path
            String fullName = file.getName();
            int extensionPos = fullName.lastIndexOf('.');
            if (extensionPos >= 0) {
                name = fullName.substring(0, extensionPos);
                extension = fullName.substring(extensionPos + 1);
            } else {
                name = fullName;
                extension = "";
            }

            // Get the base name of the file to copy:
            // - if the file is itself a copy of another file => use the name of
            // the original file
            // - use the current name otherwise
            String baseName = name;
            if (name.endsWith(mFirstCopyPattern)) {
                baseName = name.substring(0, name.length() - mFirstCopyPattern.length());
                Log.d(TAG, "The file is already a copy of " + baseName);
            } else if (name.endsWith(mCopyPatternRight)) {
                // Check if we can find the " (copy NN)" pattern
                int templatePos = name.lastIndexOf(mCopyPatternLeft);
                if (templatePos >= 0) {
                    baseName = name.substring(0, templatePos);
                    Log.d(TAG, "The file is already a copy of " + baseName);
                }
            }

            // Check if there are already some copies of the file in the current
            // folder
            // and retrieve the first available index for the suffix to add to
            // the filename
            int copyIndex = getNextCopyIndex(currentFolder, baseName, extension);

            // Build the new filename by appending a suffix to the current
            // filename
            String suffix;
            if (copyIndex == 1) {
                // First copy => add a " (copy)" suffix
                suffix = mFirstCopyPattern;
            } else {
                // Other copies => add a " (copy NN)" suffix
                suffix = mCopyPatternLeft + copyIndex + mCopyPatternRight;
            }

            if (extension.isEmpty()) {
                return (currentFolder + "/" + baseName + suffix);
            }
            return (currentFolder + "/" + baseName + suffix + "." + extension);
        }

        private int getNextCopyIndex(MetaFile currentFolder, String originalName,
                String originalExtension) {
            int maxCopyIndex = 0;

            // Scan the contents of the current folder
            MetaFile[] files = currentFolder.listFiles();
            for (MetaFile file : files) {
                int index = 0;

                // Extract the filename and the extension from the complete path
                String extension;
                String filePath = file.getAccessPath();
                int extensionPos = filePath.lastIndexOf('.');
                if (extensionPos >= 0) {
                    extension = filePath.substring(extensionPos + 1);
                } else {
                    extension = "";
                }

                // We only need to compare the filenames if the extension is the
                // same
                if (extension.equals(originalExtension)) {
                    // Strip the path and extension
                    String fileName;
                    if (extension.isEmpty()) {
                        fileName = filePath
                                .substring(currentFolder.getAccessPath().length() + 1, filePath.length());
                    } else {
                        fileName = filePath.substring(currentFolder.getAccessPath().length() + 1, filePath.length()
                                - extension.length() - 1);
                    }

                    if (fileName.startsWith(originalName)) {
                        // Check if there is a known suffix at the end of the
                        // filename
                        if (fileName.endsWith(mFirstCopyPattern)) {
                            // This is the first copy of the original file
                            index = 1;
                        } else if (fileName.endsWith(mCopyPatternRight)) {
                            // This could be a copy of the original file
                            // => check if we can find the " (copy NN)" pattern
                            // and extract NN then
                            int templatePos = fileName.lastIndexOf(mCopyPatternLeft);
                            if (templatePos >= 0) {
                                String indexString = fileName.substring(templatePos
                                        + mCopyPatternLeft.length(), fileName.length() - 1);
                                index = Integer.parseInt(indexString);
                            }
                        }
                    }
                }

                if (maxCopyIndex < index) {
                    // We found a copy with a higher index => remember it
                    maxCopyIndex = index;
                }
            }

            return (maxCopyIndex + 1);
        }
    }

    public FileManagerCore(Context context) {
        super();
        this.context = context;
        dirPathsFiltered = new HashSet<String>(DIR_PATHS_FILTER.length);
        Collections.addAll(dirPathsFiltered, DIR_PATHS_FILTER);
        dirNamesFiltered = new HashSet<String>(DIR_NAMES_FILTER.length);
        Collections.addAll(dirNamesFiltered, DIR_NAMES_FILTER);
        filePathsFiltered = new HashSet<String>(FILE_PATHS_FILTER.length);
        Collections.addAll(filePathsFiltered, FILE_PATHS_FILTER);
        doNotTouch = new HashSet<String>(DO_NOT_TOUCH.length);
        Collections.addAll(doNotTouch, DO_NOT_TOUCH);
    }

    @SuppressWarnings("unchecked")
    public ArrayList<String> getShareNames(){
        ArrayList<SmbItemData> shares = new ArrayList<SmbItemData>();
        ArrayList<String> shareNames = new ArrayList<String>();
        if (mWorkgroupsList == null)
            return shareNames;
        for (String workGroup : mWorkgroupsList.keySet())
            shares.addAll((ArrayList<SmbItemData>)mWorkgroupsList.get(workGroup).getExtraData());
        for (SmbItemData share : shares)
            shareNames.add(share.getName());
        return shareNames;
    }

    /*
     * Check if this workgroup is in the saved mWorkgroupList
     * For smb url smb://WORKGROUP/, name value should be WORKGROUP
     */
    public boolean isWorkgroup(String name){
        if (mWorkgroupsList == null)
            return false;
        return mWorkgroupsList.keySet().contains(name);
    }

    public void getList() {
        getList(STORAGE, null);
    }

    public void getList(String type) {
        getList(STORAGE, type);
    }

    public void getList(File file) {
        getList(file, null);
    }

    public void getList(File file, String type) {
        listThread = new ListThread(file, type);
        listThread.start();
    }

    public void getList(SmbFile file, String type) {
        //        stopSMBListing(); not needed anymore?
        getList(file, type, false);
    }

    public void getList(SmbFile file, String type, boolean refresh) {
        //        stopSMBListing(); not needed anymore?
        smbListThread = new SmbListThread(file, type, refresh);
        smbListThread.start();
    }

    public void getZipList(ZippedFile file) {
        zipListThread = new ListZipThread(file);
        zipListThread.start();
    }

    public void getSMBList(String path, String type) {
        SmbFile file;
        try {
            file = new SmbFile(SambaConfiguration.getCredentials(path));

        } catch (MalformedURLException e) {
            try {
                file = new SmbFile(path);
            } catch (MalformedURLException e1) {
                mHandler.obtainMessage(LISTING_ACTION, LISTING_ERROR, LISTING_CANNOT_LIST).sendToTarget();
                return;
            }
        }
        getList(file, type);
    }

    public int[] getDirectoryContent(File file) {
        return getDirectoryContent(file, null);
    }

    public int[] getDirectoryContent(File file, String filter) {
        boolean hasFilter = false;
        int files = 0;
        int directories = 0;

        if (filter != null) {
            hasFilter = true;
        }

        File[] listFiles = file.listFiles();
        if (listFiles != null) {
            for (File f : listFiles) {
                if (f.isHidden())
                    continue;
                if (f.isFile()) {
                    if (!filePathsFiltered.contains(f.getPath())) {
                        if (hasFilter) {
                            String fullName = f.getName();
                            int dotPos = fullName.lastIndexOf('.');
                            if (dotPos >= 0 && dotPos < fullName.length()) {
                                String mimeType = MimeUtils.guessMimeTypeFromExtension(fullName.substring(dotPos + 1).toLowerCase()); 
                                if (mimeType != null){
                                    String [] filters = filter.split("\\|\\|");
                                    for(String filt: filters){
                                        if(mimeType.startsWith(filt)&&!filt.isEmpty()){
                                            files++;
                                        }

                                    }
                                }


                            }
                        } else {
                            files++;
                        }
                    }
                } else if (!dirNamesFiltered.contains(f.getName())
                        && !dirPathsFiltered.contains(f.getPath()))
                    directories++;
            }
        }

        return new int[] {
                directories, files
        };
    }

    public boolean move(File srcFile, File targetFile) {
        boolean res = srcFile.renameTo(targetFile);
        if (res) {
            // Add the new file to the medialib
            scanFile(targetFile);

            // Remove the source file from the medialib
            //            scanFile(srcFile);
            ContentResolver cr = context.getContentResolver();
            Uri uri = MediaStore.Files.getContentUri("external");
            String where = MediaColumns.DATA + "=?";
            String[] selectionArgs = { srcFile.getPath() };
            cr.delete(uri, where, selectionArgs);
        }
        return res;
    }

    private void scanFile(File file) {
        // Ask mediascanner to scan the provided file/folder
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file));
        intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
        context.sendBroadcast(intent);
    }

    /**
     * This method will start a thread deleting, recursively if file is a
     * directory, the file. Result of the deleting is sending by the
     * notifyObserver method.
     */
    public void clean(MetaFile file) {
        cxCcCv = new CxCcCv(file, true);
        cxCcCv.start();
    }
    public void delete(MetaFile file) {
        cxCcCv = new CxCcCv(file);
        cxCcCv.start();
    }

    public void delete(ArrayList<MetaFile> list) {
        cxCcCv = new CxCcCv(list);
        cxCcCv.start();
    }

    public void copy(MetaFile src, MetaFile target, String fileCopyString) {
        cxCcCv = new CxCcCv(src, target, false, fileCopyString);
        cxCcCv.start();
    }

    public void copy(ArrayList<MetaFile> list, ArrayList<MetaFile> target, String fileCopyString, boolean overwrite) {
        cxCcCv = new CxCcCv(list, target, false, fileCopyString, overwrite);
        cxCcCv.start();
    }

    public void copy(ArrayList<MetaFile> list, ArrayList<MetaFile> target, String fileCopyString) {
        cxCcCv = new CxCcCv(list, target, false, fileCopyString);
        cxCcCv.start();
    }

    public void cut(MetaFile src, MetaFile target, String fileCopyString) {
        cxCcCv = new CxCcCv(src, target, true, fileCopyString);
        cxCcCv.start();
    }

    public void cut(ArrayList<MetaFile> list, ArrayList<MetaFile> target, String fileCopyString) {
        cxCcCv = new CxCcCv(list, target, true, fileCopyString);
        cxCcCv.start();
    }

    public boolean isListing() {
        return ((listThread != null) && listThread.isAlive());
    }

    /**
     * Call this method to stop the thread listing files.
     */
    public void stopListing() {
        if (isListing())
            listThread.stopThread();
    }

    public boolean isSMBListing() {
        return ((smbListThread != null) && smbListThread.isAlive());
    }

    /**
     * Call this method to stop the thread listing files.
     */
    public void stopSMBListing() {
        if (isSMBListing())
            smbListThread.stopThread();
    }


    private boolean isOperating() {
        return ((cxCcCv != null) && cxCcCv.isAlive());
    }

    public boolean isDeleting() {
        if (isOperating()) {
            return (cxCcCv.getMode() == DELETE);
        }
        return false;
    }

    public boolean isCopying() {
        if (isOperating()) {
            return (cxCcCv.getMode() == COPY);
        }
        return false;
    }

    public boolean isCutting() {
        if (isOperating()) {
            return (cxCcCv.getMode() == CUT);
        }
        return false;
    }

    private void stopCxCcCv() {
        if (isOperating())
            cxCcCv.stopThread();
    }

    /**
     * Call this method to stop the thread deleting files.
     */
    public void stopDeleting() {
        stopCxCcCv();
    }

    /**
     * Call this method to stop the thread pasting files.
     */
    public void stopPasting() {
        stopCxCcCv();
    }

    private String getIpAddress() {
        ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if (!intf.getDisplayName().startsWith("tun") && !intf.getDisplayName().startsWith("tap"))
                    continue;
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) { // we get both ipv4 and ipv6, we want ipv4
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG,"getIpAddress", e);
        }

        final android.net.NetworkInfo wifi = connMgr
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifi.isConnected()) {
            WifiManager myWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo myWifiInfo = myWifiManager.getConnectionInfo();
            int ipAddress = myWifiInfo.getIpAddress();
            return android.text.format.Formatter.formatIpAddress(ipAddress);
        }
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) { // we get both ipv4 and ipv6, we want ipv4
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG,"getIpAddress", e);
        }
        return null;
    }

    public int getSmbFolderStatus(String path) {
        if (!path.startsWith("smb://")) {
            return FOLDER_STATUS_ERROR;
        }

        SmbFile file;
        try {
            // Take into account the credentials for this folder when they are known
            file = new SmbFile(SambaConfiguration.getCredentials(path));
            // Check if the contents of the folder is available
            if (file.exists() && file.canRead()) {
                file.listFiles();
            }
        } catch (MalformedURLException e) {
            return FOLDER_STATUS_ERROR;
        } catch (SmbException e) {
            return FOLDER_STATUS_PROTECTED;
        } catch (NetworkOnMainThreadException e) {
            return FOLDER_STATUS_OK;
        }

        return FOLDER_STATUS_OK;
    }

    static {
        System.loadLibrary("filecoreutils");
    }
}
