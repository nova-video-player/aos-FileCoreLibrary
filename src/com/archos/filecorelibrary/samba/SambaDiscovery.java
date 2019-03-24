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

package com.archos.filecorelibrary.samba;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jcifs.netbios.UdpDiscovery;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.archos.environment.ArchosFeatures;
import com.archos.environment.ArchosUtils;


public class SambaDiscovery implements InternalDiscoveryListener {

    private final static String TAG = "SambaDiscovery";
    private static boolean DBG = false;

    public static final int SOCKET_TIMEOUT = 150;

    final private Context mContext;

    /**
     * Handler running on main UI thread, used to post listeners callbacks to the UI thread
     */
    final private Handler mUiHandler;

    /**
     *Someone interested about our discoveries
     */
    private List<Listener> mListeners = new LinkedList<Listener>();

    /**
     * Minimum time to wait between 2 updates to the listener
     */
    private long mMinimumPeriodInMs = 0;

    /**
     * Maximum time to wait to read sockets
     */
    private int mSocketReadDurationMs = 10000;

    /**
     * Last time when we notified the listener
     */
    private long mLastListenerUpdate = 0;

    /**
     * True if there is something new to report to the listener
     */
    private boolean mThereIsAnUpdate = false;
    private boolean mUpdateDelayed = false;

    /**
     * We have several (2 as of today...) discovery engine internally
     */
    private List<InternalDiscovery> mInternalDiscoveries = new ArrayList<InternalDiscovery>(2);

    /**
     * true if user aborted the discovery
     */
    private boolean mIsAborted = false;

    /**
     * Workgroups indexed by name
     */
    final private Map<String, Workgroup> mWorkgroups = new HashMap<String, Workgroup>();

    public Handler getmUiHandler() {
        return mUiHandler;
    }

    public List<Listener> getmListeners() {
        return mListeners;
    }

    public Map<String,Workgroup> getmWorkgroups() {
        return mWorkgroups;
    }

    public void setmThereIsAnUpdate(boolean mThereIsAnUpdate) {
        this.mThereIsAnUpdate = mThereIsAnUpdate;
    }

    /**
     * Called by two threads (Udp and Tcp) when they detect a (new) share
     * @param workgroupName
     * @param shareName
     */
    @Override
    synchronized public void onShareFound(String workgroupName, String shareName, String shareAddress) {
        if (DBG) Log.d(TAG, "onShareFound "+workgroupName+" \""+shareName+"\" "+shareAddress);

        boolean alreadyFound = false;

        // Check if this address is already in the list
        Iterator<Workgroup> wkIter = mWorkgroups.values().iterator();
        while(wkIter.hasNext()) {
            Workgroup wk = wkIter.next();
            Iterator<Share> shIter = wk.getShares().iterator();
            while (shIter.hasNext()) {
                if (shIter.next().getAddress().equals(shareAddress)) {
                    alreadyFound = true;

                    // When shareName is not null and not empty, we must remove a previous instance
                    // with the same address (but no name because from TCP discovery) to replace it
                    if (shareName != null && !shareName.isEmpty()) {
                        if (DBG) Log.d(TAG, "addIfNeeded: removing " + shareAddress);
                        shIter.remove();
                    }
                }
            }
            // Remove workgroup if it is now empty
            if (wk.isEmpty()) {
                wkIter.remove();
            }
        }

        // Do not add a share with no name if it is already found (because the already existing instance has a name in that case)
        if (alreadyFound && (shareName==null || shareName.isEmpty())) {
            return;
        }

        // Check if the workgroup is already in the list, if not create it
        Workgroup workgroup = mWorkgroups.get(workgroupName);
        if (workgroup == null) {
            workgroup = new Workgroup(workgroupName);
            mWorkgroups.put(workgroupName, workgroup);
            if (DBG) Log.d(TAG, "new workgroup "+workgroup.getName()+" added");
        }

        // Add the new share
        workgroup.addShare(shareName, shareAddress);
        if (DBG) Log.d(TAG, "added share " + shareName + " ; "+shareAddress);
        mThereIsAnUpdate = true;
        informListener(false);
    }

    @Override
    public void onInternalDiscoveryEnd(InternalDiscovery discovery, boolean aborted) {
        if (DBG) Log.d(TAG, "onInternalDiscoveryEnd "+discovery.getClass().getSimpleName()+" aborted="+aborted);
        mInternalDiscoveries.remove(discovery);

        // Tell the discovery is over when all the internal discoveries are over
        // We do not tell that the discovery is over when it is aborted because some new discovery threads may already be started
        if (mInternalDiscoveries.isEmpty() && !aborted) {
            informListener(true);
            if (DBG) Log.d(TAG, "onInternalDiscoveryEnd calls discoveryFinished");
            discoveryFinished();
        }
    }

    public interface Listener {
        /**
         * Caution: onDiscoveryStart is called AFTER start() returns (because it is "posted" to the UI thread)
         */
        public void onDiscoveryStart();

        /**
         * Caution: onDiscoveryEnd() is NOT called when the discovery is aborted using abort()
         */
        public void onDiscoveryEnd();

        public void onDiscoveryUpdate(List<Workgroup> workgroups);

        /**
         * Reports an error that stops the discovery
         */
        public void onDiscoveryFatalError();
    }

    public SambaDiscovery() {
        this(ArchosUtils.getGlobalContext());
    }

    public SambaDiscovery(Context context) {
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Set this to avoid too many updates called on the listener
     * Default is 0 (i.e. update as soon as possible)
     * Recommended is ~100 (slow enough from CPU point of view, quick enough from user point of view) 
     * @param minimumPeriodInMs
     */
    public void setMinimumUpdatePeriodInMs(long minimumPeriodInMs) {
        mMinimumPeriodInMs = minimumPeriodInMs;
    }

    /**
     * All the listener methods are called asynchronously on the UI thread (onDiscoveryStart() is called AFTER start() returns)
     * @param listener
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * throw IllegalStateException if the listener has not been added or has already been removed
     * @param listener
     */
    public void removeListener(Listener listener) {
        boolean ret = mListeners.remove(listener);
        if (!ret) {
            throw new IllegalStateException("This is not a listener (anymore?)");
        }
    }

    /**
     * Start the discovery.
     * Calling start() when the discovery is already started abort it and start it again from scratch
     */
    public void start() {
        abort();
        //reset abort variable
        mIsAborted = false;
        mWorkgroups.clear(); // reset previous results

        if (!ArchosUtils.isLocalNetworkConnected(mContext)) {
            if (DBG) Log.d(TAG, "start: no localNetworkConnected (eth/wifi), do not start discovery!");
            return;
        }

        // Tell the listener we are starting discovery
        this.getmUiHandler().post(new Runnable() {
            public void run() {
                for (SambaDiscovery.Listener listener : getmListeners()) {
                    listener.onDiscoveryStart();
                }
            }
        });

        final String ipAddress = initIpAddress();
        if (ipAddress == null) {
            // Tell the listener about the error
            this.getmUiHandler().post(new Runnable() {
                public void run() {
                    for (SambaDiscovery.Listener listener : getmListeners()) {
                        listener.onDiscoveryFatalError();
                    }
                }
            });
            return; // bye
        }

        // It's like if the user asking to start was the first update time
        mLastListenerUpdate = SystemClock.elapsedRealtime();
        mThereIsAnUpdate = false;

        // Init the TCP and UDP discoveries
        mInternalDiscoveries.add(new UdpDiscovery(this, ipAddress, mSocketReadDurationMs));
        //tcp is quicker than udp sometimes, but we only use it as fallback, so we delay it 1 sec
        mInternalDiscoveries.add(new TcpDiscovery(this, ipAddress, mSocketReadDurationMs, 1000));

        // Start all the discoveries
        if (DBG) Log.d(TAG, "Start discovery");
        for (InternalDiscovery discovery : mInternalDiscoveries) {
            discovery.start();
        }
    }

    /**
     * UDP-only discovery. Blocking method
     * @param socketReadDurationMs maximum duration
     */
    public void runUdpOnly_blocking(int socketReadDurationMs) {
        final String ipAddress = initIpAddress();
        if (ipAddress == null)
            return;

        if (DBG) Log.d(TAG, "Start UDP only discovery");
        UdpDiscovery udpDiscovery = new UdpDiscovery(this, ipAddress, socketReadDurationMs);
        mInternalDiscoveries.add(udpDiscovery); // this list will probably not be used in "blocking" mode be better be consistant
        udpDiscovery.run_blocking(); // running in the current thread
        if (DBG) Log.d(TAG, "UDP only discovery finished");
    }

    /**
     * Stop the discovery as soon as possible
     * onDiscoveryEnd() is NOT called
     */
    public void abort() {
        mIsAborted = true;

        for (InternalDiscovery discovery : mInternalDiscoveries) {
            discovery.abort();
        }
        mInternalDiscoveries.clear();
    }

    /**
     * @return true if the discovery is running. Returns false as soon as the discovery is aborted, even if the discovery thread is still running internally.
     */
    public boolean isRunning() {
        if (mIsAborted) {
            return false;
        }

        for (InternalDiscovery discovery : mInternalDiscoveries) {
            if (discovery.isAlive()) {
                return true; // found someone alive
            }
        }

        return false; // Found no one alive
    }

    void informListener(boolean doNotCheckPeriod) {
        // Nothing to tell if there is no update
        if (!mThereIsAnUpdate) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();

        // Call listener only if the update period is passed, or if we were told to not take this period into account 
        if ((now - mLastListenerUpdate > mMinimumPeriodInMs) || doNotCheckPeriod) {
            mLastListenerUpdate = now;

            // Make a copy of the workgroups and servers to avoid concurrency issues
            final List<Workgroup> workgroupsCopy;
            synchronized (mWorkgroups) {
                workgroupsCopy = new ArrayList<Workgroup>(mWorkgroups.size());
                for (Workgroup w : mWorkgroups.values()) {
                    workgroupsCopy.add(new Workgroup(w));
                }
            }

            // Tell the listener the discovery is over
            mUiHandler.post(new Runnable() {
                public void run() {
                    for (Listener listener: mListeners) {
                        listener.onDiscoveryUpdate(workgroupsCopy);
                    }
                }
            });

            mThereIsAnUpdate = false;
            mUpdateDelayed = false;
        } else if (!mUpdateDelayed){
            mUpdateDelayed = true;
            mUiHandler.postDelayed(new Runnable() {
                public void run() {
                    informListener(false);
                }
            }, 500);
        }
    }

    void discoveryFinished() {
        // Tell the listener the discovery is over
        this.getmUiHandler().post(new Runnable() {
            public void run() {
                for (SambaDiscovery.Listener listener : getmListeners()) {
                    listener.onDiscoveryEnd();
                }
            }
        });
    }

    private static native int findDoubleNatIp();
    private static native int findLocalIp();

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private String getIp(LinkProperties lp) {
        List<LinkAddress> las = lp.getLinkAddresses();
        for(LinkAddress la: las) {
            InetAddress inetAddress = la.getAddress();
            if (inetAddress instanceof Inet4Address) {
                if (DBG) Log.d(TAG, lp.getInterfaceName() + ": " + inetAddress.getHostAddress());
                return inetAddress.getHostAddress();
            }
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private LinkProperties getLP(ConnectivityManager connMgr, int cap) {
        Network nets[] = connMgr.getAllNetworks();
        for (Network n: nets) {
            LinkProperties lp = connMgr.getLinkProperties(n);
            NetworkCapabilities np = connMgr.getNetworkCapabilities(n);
            String iname =  lp.getInterfaceName();
            if (iname != null && np != null) {
                if (DBG) Log.d(TAG, ">>> " + iname + ": " + np.hasTransport(cap));
                if (np.hasTransport(cap))
                    return lp;
            }
        }
        return null;
    }

    private List<String> initIpAdresses() {
        List<String> result = new ArrayList<String>(2);
        String ip = initIpAddress();
        if (ip != null)
            result.add(ip);
        int intIpAddress = findDoubleNatIp();
        if (intIpAddress != 0) {
            byte[] bytes = BigInteger.valueOf(intIpAddress).toByteArray();
            InetAddress address = null;
            try {
                address = InetAddress.getByAddress(bytes);
            } catch (UnknownHostException ignored) {}
            if (address != null && address.isSiteLocalAddress())
                result.add(address.getHostAddress());
        }
        return result;
    }

    private static InetAddress inetFromInt(int intIpAddress) {
        if (intIpAddress != 0) {
            byte[] bytes = BigInteger.valueOf(intIpAddress).toByteArray();
            InetAddress address = null;
            try {
                address = InetAddress.getByAddress(bytes);
            } catch (UnknownHostException ignored) {}
            if (address != null && address.isSiteLocalAddress())
                return address;
        }
        return null;
    }

    private boolean isUsableIpforSMB(InetAddress inetAddress) {
        return !inetAddress.isLoopbackAddress()
                && !inetAddress.isAnyLocalAddress()
                && (inetAddress instanceof Inet4Address);
    }

    private String initIpAddress() {
        if (ArchosFeatures.isChromeOS(mContext))
            return getDoubleNatIpAddress();

        ConnectivityManager connMgr = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LinkProperties lp;
            String ipAddress = null;
            lp = getLP(connMgr, NetworkCapabilities.TRANSPORT_VPN);
            if (lp == null)
                lp = getLP(connMgr, NetworkCapabilities.TRANSPORT_ETHERNET);
            if (lp == null)
                lp = getLP(connMgr, NetworkCapabilities.TRANSPORT_WIFI);
            if (lp != null)
                ipAddress =  getIp(lp);
            if (ipAddress != null)
                return ipAddress;
        }

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if (!intf.isUp())
                    continue;
                String name = intf.getDisplayName();
                if (!name.startsWith("tun") && !name.startsWith("tap"))
                    continue;
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (isUsableIpforSMB(inetAddress)) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG,"getIpAddress", e);
        }

        final android.net.NetworkInfo wifi = connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifi.isConnected()) {
            WifiManager myWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            WifiInfo myWifiInfo = myWifiManager.getConnectionInfo();
            int ipAddress = myWifiInfo.getIpAddress();
            InetAddress address = inetFromInt(ipAddress);
            if (address != null)
                return address.getHostAddress();
        }
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if (!intf.isUp())
                    continue;
                String name = intf.getDisplayName();
                if (name.startsWith("tun") || name.startsWith("tap") || name.contains("wlan"))
                    continue;
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (isUsableIpforSMB(inetAddress)) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG,"getIpAddress", e);
        }
        
    return getLocalIpAddress();
    }

    public static String getDoubleNatIpAddress() {
        int intIpAddress = findDoubleNatIp();
        InetAddress address = inetFromInt(intIpAddress);
        if (address != null)
            return address.getHostAddress();
        return null;
    }

    public static String getLocalIpAddress() {
        int intIpAddress = findLocalIp();
        InetAddress address = inetFromInt(intIpAddress);
        if (address != null)
            return address.getHostAddress();
        return null;
    }

    static {
        System.loadLibrary("filecoreutils");
    }

}
