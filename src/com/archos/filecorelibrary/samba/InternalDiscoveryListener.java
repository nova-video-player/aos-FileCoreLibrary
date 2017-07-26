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

package com.archos.filecorelibrary.samba;

/**
 * SambaDiscovery is the InternalDiscoveryListener, listening to all the InternalDiscovery instances: UdpDiscovery and TcpDiscovery
 * Created by vapillon on 07/09/15.
 */
public interface InternalDiscoveryListener {

    /**
     * To be called each time a server is found
     */
    void onShareFound(String workgroupName, String shareName, String shareAddress);

    /**
     * To be called when a discovery is done (i.e. nothing "running" anymore)
     * @param discovery the discovery that ended
     * @param aborted true if the discovery has been aborted
     */
    void onInternalDiscoveryEnd(InternalDiscovery discovery, boolean aborted);
}
