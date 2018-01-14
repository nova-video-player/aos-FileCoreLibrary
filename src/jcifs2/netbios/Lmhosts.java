/* jcifs smb client library in Java
 * Copyright (C) 2000  "Michael B. Allen" <jcifs at samba dot org>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package jcifs2.netbios;

import com.archos.filecorelibrary.samba.SambaDiscovery;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Hashtable;

import jcifs2.Config;
import jcifs2.util.LogStream;

public class Lmhosts {

    private static final String FILENAME = Config.getProperty( "jcifs.netbios.lmhosts" );
    private static final Hashtable TAB = new Hashtable();
    private static final String TAG = "Lmhosts";
    private static long lastModified = 1L;
    private static int alt;
    private static LogStream log = LogStream.getInstance();

    /**
     * This is really just for {@link jcifs2.UniAddress}. It does
     * not throw an {@link java.net.UnknownHostException} because this
     * is queried frequently and exceptions would be rather costly to
     * throw on a regular basis here.
     */

    public synchronized static NbtAddress getByName( String host ) {
        return getByName( new Name( host, 0x20, null ));
    }

    synchronized static NbtAddress getByName( Name name ) {
        NbtAddress result = (NbtAddress) TAB.get(name);
        if (result == null && lastModified + 4000 < System.currentTimeMillis()) { //ensure for 4 secs wait before re-scan when cache miss
            lastModified = System.currentTimeMillis();
            SambaDiscovery discovery = new SambaDiscovery();
            discovery.runUdpOnly_blocking(1500); // limit to max 1.5 seconds
            result = (NbtAddress) TAB.get(name);
        }
        return result;
    }

    public synchronized static void addHost(String host, int ip) {
        Name name = new Name( host, 0x20, null );
        NbtAddress addr = new NbtAddress( name, ip, false, NbtAddress.B_NODE,
                false, false, true, true,
                NbtAddress.UNKNOWN_MAC_ADDRESS );
        TAB.put( name, addr );
    }

    public synchronized static void reset() {
        TAB.clear();
    }
}
