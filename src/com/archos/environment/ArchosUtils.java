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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;


public final class ArchosUtils {
    private static final String TAG = "ArchosUtils";

    private static Context globalContext;

    @SuppressWarnings("unused")
    public static void ArchosRKDeviceOnlyBarrier(final Activity activity) {
    	if (!Build.MANUFACTURER.equals("archos") ||
    			(!Build.MODEL.equals("ARCHOS 80XSK") &&
    					!Build.MODEL.equals("A70GT")))  {
    		AlertDialog alertDialog = new AlertDialog.Builder(activity).setTitle("Error")
    				.setMessage("This application in not authorized on this device.")
    				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
    					public void onClick(DialogInterface dialog, int which) {
    						activity.finish();
    					}
    				}).create();
    		alertDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
    			public void onDismiss(DialogInterface dialog) {
    				activity.finish();
    			}
    		});
    		alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
    		alertDialog.setCancelable(false);
    		alertDialog.show();
    	}
    }

    public static boolean isNetworkConnected(Context context) {
        // Check network status
        boolean networkEnabled = false;
        ConnectivityManager connectivity = (ConnectivityManager)(context.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                networkEnabled = true;
            }
        }

        return networkEnabled;
    }

    public static boolean isLocalNetworkConnected(Context context) {
        // Check network status
        boolean networkEnabled = false;
        ConnectivityManager connectivity = (ConnectivityManager)(context.getSystemService(Context.CONNECTIVITY_SERVICE));
        if (connectivity != null) {
            NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected() &&
                    (networkInfo.getType() == ConnectivityManager.TYPE_WIFI || networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                networkEnabled = true;
            }
        }

        return networkEnabled;
    }

    public static boolean isArchosDevice(@SuppressWarnings("UnusedParameters") Context context) {
        File file = new File("/system/framework/com.archos.frameworks.jar");
        return (file.exists() && Build.MANUFACTURER.equalsIgnoreCase("archos"));
    }

    public static boolean isFreeVersion(Context context) {
        return context.getPackageName().endsWith("free") && !isArchosDevice(context);
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

    public static boolean shouldAnimate() {
        return !(Build.MANUFACTURER.equalsIgnoreCase("samsung")&&Build.VERSION.SDK_INT==Build.VERSION_CODES.JELLY_BEAN_MR2); //do not animate fragments on samsung with 4.3
    }
}
