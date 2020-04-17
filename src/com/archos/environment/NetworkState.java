// Copyright 2020 Courville Software
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

package com.archos.environment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.util.Log;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

/** network state updated from NetworkStateReceiver, should always represent the current state */
public class NetworkState {
    private static final String TAG = NetworkState.class.getSimpleName();
    private static final boolean DBG = false;

    private boolean mConnected;
    private boolean mHasLocalConnection;
    public static boolean isNetworkConnected;
    private Context mContext;
    private static ConnectivityManager mConnectivityManager;
    private static ConnectivityManager.NetworkCallback mNetworkCallback = null;

    // support to notify Observers
    private static PropertyChangeSupport propertyChangeSupport;
    // two states to communicate with Observers
    public static final String LAN_STATE = "lan_state";
    public static final String WAN_STATE = "wan_state";

    // singleton, volatile to make double-checked-locking work correctly
    private static volatile NetworkState sInstance;

    /** may return null but no Context required */
    public static NetworkState peekInstance() {
        return sInstance;
    }

    /** get's the instance, context is used for initial update */
    public static NetworkState instance(Context context) {
        if (sInstance == null) {
            synchronized (NetworkState.class) {
                if (sInstance == null) {
                    NetworkState state = new NetworkState(context.getApplicationContext());
                    sInstance = state;
                }
            }
        }
        return sInstance;
    }

    protected NetworkState(Context context) {
        mContext = context;
        // set initial state
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mConnected = isNetworkConnected(context);
        mHasLocalConnection = isLocalNetworkConnected(mContext);
        propertyChangeSupport = new PropertyChangeSupport(context);
    }

    public boolean hasLocalConnection() { return mHasLocalConnection; }

    public boolean isConnected() { return mConnected; }

    public boolean updateFrom() {
        // returns true when that changes hasLocalConnection
        boolean returnBoolean = false;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean connected = isNetworkConnected(mContext);
        if (connected != mConnected) { // only fire change if there is a change
            if (DBG) Log.d(TAG, "updateFrom: connected changed notifying, " + mConnected + "->" +  connected);
            propertyChangeSupport.firePropertyChange(WAN_STATE, mConnected, connected);
            mConnected = connected;
        }
        boolean hasLocalConnection = isLocalNetworkConnected(mContext) || preferences.getBoolean("vpn_mobile", false);
        if (hasLocalConnection != mHasLocalConnection) { // only fire change if there is a change
            if (DBG) Log.d(TAG, "updateFrom: hasLocalConnection changed notifying, " + mHasLocalConnection + "->" +  hasLocalConnection);
            returnBoolean = true;
            propertyChangeSupport.firePropertyChange(LAN_STATE, mHasLocalConnection, hasLocalConnection);
            mHasLocalConnection = hasLocalConnection;
        }
        return returnBoolean;
    }

    public static boolean isNetworkConnected(Context context) {
        // Check network status
        if (context == null) return false;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null)
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        // to test hasInternet capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (DBG) Log.d(TAG, "isNetworkConnected: true");
                        return true;
                    }
            } else {
                try {
                    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected()) {
                        if (DBG) Log.d(TAG, "isNetworkConnected: true");
                        return true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "isNetworkConnected: caught exception" + e.getMessage());
                }
            }
        }
        if (DBG) Log.d(TAG, "isNetworkConnected false");
        return false;
    }

    public static boolean isLocalNetworkConnected(Context context) {
        // Check if LAN i.e. WIFI or ETHERNET
        if (context == null) return false;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null)
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        if (DBG) Log.d(TAG, "isLocalNetworkConnected: true (WIFI)");
                        return true;
                    } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        if (DBG) Log.d(TAG, "isLocalNetworkConnected: true (ETHERNET)");
                        return true;
                    }
            } else {
                try {
                    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected() &&
                            (networkInfo.getType() == ConnectivityManager.TYPE_WIFI || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                        return true;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "isLocalNetworkConnected: caught exception" + e.getMessage());
                }
            }
        }
        if (DBG) Log.d(TAG, "isLocalNetworkConnected: false");
        return false;
    }

    public static boolean isWifiAvailable(Context context) {
        // Check if WIFI available
        if (context == null) return false;
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
                if (capabilities != null)
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                        return true;
            } else {
                try {
                    NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                    if (networkInfo != null && networkInfo.isConnected() &&
                            (networkInfo.getType() == ConnectivityManager.TYPE_WIFI))
                        return true;
                } catch (Exception e) {
                    Log.w(TAG, "isWiFiAvailable: caught exception" + e.getMessage());
                }
            }
        }
        return false;
    }

    public static String getIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)) { // we get both ipv4 and ipv6, we want ipv4
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG,"getIpAddress", e);
        }
        return null;
    }

    public int getAvailableNetworksCount() {
        int count = 0;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Network[] allNetworks = mConnectivityManager.getAllNetworks(); // added in API 21 (Lollipop)
            for (Network network : allNetworks) {
                NetworkCapabilities networkCapabilities = mConnectivityManager.getNetworkCapabilities(network);
                if (networkCapabilities != null)
                    if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                        count++;
            }
        }
        return count;
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        if (DBG) Log.d(TAG, "addPropertyChangeListener");
        propertyChangeSupport.addPropertyChangeListener(propertyChangeListener);
        if (DBG) Log.d(TAG, "addPropertyChangeListener: number of Listeners=" + propertyChangeSupport.getPropertyChangeListeners().length);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        if (DBG) Log.d(TAG, "removePropertyChangeListener");
        propertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
        if (DBG) Log.d(TAG, "removePropertyChangeListener: number of Listeners=" + propertyChangeSupport.getPropertyChangeListeners().length);
    }

    public void removeAllPropertyChangeListener() {
        if (DBG) Log.d(TAG, "removeAllPropertyChangeListener");
        for (PropertyChangeListener propertyChangeListener : propertyChangeSupport.getPropertyChangeListeners())
            propertyChangeSupport.removePropertyChangeListener(propertyChangeListener);
    }

    public void registerNetworkCallback() { // for API21+
        try {
            if (mNetworkCallback == null) {
                updateFrom(); // need to update initial state
                ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkRequest.Builder builder = new NetworkRequest.Builder();
                assert connectivityManager != null;
                mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull android.net.Network network) {
                        super.onAvailable(network);
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onAvailable");
                        isNetworkConnected = true;
                        updateFrom();
                    }
                    @Override
                    public void onLost(@NonNull android.net.Network network) {
                        // note that onLost is not sent when loosing wifi and 4G is connected
                        super.onLost(network);
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onLost");
                        updateFrom();
                        if (getAvailableNetworksCount() == 0) { // need to be sure that there is really no interface working
                            isNetworkConnected = false;
                        }
                    }
                    @Override
                    public void onBlockedStatusChanged(@NonNull Network network, boolean blocked) {
                        super.onBlockedStatusChanged(network, blocked);
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onBlockedStatusChanged");
                    }
                    @Override
                    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                        super.onCapabilitiesChanged(network, networkCapabilities);
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onCapabilitiesChanged");
                        updateFrom();
                    }
                    @Override
                    public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
                        super.onLinkPropertiesChanged(network, linkProperties);
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onLinkPropertiesChanged");
                    }
                    @Override
                    public void onLosing(@NonNull Network network, int maxMsToLive) {
                        super.onLosing(network, maxMsToLive);
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onLosing");
                    }
                    @Override
                    public void onUnavailable() {
                        super.onUnavailable();
                        if (DBG) Log.d(TAG, "registerNetworkCallback: onUnavailable");
                    }
                };
                connectivityManager.registerNetworkCallback(builder.build(), mNetworkCallback);
            }
        } catch (Exception e) {
            Log.w(TAG, "registerNetworkCallback: caught exception");
            updateFrom();
            isNetworkConnected = false;
        }
    }

    public void unRegisterNetworkCallback() { // for API21+
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connectivityManager != null;
        connectivityManager.unregisterNetworkCallback(mNetworkCallback);
        mNetworkCallback = null;
    }
}
