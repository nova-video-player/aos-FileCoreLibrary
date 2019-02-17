// Copyright 2019 Courville Software
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

package com.archos.filecorelibrary.jcifs;

import android.util.Log;

import jcifs.CIFSException;
import jcifs.context.BaseContext;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import java.util.Properties;


public class JcifsUtils {

    private final static String TAG = "JcifsUtils";
    
    private static Properties prop = null;
    private static CIFSContext baseContextSmb1 = createContext(false);
    private static CIFSContext baseContextSmb2 = createContext(true);

    private static CIFSContext createContext(boolean isSmb2) {
        prop = new Properties();
        prop.putAll(System.getProperties());

        prop.put("jcifs.smb.client.enableSMB2", String.valueOf(isSmb2));
        prop.put("jcifs.smb.client.disableSMB1", "false");
        prop.put("jcifs.traceResources", "true");

        // get around https://github.com/AgNO3/jcifs-ng/issues/40
        prop.put("jcifs.smb.client.ipcSigningEnforced", "false");
        // allow plaintext password fallback
        prop.put("jcifs.smb.client.disablePlainTextPasswords", "false");
        // disable dfs makes win10 shares with ms account work
        prop.put("jcifs.smb.client.dfs.disabled", "true");

        PropertyConfiguration propertyConfiguration = null;
        try {
            propertyConfiguration = new PropertyConfiguration(prop);
        } catch (CIFSException e) {
            Log.d(TAG, "CIFSException: ", e);
        }
        return new BaseContext(propertyConfiguration);
    }

    public static CIFSContext getBaseContext(boolean isSmb2) {
        return isSmb2 ? baseContextSmb2 : baseContextSmb1;
    }
}