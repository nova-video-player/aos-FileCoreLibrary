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
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


public final class ArchosUtils {
    private static final String TAG = "ArchosUtils";
    private static final boolean DBG = false;

    private static Context globalContext;

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

    public static boolean isAmazonApk() {
       return android.os.Build.MANUFACTURER.toLowerCase().equals("amazon");
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

    public static String getNameWithoutExtension(String filenameWithExtension) {
        int dotPos = filenameWithExtension.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filenameWithExtension.length()) {
            return filenameWithExtension.substring(0, dotPos);
        } else {
            return filenameWithExtension;
        }
    }

    public static String getExtension(String filename) {
        if (filename == null)
            return null;
        int dotPos = filename.lastIndexOf('.');
        if (dotPos >= 0 && dotPos < filename.length()) {
            return filename.substring(dotPos + 1).toLowerCase();
        }
        return null;
    }

    public static void setGlobalContext(Context globalContext) {
        ArchosUtils.globalContext = globalContext;
    }

    public static Context getGlobalContext() {
        return globalContext;
    }

}
