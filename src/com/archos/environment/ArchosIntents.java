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

public class ArchosIntents {

    /**
     * Broadcast Action: suspend the device.
     */
    public static final String ACTION_SUSPEND = "archos.intent.action.SUSPEND";

    /**
     * Broadcast Action: reboot from userland.
     */
    public static final String ACTION_USER_REBOOT = "archos.intent.action.USER_REBOOT";

    /**
     * Broadcast Action: shutdown from userland.
     */
    public static final String ACTION_USER_SHUTDOWN = "archos.intent.action.USER_SHUTDOWN";

    /**
     */
    public static final String ARCHOS_LAUNCH_AUDIOPLAYER = "archos.intent.action.LAUNCH_AUDIOPLAYER";

    /**
     */
    public static final String ARCHOS_RESUME_AUDIOPLAYER = "archos.intent.action.RESUME_AUDIOPLAYER";

    /**
     */
    public static final String ARCHOS_RESUME_VIDEOPLAYER = "archos.intent.action.RESUME_VIDEOPLAYER";

    /**
     * Broadcast Action: Launch Audio Settings Menu
     */
    public static final String ACTION_SHOW_AUDIOSETTINGS = "archos.intent.action.SHOW_AUDIOSETTINGS";
    

    /**
     * Broadcast Action: resuming after suspend
     */
    public static final String ACTION_RESUMING_FROM_SUSPEND = "archos.intent.action.RESUMING_FROM_SUSPEND";

    /**
     * The name of the extra used to indicate whether background of AudioSettingsActivity should be transparent
     */
    public static final String EXTRA_SHOW_AUDIOSETTINGS_TRANSPARENT = "archos.intent.extras.SHOW_AUDIOSETTINGS_TRANSPARENT";

    /**
     */
    public static final String ACTION_CHECK_NETWORK_SHARES = "archos.intent.action.CHECK_NETWORK_SHARES";

    /**
     * Broadcast Action: Fusesmb state changed
     */
    public static final String ACTION_SMB_STATE_CHANGED = "archos.intent.action.SMB_STATE_CHANGED";

    /**
     * Broadcast Action: Fusesmb state
     */
    public static final String EXTRA_SMB_STATE = "archos.intent.extra.SMB_STATE";


    /**
     * Broadcast Action: Fusesmb stop scanning
     */
    public static final String ACTION_SMB_SCAN_STOP = "archos.intent.action.SMB_STOP_SCAN";

    /**
     * Broadcast Action: upnp state changed
     */
    public static final String ACTION_UPNP_STATE_CHANGED = "archos.intent.action.UPNP_STATE_CHANGED";

    /**
     * Broadcast Action: upnp state
     */
    public static final String EXTRA_UPNP_STATE = "archos.intent.extra.UPNP_STATE";

    /**
     * Broadcast Action: djmount stop scanning
     */
    public static final String ACTION_UPNP_SCAN_STOP = "archos.intent.action.UPNP_STOP_SCAN";
   
    /**
     * Broadcast Action: start download firmware update
     */
    public static final String ACTION_DAOS_UPDATE_NOW = "archos.intent.action.DAOS_UPDATE_NOW";

    /**
     * Broadcast Action: notify that the mouse cursor is hidden/shown
     */
    public static final String ARCHOS_MOUSE_EVENT = "archos.intent.action.MOUSE_EVENT";


    /**
     */
    public static final String EXTRA_DATA_KEY_STATE = "archos.intent.extra.EXTRA_DATA_KEY_STATE";


    /**
     * Broadcast Action: Start to play video instead of screen off in demo mode
     */
    public static final String ACTION_DEMO_MODE_PLAY_VIDEO = "archos.intent.action.DEMO_MODE_PLAY_VIDEO";

    /**
     * Service Action: Run DemoMode Service to display message that feature is disabled in demo mode
     */
    public static final String ACTION_DEMO_MODE_FEATURE_DISABLED = "archos.intent.action.DEMO_MODE_FEATURE_DISABLED";
    
    /**
     * Service Action: Run DemoMode Service to display message how to disable demo mode
     */
    public static final String ACTION_DEMO_MODE_SHOW_HOWTO_DISABLE = "archos.intent.action.ACTION_DEMO_MODE_SHOW_HOWTO_DISABLE";
    
    /**
     * Service Action: Run DemoMode Service to disable demo mode.
     */
    public static final String ACTION_DEMO_MODE_DISABLE = "archos.intent.action.DEMO_MODE_DISABLE";
    
    /**
     * Service Action: Run DemoMode Service to enable demo mode.
     */
    public static final String ACTION_DEMO_MODE_ENABLE = "archos.intent.action.DEMO_MODE_ENABLE";
    
    /**
     * Broadcast Action: Launch a scan of the external volume
     */
    public static final String ARCHOS_SCAN_EXTVOL ="archos.intent.action.SCAN_EXTVOL";


    /**
     * Broadcast Action: Intent to reset default hdd content scraper data
     */
    public static final String MEDIASCANNER_MEDIASCRAPER_RESET ="archos.intent.action.MEDIASCANNER_MEDIASCRAPER_RESET";

    /**
     * Broadcast Action: Flush the media library, reboot and rebuild it
     */
    public static final String MEDIA_LIBRARY_FLUSH ="archos.intent.action.MEDIA_LIBRARY_FLUSH";
    
    /**
     * Broadcast Action: remove usb dialog
     */
    public static final String ACTION_USB_VETO_ON = "archos.intent.action.USB_VETO_ON";

    /**
     * Broadcast Action: restore usb dialog
     */
    public static final String ACTION_USB_VETO_OFF = "archos.intent.action.USB_VETO_OFF";
 
    /**
     */
    public static final String ACTION_DATA_KEY_CONNECTION = "archos.intent.action.ACTION_DATA_KEY_CONNECTION";

    /**
     */
    public static final String ACTION_DISPLAY_SWITCH = "archos.intent.action.ACTION_DISPLAY_SWITCH";

    /**
     */
    public static final String ACTION_KEY_3G_PORT_STATE_CHANGED = "archos.intent.action.KEY_3G_PORT_STATE_CHANGED";

    /**
     */
    public static final String ACTION_KEY_3G_SETTINGS = "archos.settings.KEY_3G_SETTINGS";

    /**
     * Broadcast Action: hook for performing whatever you want after an update has been
     * received via MTP.
     *
     * The Broadcast is sent, when an update has been received via MTP
     */
    public static final String ACTION_MTP_UPDATE_RECEIVED = "archos.intent.action.MTP_UPDATE_RECEIVED";

    /**
     */
    public static final String ACTION_USB_DEVICE_ATTACHED = "archos.intent.action.USB_DEVICE_ATTACHED";

    /**
     */
    public static final String ACTION_USB_HOST_STATE_CHANGED = "archos.hardware.usb.action.USB_HOST_STATE";

    /**
     */
    public static final String ACTION_UPDATE_HDMI_STATE  = "archos.intent.action.UPDATE_HDMI_STATE";

    /**
     */
    public static final String ACTION_DLNA_SCAN_START  = "archos.intent.action.DLNA_SCAN_START";

    /**
     */
    public static final String ACTION_DLNA_SCAN_STOP  = "archos.intent.action.DLNA_SCAN_STOP";

    /**
     * Starts both Audio and Video Network Scanner for a path given as data Uri
     */
    public static final String ACTION_MEDIA_SCANNER_SCAN_FILE = "archos.intent.action.MEDIA_SCANNER_SCAN_FILE";

    /**
     * opposite of {@link #ACTION_MEDIA_SCANNER_SCAN_FILE}, removes a path gives as data Uri from both Video and Music
     * Databases.
     */
    public static final String ACTION_MEDIA_SCANNER_REMOVE_FILE = "archos.intent.action.MEDIA_SCANNER_REMOVE_FILE";

    public static final String ACTION_CEC_PLUGGED = "archos.intent.action.CEC_PLUGGED";

    /**
     */
    public final static String EXTRA_CEC_PLUGGED_STATE = "state";
}
