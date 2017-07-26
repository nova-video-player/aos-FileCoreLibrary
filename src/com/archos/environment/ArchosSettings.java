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
import android.provider.Settings;

/**
 * The Settings provider contains global system-level device preferences.
 */
public final class ArchosSettings {

        /**
         * TV Overscan percent : 0..100
         */
        public static final String OVERSCAN = "overscan";

        /**
         * Whether the device is in demo mode.
         *
         * nonzero = device in demo mode
         * 0 = device not in demo mode
         */
        public static final String DEMO_MODE_ACTIVE = "demo_mode_active";

         /**
         * Whether 3G key is on.
         */
        public static final String KEY_3G_ON = "key_3g_on";

         /**
         * Whether 3g stick sleep policy.
         */
        public static final String KEY_3G_SLEEP_POLICY = "key_3g_sleep_policy";

         /**
         * Whether 3g stick sleep policy timeout.
         */
        public static final String KEY_3G_SLEEP_POLICY_TIMEOUT_MS = "key_3g_sleep_policy_timeout_ms";

         /**
         * Whether we add telephony gsm feature or not.
         */
        public static final String ALLOW_MOCK_TELEPHONY_GSM = "mock_telephony_gsm";

         /**
         * Whether we add rear camera feature or not.
         */
        public static final String ALLOW_MOCK_CAMERA_REAR = "mock_camera_rear";

        /**
         * Whether we add gps feature or not.
         */
        public static final String ALLOW_MOCK_GPS = "mock_gps";

         /**
         * Whether we add compass feature or not.
         */
        public static final String ALLOW_MOCK_COMPASS = "mock_compass";

         /**
         * Whether deep sleep power management must be activated or not.
         */
        public static final String DEEP_SLEEP_PM = "deep_sleep_pm";

        /**
         * Whether to use static IP and other static network attributes.
         * <p>
         * Set to 1 for true and 0 for false.
         */
        public static final String ETHERNET_USE_STATIC_IP = "ethernet_use_static_ip";

        /**
         * The static IP address.
         * <p>
         * Example: "192.168.1.51"
         */
        public static final String ETHERNET_STATIC_IP = "ethernet_static_ip";

        /**
         * If using static IP, the gateway's IP address.
         * <p>
         * Example: "192.168.1.1"
         */
        public static final String ETHERNET_STATIC_GATEWAY = "ethernet_static_gateway";

        /**
         * If using static IP, the net mask.
         * <p>
         * Example: "255.255.255.0"
         */
        public static final String ETHERNET_STATIC_NETMASK = "ethernet_static_netmask";

        /**
         * If using static IP, the primary DNS's IP address.
         * <p>
         * Example: "192.168.1.1"
         */
        public static final String ETHERNET_STATIC_DNS1 = "ethernet_static_dns1";

        /**
         * If using static IP, the secondary DNS's IP address.
         * <p>
         * Example: "192.168.1.2"
         */
        public static final String ETHERNET_STATIC_DNS2 = "ethernet_static_dns2";


        public static boolean isDemoModeActive(Context context) {
            int demoModeActive = Settings.System.getInt(context.getContentResolver(), DEMO_MODE_ACTIVE, 0);
            return (demoModeActive == 1);
        }
}
