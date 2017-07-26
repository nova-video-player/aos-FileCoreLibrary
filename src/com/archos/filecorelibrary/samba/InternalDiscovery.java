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
 * The public SambaDiscovery is using several internal discoveries: UdpDiscovery and TcpDiscovery
 * Created by vapillon on 07/09/15.
 */
public interface InternalDiscovery {

    /**
     * Start the discovery (non blocking)
     */
    void start();

    /**
     * do the discovery (blocking, returns when the discovery is over)
     */
    void run_blocking();

    /**
     * Stop the discovery as soon as possible (non blocking)
     */
    void abort();

    /**
     * Returns <code>true</code> if the receiver has already been started and
     * still runs code (hasn't died yet). Returns <code>false</code> either if
     * the receiver hasn't been started yet or if it has already started and run
     * to completion and died.
     *
     * @return a <code>boolean</code> indicating the liveness of the Thread
     * @see Thread#start
     */
    boolean isAlive();
}
