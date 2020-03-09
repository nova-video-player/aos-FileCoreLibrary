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

package com.archos.environment;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.WeakHashMap;

/** network state updated from NetworkStateReceiver, should always represent the current state */
public class NetworkState {
    private static final String TAG = NetworkState.class.getSimpleName();
    private static final boolean DBG = false;

    private boolean mConnected;
    private boolean mHasLocalConnection;
    // abusing WeakHashMap to have a list of WeakReferences to Observers
    private final WeakHashMap<Observer, Void> mObservers = new WeakHashMap<Observer, Void>();
    private Context mContext;

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
        updateFrom(context);
    }
    
    public boolean hasLocalConnection() { return mHasLocalConnection; }

    public boolean isConnected() { return mConnected; }

    public boolean updateFrom(Context context) {
        // returns true when that changes hasLocalConnection
        boolean returnBoolean = false;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean connected = isNetworkConnected(context);
        if (connected != mConnected) {
            if (DBG) Log.d(TAG, "updateFrom: connected changed " + mConnected + "->" +  connected);
            mConnected = connected;
        }
        boolean hasLocalConnection = isLocalNetworkConnected(mContext) || preferences.getBoolean("vpn_mobile", false);
        if (hasLocalConnection != mHasLocalConnection) {
            if (DBG) Log.d(TAG, "updateFrom: connected changed " + mHasLocalConnection + "->" +  hasLocalConnection);
            mHasLocalConnection = hasLocalConnection;
            returnBoolean = true;
            handleChange();
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

    /** implement & register for observing hasLocalConnection() */
    public interface Observer {
        // TODO nobody uses it!!!
        public void onLocalNetworkState(boolean available);
    }

    public void addObserver(Observer observer) {
        if (observer == null) {
            throw new NullPointerException();
        }
        synchronized (this) {
            if (!mObservers.containsKey(observer))
                mObservers.put(observer, null);
        }
    }

    public synchronized void removeObserver(Observer observer) {
        mObservers.remove(observer);
    }

    private synchronized void handleChange() {
        for(Observer observer : mObservers.keySet()) {
            if (observer != null)
                observer.onLocalNetworkState(mHasLocalConnection);
        }
    }

}
