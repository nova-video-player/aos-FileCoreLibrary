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

// Copyright 2011 Google Inc. All Rights Reserved.

package com.archos.filecorelibrary.wifidirect;

import com.archos.filecorelibrary.wifidirect.WiFiDirectBroadcastReceiver.WiFiDirectBroadcastListener;
import com.archos.filecorelibrary.R;
import com.archos.environment.ArchosUtils;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.RemoteViews;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends Service implements WiFiDirectBroadcastListener, ConnectionInfoListener, PeerListListener, ActionListener {

    public static final String TAG = "wifidirect";
    private static final int SOCKET_TIMEOUT = 5000;
    public static final int BUFFER_SIZE = 300;
    public static final int NOTIFICATION_ID = 42;
    WifiLock mWiFiLock;
    public static final String ACTION_CONNECT = "com.archos.wifidirect.CONNECT";
    public static final String ACTION_SEND_FILE = "com.archos.wifidirect.SEND_FILE";
    public static final String ACTION_STOP = "com.archos.wifidirect.STOP";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String REMOTE_DEVICE_ADDRESS = "remote_address";
    public static final int OWNER_PORT = 8988;
    Handler mHandler;
    volatile Builder notificationBuilder;
    NotificationManager notificationManager;
    private long mProgress = 0;
    private int percent = 0;
    private boolean uploading = false;
    private boolean downloading = false;
    private boolean readyToStop = false;
    volatile RemoteViews contentView;
    volatile ProgressBar progressBar;
    private WifiP2pManager mManager;
    private Channel mChannel;
    BroadcastReceiver mReceiver;
    WifiP2pInfo info;
    IntentFilter mIntentFilter;
    public static Thread thread;
    static Socket socket = null;
    static ServerSocket serverSocket = null;
    private String path;

    final RemoteCallbackList<IFileTransferServiceCallback> mCallbacks
            = new RemoteCallbackList<IFileTransferServiceCallback>();

    public void onCreate(){
        super.onCreate();

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWiFiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL , "WiFiDirectLock");



        mHandler = new Handler();
        registerReceiver(mReceiver, mIntentFilter);
//        try {
//            Method enableP2p = WifiP2pManager.class.getMethod("enableP2p", Channel.class);
//            enableP2p.invoke(mManager, mChannel);
//        } catch (IllegalArgumentException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        }
//        mManager.enableP2p(mChannel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
     // configure the notification
        notificationBuilder = new Notification.Builder(this);
        contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.notification_progress);
        contentView.setImageViewResource(R.id.image, R.drawable.ic_wifip2p);
        contentView.setTextViewText(R.id.title, "Waiting for connection to download");
        contentView.setProgressBar(R.id.status_progress, 100, 0, false);
        notificationBuilder.setContent(contentView);
        notificationBuilder.setSmallIcon(R.drawable.ic_wifip2p);
        notificationBuilder.setOngoing(true);
        notificationBuilder.setTicker("WiFi Direct service started");
        notificationBuilder.setOnlyAlertOnce(true);

        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean client = false;
        if (intent != null && intent.hasExtra("client"))
            client = intent.getBooleanExtra("client", false);
        if (intent != null && intent.hasExtra("path"))
            path = intent.getStringExtra("path");
        i.setComponent(new ComponentName("com.archos.wifidirect",
                client ? "com.archos.wifidirect.WiFiDirectSenderActivity" : "com.archos.wifidirect.WiFiDirectReceiverActivity"));
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        notificationBuilder.setContentIntent(pi);
        notificationManager = (NotificationManager) getApplicationContext().getSystemService(
                Context.NOTIFICATION_SERVICE);
        //To not be killed
        startForeground(NOTIFICATION_ID, notificationBuilder.getNotification());
        return START_STICKY;
    }

    /* unregister the broadcast receiver */
    @Override
    public void onDestroy() {
        super.onDestroy();
        mManager.removeGroup(mChannel, this);
        mManager.cancelConnect(mChannel, this);
        if (socket != null && !socket.isClosed()){
            try {
                if (!socket.isOutputShutdown())
                    socket.shutdownOutput();
            } catch (IOException e){}
            try {
                if (!socket.isInputShutdown())
                socket.shutdownInput();
            } catch (IOException e){}
        }

        if (serverSocket != null && !serverSocket.isClosed()){
            try {
            serverSocket.close();
            } catch (IOException e){}
        }
        unregisterReceiver(mReceiver);
//        Method disableP2p;
//        try {
//            disableP2p = WifiP2pManager.class.getMethod("disableP2p", Channel.class);
//            disableP2p.invoke(mManager, mChannel);
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (IllegalArgumentException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        }
    }

    public FileTransferService() {
        super();
    }

    private void discoverPeers(){
        mManager.discoverPeers(mChannel, null);
    }

    private void connect(String deviceAddress){
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        config.groupOwnerIntent = 0; //Client mustn't be group owner
        mManager.connect(mChannel, config, null);
    }

    private void sendFile(String host){

        startProgress();
        OutputStream stream = null;
        FileInputStream is = null;
        ObjectOutputStream oos = null;
        File file;
        socket = new Socket();
        file = new File(path);
        final long length = file.length();
        try {
//            socket.setSoTimeout(1000);
            socket.setSoLinger(true, 2);
            uploading = true;
            //WiFi lock for preserving file transfer
            mWiFiLock.acquire();
            socket.bind(null);
            socket.connect((new InetSocketAddress(host, OWNER_PORT)), SOCKET_TIMEOUT);
            //Send file name
            stream = socket.getOutputStream();
            oos = new ObjectOutputStream(stream);
            oos.writeObject(new WiFiDirectHeader(WiFiDirectHeader.TYPE_TRANSFER, file.getName(), length));
            oos.flush();
            //copy file content
            is = new FileInputStream(file);
            byte buf[] = new byte[1024];
            int len;
            while ((len = is.read(buf)) != -1) {
                stream.write(buf, 0, len);
                mProgress += len;
                updateProgress(mProgress, length);
            }
            stream.flush();
            Log.w(TAG, "Client: Data written");
        } catch (FileNotFoundException e) {
            Log.w(TAG, "FileNotFoundException",e);
        } catch (IOException e) {
            Log.w(TAG, "IOException", e);
        } finally {
            terminateProgress();
            if (mWiFiLock.isHeld())
                mWiFiLock.release();
            Utils.closeSilently(is);
//            if (socket != null) {
//                try {
//                    Log.d(TAG, "socket shutdown o/");
//                    socket.shutdownOutput();
//                } catch (IOException e) {
//                    // Give up
//                    e.printStackTrace();
//                } finally {
//                    socket = null;
//
//                }
//            }
            mHandler.post(new Runnable() {
                public void run() {
                    final int N = mCallbacks.beginBroadcast();
                    for (int i=0; i<N; i++) {
                        try {
                            mCallbacks.getBroadcastItem(i).operationDone();
                        } catch (RemoteException e) {}
                    }
                    mCallbacks.finishBroadcast();
                }});
            readyToStop = true;
        }
    }

    private void stopDelayed() {
        mHandler.postDelayed(new Runnable() {

            @Override
            public void run() {
                stopSelf();
            }
        }, 3000);
    }

    private void receiveFile(){
        try {
            serverSocket = new ServerSocket(OWNER_PORT);
            socket = serverSocket.accept();
            downloading = true;
            mWiFiLock.acquire();
            saveFile(socket, path);
        } catch (IOException e) {
            Log.w(FileTransferService.TAG, "Server: Socket failed",e);
            return;
        } catch (Exception e) {
            Log.w(FileTransferService.TAG, "Server: other",e);
            return;
        } finally {
            endDownload();
        }
    }

    private void endDownload() {
        if (mWiFiLock.isHeld())
            mWiFiLock.release();
        downloading = false;
        mHandler.post(new Runnable() {
            public void run() {
                final int N = mCallbacks.beginBroadcast();
                for (int i=0; i<N; i++) {
                    try {
                        mCallbacks.getBroadcastItem(i).operationDone();
                    } catch (RemoteException e) {}
                }
                mCallbacks.finishBroadcast();
            }});
        stopSelf();
    }

    private String saveFile(Socket socket, String path) throws Exception {
        InputStream inputstream = socket.getInputStream();
        ObjectInputStream ois = new ObjectInputStream(inputstream);
        BufferedInputStream bis;
        FileOutputStream fos = null;
        WiFiDirectHeader fileReceived = null;
        File file;
        // 1. Read file name.
        Object o = ois.readObject();

        if (o instanceof WiFiDirectHeader) {
            fileReceived = (WiFiDirectHeader) o;
            file = new File(path,fileReceived.getName());
            fos = new FileOutputStream(file);
        } else {
            Log.w(FileTransferService.TAG, "not WiFiDirectHeader");
            throw new Exception("Something is wrong");
        }

        //write the file, and update medialib in case of success
        bis = new BufferedInputStream(inputstream);
        if (copyFile(bis, fos, fileReceived.getSize())) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://".concat(path).concat("/").concat(fileReceived.getName())));
            intent.setPackage(ArchosUtils.getGlobalContext().getPackageName());
            sendBroadcast(intent);
        } else {
            file.delete();
        }
        //We close
        Utils.closeSilently(fos);
        Utils.closeSilently(ois);
        Utils.closeSilently(bis);
        return path.concat("/").concat(fileReceived.getName());
    }

    private boolean copyFile(InputStream inputStream, OutputStream out, long length) {
        byte buf[] = new byte[1024];
        int len;
        long progress = 0;
        try {
            while ((len = inputStream.read(buf,0,1024)) > 0) {
                out.write(buf, 0, len);
                progress += (long) len;
                updateProgress(progress, length);
                if (progress == length)
                    break;
            }
//            out.write(buf, 0, len);
            out.flush();
        } catch (IOException e) {
            Log.w(FileTransferService.TAG, "copy exception", e);
            return false;
        }
        return true;
    }

    /*
     * ProgressDialog management
     */
    private void updateProgress(final long progress, final long length){
        if (((int)((double)(progress*100)/(double)length)) > percent){
            percent = ((int)((double)(progress*100)/(double)length));
            //Notification update
            mHandler.post(new Runnable() {
                public void run() {
                    long oldId = Binder.clearCallingIdentity();
                    contentView.setProgressBar(R.id.status_progress, 100, percent, false);
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.getNotification());
                    Binder.restoreCallingIdentity(oldId);
                    //Remote progressDialog update
                    final int N = mCallbacks.beginBroadcast();
                    for (int i=0; i<N; i++) {
                        try {
                            mCallbacks.getBroadcastItem(i).transferInProgress(percent);
                        } catch (RemoteException e) {}
                    }
                    mCallbacks.finishBroadcast();
                }
            });

        }
    }

    private void terminateProgress(){
        mHandler.post(new Runnable() {
            public void run() {
                long oldId = Binder.clearCallingIdentity();
                notificationManager.cancel(NOTIFICATION_ID);
                Binder.restoreCallingIdentity(oldId);
                }
        });
    }

    private void startProgress(){
        mHandler.post(new Runnable() {
            public void run() {
                long oldId = Binder.clearCallingIdentity();
                contentView.setTextViewText(R.id.title, "Download in progress");
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.getNotification());
                Binder.restoreCallingIdentity(oldId);
                }
        });
    }

    @Override
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        if (isWifiP2pEnabled)
            discoverPeers();
//        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void updateThisDevice(WifiP2pDevice device) {
//        stopServer();
        Device dev = new Device(device.deviceName, device.deviceAddress, device.status);
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).deviceUpdate(dev);
            } catch (RemoteException e) {}
        }
        mCallbacks.finishBroadcast();
        if (readyToStop)
            stopSelf();
    }

    private void stopServer() {
        if (downloading){
            thread.interrupt();
            endDownload();
            downloading = false;
        }
    }

    // ConnectionInfoListener interface
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        this.info = info;
        if (info.groupFormed){
            final int N = mCallbacks.beginBroadcast();
            for (int i=0; i<N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).connectionDone();
                } catch (RemoteException e) {}
            }
            mCallbacks.finishBroadcast();
        }
    }

    //PeerListListener
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        List<Device> peerList = new ArrayList<Device>();
        for (WifiP2pDevice peer : peers.getDeviceList()){
            peerList.add(new Device(peer.deviceName, peer.deviceAddress, peer.status));
        }
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        for (int i=0; i<N; i++) {
            try {
                mCallbacks.getBroadcastItem(i).updatePeersList(peerList);
            } catch (RemoteException e) {}
        }
        mCallbacks.finishBroadcast();
    }

    private final IFileTransferService.Stub mBinder = new IFileTransferService.Stub() {
        public void registerCallback(IFileTransferServiceCallback cb) {
            if (cb != null) mCallbacks.register(cb);
        }

        public void unregisterCallback(IFileTransferServiceCallback cb) {
            if (cb != null) mCallbacks.unregister(cb);
        }

        public void rConnect(final String deviceAddress){
            thread =new Thread(new Runnable() {
                @Override
                public void run() {
                    connect(deviceAddress);
                }
            });
            thread.start();
        }

        public void rSendFile(){
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    sendFile(info.groupOwnerAddress.getHostAddress());
                }
            });
            thread.start();
        }

        public void rReceiveFile(){
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveFile();
                }
            });
            thread.start();
        }

        public void stop(boolean force){
            if (uploading)
                thread.interrupt();
            stopSelf();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopSelf();
        return true;
    }
    @Override
    public void onFailure(int reason) {}

    @Override
    public void onSuccess() {}
}
