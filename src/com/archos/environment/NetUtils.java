// Courville Software adapted from https://github.com/M7mdZain/InternetConnectivityLibrary to capture LAN and WAN
//
// Modifications licensed under the Apache License, Version 2.0 (the "License");
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

package com.archos.environment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

// Note that this code has not be debugged yet

public class NetUtils implements LifecycleObserver {
    private static final String TAG = NetUtils.class.getSimpleName();
    private static final boolean DBG = false;

    private ConnectivityManager mConnectivityMgr;
    private Context mContext;
    private NetworkStateReceiver mNetworkStateReceiver;
    /*
     * boolean indicates if my device is connected to the internet or not
     * */
    private boolean mIsOnWAN = false;
    private boolean mIsOnLAN = false;
    private ConnectionMonitor mConnectionMonitor;
    /**
     * Indicates there is no available network.
     */
    private static final int NO_NETWORK_AVAILABLE = -1;
    /**
     * Indicates this network uses a Cellular transport.
     */
    public static final int TRANSPORT_CELLULAR = 0;
    /**
     * Indicates this network uses a Wi-Fi transport.
     */
    public static final int TRANSPORT_WIFI = 1;
    /**
     * Indicates this network uses a Ethernet transport.
     */
    public static final int TRANSPORT_ETHERNET = 2;

    public interface ConnectionStateListener {
        void onAvailable(boolean isAvailable); // for global connectivity
        void onLanAvailable(boolean isAvailable); // for lan connectivity
        void onWanAvailable(boolean isAvailable); // for wan connectivity
        void onChange(boolean isChanged); // for any change
    }

    public NetUtils(Context context) {
        mContext = context;
        mConnectivityMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        ((AppCompatActivity) mContext).getLifecycle().addObserver(this);
        mConnectionMonitor = new ConnectionMonitor();
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();
        mConnectivityMgr.registerNetworkCallback(networkRequest, mConnectionMonitor);
        // proper state init
        mIsOnLAN = isOnLAN();
        mIsOnWAN = isOnWAN();
    }

    /**
     * Returns true if connected to the internet, and false otherwise
     */
    public boolean isOnWAN() {
        mIsOnWAN = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Checking internet connectivity
            NetworkInfo activeNetwork = null;
            if (mConnectivityMgr != null) {
                activeNetwork = mConnectivityMgr.getActiveNetworkInfo(); // Deprecated in API 29
            }
            mIsOnWAN = activeNetwork != null;
        } else {
            Network[] allNetworks = mConnectivityMgr.getAllNetworks(); // added in API 21 (Lollipop)
            for (Network network : allNetworks) {
                NetworkCapabilities networkCapabilities = mConnectivityMgr.getNetworkCapabilities(network);
                if (networkCapabilities != null) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                        mIsOnWAN = true;
                }
            }
        }
        return mIsOnWAN;
    }

    /**
     * Returns true if connected to the local network, and false otherwise
     */
    public boolean isOnLAN() {
        mIsOnLAN = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Checking internet connectivity
            NetworkInfo activeNetwork = null;
            if (mConnectivityMgr != null) {
                activeNetwork = mConnectivityMgr.getActiveNetworkInfo(); // Deprecated in API 29
                if (activeNetwork != null && activeNetwork.isConnected() &&
                        (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI || activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                    mIsOnLAN = true;
                }
            }
        } else {
            Network[] allNetworks = mConnectivityMgr.getAllNetworks(); // added in API 21 (Lollipop)
            for (Network network : allNetworks) {
                NetworkCapabilities networkCapabilities = mConnectivityMgr.getNetworkCapabilities(network);
                if (networkCapabilities != null) {
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                        mIsOnLAN = true;
                }
            }
        }
        return mIsOnLAN;
    }

    /**
     * Returns
     * <p> <p>
     * <p><p> NO_NETWORK_AVAILABLE >>> when you're offline
     * <p><p> TRANSPORT_CELLULAR >> When Cellular is the active network
     * <p><p> TRANSPORT_WIFI >> When Wi-Fi is the Active network
     * <p><p> TRANSPORT_ETHERNET >> When Ethernet is the Active network
     * <p>
     */
    public int getActiveNetwork() {
        NetworkInfo activeNetwork = mConnectivityMgr.getActiveNetworkInfo(); // Deprecated in API 29
        if (activeNetwork != null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkCapabilities capabilities = mConnectivityMgr.getNetworkCapabilities(mConnectivityMgr.getActiveNetwork());
                if (capabilities != null)
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        return TRANSPORT_CELLULAR;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return TRANSPORT_WIFI;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return TRANSPORT_ETHERNET;
                    }
            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) { // Deprecated in API 28
                    return TRANSPORT_CELLULAR;
            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) { // Deprecated in API 28
                    return TRANSPORT_WIFI;
            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET) { // Deprecated in API 28
                return TRANSPORT_WIFI;
            }
        return NO_NETWORK_AVAILABLE;
    }

    public int getAvailableNetworksCount() {
        int count = 0;
        Network[] allNetworks = mConnectivityMgr.getAllNetworks(); // added in API 21 (Lollipop)
        for (Network network : allNetworks) {
            NetworkCapabilities networkCapabilities = mConnectivityMgr.getNetworkCapabilities(network);
            if (networkCapabilities != null)
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    count++;
        }
        return count;
    }

    public List<Integer> getAvailableNetworks() {
        List<Integer> activeNetworks = new ArrayList<>();
        Network[] allNetworks; // added in API 21 (Lollipop)
        allNetworks = mConnectivityMgr.getAllNetworks();
        for (Network network : allNetworks) {
            NetworkCapabilities networkCapabilities = mConnectivityMgr.getNetworkCapabilities(network);
            if (networkCapabilities != null) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    activeNetworks.add(TRANSPORT_WIFI);
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    activeNetworks.add(TRANSPORT_CELLULAR);
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    activeNetworks.add(TRANSPORT_CELLULAR);
            }
        }
        return activeNetworks;
    }

    public void onInternetStateListener(ConnectionStateListener listener) {
        mConnectionMonitor.setOnConnectionStateListener(listener);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy");
        ((AppCompatActivity) mContext).getLifecycle().removeObserver(this);
        if (mConnectionMonitor != null)
            mConnectivityMgr.unregisterNetworkCallback(mConnectionMonitor);
    }

    public class NetworkStateReceiver extends BroadcastReceiver {
        ConnectionStateListener mListener;
        public NetworkStateReceiver(ConnectionStateListener listener) {
            mListener = listener;
        }
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getExtras() != null) {
                NetworkInfo activeNetworkInfo = mConnectivityMgr.getActiveNetworkInfo(); // deprecated in API 29
                if (mIsOnWAN != isOnWAN() && activeNetworkInfo != null && activeNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    if (DBG) Log.d(TAG, "onReceive: " + "Connected to WAN: " + activeNetworkInfo.getTypeName());
                    mIsOnWAN = true;
                    mListener.onWanAvailable(true);
                } else if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
                    if (!isOnWAN()) {
                        mListener.onWanAvailable(false);
                        mIsOnWAN = false;
                    }
                }
                if (mIsOnLAN != isOnLAN() && activeNetworkInfo != null && activeNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                    if (DBG) Log.d(TAG, "onReceive: " + "Connected LAN: " + activeNetworkInfo.getTypeName());
                    mIsOnLAN = true;
                    mListener.onLanAvailable(true);
                } else if (intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
                    if (!isOnLAN()) {
                        mListener.onLanAvailable(false);
                        mIsOnWAN = false;
                    }
                }
            }
        }
    }

    public class ConnectionMonitor extends ConnectivityManager.NetworkCallback {
        private ConnectionStateListener mConnectionStateListener;
        void setOnConnectionStateListener(ConnectionStateListener connectionStateListener) {
            mConnectionStateListener = connectionStateListener;
        }
        @Override
        public void onAvailable(@NonNull Network network) {
            if (!mIsOnWAN && !mIsOnLAN && (mIsOnWAN != isOnWAN() || mIsOnLAN != isOnLAN()))
                mConnectionStateListener.onAvailable(true);
            if (!mIsOnWAN && mIsOnWAN != isOnWAN()) {
                if (mConnectionStateListener != null) {
                    mConnectionStateListener.onWanAvailable(true);
                    mConnectionStateListener.onChange(true);
                    mIsOnWAN = true;
                }
            }
            if (!mIsOnLAN && mIsOnLAN != isOnLAN()) {
                if (mConnectionStateListener != null) {
                    mConnectionStateListener.onLanAvailable(true);
                    mConnectionStateListener.onChange(true);
                    mIsOnLAN = true;
                }
            }
        }
        @Override
        public void onLost(@NonNull Network network) {
            if (!isOnWAN() && !isOnLAN() && (mIsOnWAN != isOnWAN() || mIsOnLAN != isOnLAN()))
                mConnectionStateListener.onAvailable(false);
            if (mIsOnWAN && mIsOnWAN != isOnWAN()) {
                if (mConnectionStateListener != null) {
                    mConnectionStateListener.onWanAvailable(false);
                    mConnectionStateListener.onChange(true);
                    mIsOnWAN = false;
                }
            }
            if (mIsOnLAN && mIsOnLAN != isOnLAN()) {
                if (mConnectionStateListener != null) {
                    mConnectionStateListener.onLanAvailable(false);
                    mConnectionStateListener.onChange(true);
                    mIsOnLAN = false;
                }
            }
        }
    }
}
