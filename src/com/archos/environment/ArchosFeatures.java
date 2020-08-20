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
import android.content.res.Configuration;
import android.os.Build;

public final class ArchosFeatures {

    //returns whereas device is a TV or not
    public static boolean isTV(Context ct) {
        return isAndroidTV(ct);
    }

    // tokeep: sentimental LUDO was Archos TV connect, first Android on TV before AndroidTV in 2012
    public static boolean isLUDO() {
        if (Build.MODEL.equals("LUDO") || Build.MODEL.equals("A101XS")) return true;
        return false;
    }

    public static boolean isAndroidTV(Context ct) {
        UiModeManager uiModeManager = (UiModeManager) ct.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static boolean isChromeOS(Context context) {
        return context.getPackageManager().hasSystemFeature("org.chromium.arc.device_management");
    }
}
