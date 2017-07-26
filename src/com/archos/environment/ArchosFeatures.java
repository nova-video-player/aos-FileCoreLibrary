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

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;


public final class ArchosFeatures {

    /**
     * Returns whether board has a builtin GSM
     * @param context
     */
    public static boolean hasGsm(Context context) {
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        int phoneType = manager.getPhoneType();
        if (phoneType != TelephonyManager.PHONE_TYPE_NONE)
            return true;
        String value = SystemPropertiesProxy.get("ro.board.has_gsm", "no");
        return value.equals("yes");
    }

    /**
     * Returns whether board is emulator
     */
    public static boolean isEmulator() {
        String value = SystemPropertiesProxy.get("ro.board.emulator", "no");
        return value.equals("yes");
    }

    /**
     * Returns whether the device has a HDD
     */
    public static boolean hasHDD() {
        String value = SystemPropertiesProxy.get("ro.board.has_hdd", "no");
        return value.equals("yes");
    }

    //returns whereas device is a TV or not
    public static boolean isTV(Context ct) {
        if (hasNoTouchScreen())
            return true;
        return isAndroidTV(ct);
    }

    public static boolean isLUDO() {
        return getProductName().equals("LUDO")|| getProductName().equals("A101XS");
    }

    public static boolean isAndroidTV(Context ct) {
        UiModeManager uiModeManager = (UiModeManager) ct.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static boolean isChromeOS(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature("org.chromium.arc");
    }

    /**
     * Returns the product name
     */
    public static String getProductName() {
        return SystemPropertiesProxy.get("ro.hardware_product", "unknown");
    }
    /**
     * Returns whether board has a battery
     */
    public static boolean hasBattery() {
        String value = SystemPropertiesProxy.get("ro.board.has_battery", "yes");
        return value.equals("yes");
    }
    
    /**
     * Returns whether board has a touchscreen or not
     */
    public static boolean hasNoTouchScreen() {
        String value = SystemPropertiesProxy.get("ro.board.notouchscreen", "no");
        return value.equals("yes");
    }
}
