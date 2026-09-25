// Copyright 2026 Courville Software
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

package com.archos.filecorelibrary;

import android.net.Uri;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Endpoint monitors: never hold a pool-wide monitor while performing network I/O.
 * Entries live as long as their pool, so retirement cannot create a second monitor
 * while another caller is still waiting on the first one.
 */
public final class ConnectionLocks {
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    public Object forUri(Uri uri) {
        String host = uri.getHost();
        if (host == null) throw new IllegalArgumentException("Missing SSH host");
        return locks.computeIfAbsent(host.toLowerCase(Locale.ROOT) + ":"
                + (uri.getPort() < 0 ? 22 : uri.getPort()), key -> new Object());
    }
}
