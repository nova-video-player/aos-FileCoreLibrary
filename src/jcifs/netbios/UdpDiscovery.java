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

package jcifs.netbios;

import android.os.SystemClock;
import android.util.Log;

import com.archos.filecorelibrary.jcifs.JcifsUtils;
import com.archos.filecorelibrary.samba.InternalDiscovery;
import com.archos.filecorelibrary.samba.InternalDiscoveryListener;
import com.archos.filecorelibrary.samba.SambaDiscovery;
import com.archos.filecorelibrary.samba.Workgroup;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;

import jcifs.CIFSContext;
import jcifs.Configuration;

import static jcifs.netbios.NbtAddress.UNKNOWN_MAC_ADDRESS;

public class UdpDiscovery implements InternalDiscovery {
    private static final String TAG = "UdpDiscovery";
    private static final boolean DBG = false;

    private static final int SMB_NS_PORT = 137;

    private final Thread mThread;
    private final InternalDiscoveryListener mListener;
    private final String mIpAddress;
    private final int mSocketReadDurationMs;

    private boolean mAbort = false;

    public UdpDiscovery(InternalDiscoveryListener listener, String ipAddress, int socketReadDurationMs) {
        mThread = new UdpDiscoveryThread();
        mListener = listener;
        mIpAddress = ipAddress;
        mSocketReadDurationMs = socketReadDurationMs;
    }

    @Override
    public void start() {
        mThread.start(); // start the Thread
    }

    @Override
    public void run_blocking() {
        mThread.run();
    }

    @Override
    public void abort() {
        mAbort = true;
    }

    @Override
    public boolean isAlive() {
        return mThread.isAlive();
    }

    private class UdpDiscoveryThread extends Thread {
        public void run() {

            NbtAddress[] addrs;
            LinkedList<InetAddress> addresses = new LinkedList<>();
            LinkedList<DatagramChannel> datagrams = new LinkedList<>();

            Selector selector = null;
            try {
                selector = Selector.open();
            } catch (IOException e) {
                if(DBG) Log.d(TAG, "abort UdpDiscovery: no selector");
                return;
            }
            byte[] snd_buf = new byte[576];
            ByteBuffer rcv_buf = ByteBuffer.allocate(576);
            final String netRange = mIpAddress.substring(0, mIpAddress.lastIndexOf(".") + 1);
            // Send node status request to each IP
            CIFSContext cifsContext = JcifsUtils.getBaseContext(true);
            Configuration configuration = cifsContext.getConfig();
            final NodeStatusRequest request = new NodeStatusRequest(configuration,
                    new Name(configuration,
                            NbtAddress.ANY_HOSTS_NAME, 0x00, null));
            request.nameTrnId = 1; //fixed to 1, no retry.

            int nbSent = 0;
            int nbToSend = 254;
            int nbSocket = nbToSend / 32;
            for (int i = 0; i < nbToSend + 1; i++) {
                final String ip = netRange.concat(String.valueOf(i + 1));
                final InetAddress inetAddress;
                try {
                    inetAddress = InetAddress.getByName(ip);
                    addresses.add(inetAddress);
                } catch (UnknownHostException e) {
                    continue;
                }
            }

            for (int i = 0; i < nbSocket; i++) {
                DatagramChannel datagramChannel = null;
                try {
                    datagramChannel = DatagramChannel.open();
                } catch (IOException ignored) {
                }
                if (datagramChannel == null) continue;
                datagrams.add(datagramChannel);

                try {
                    datagramChannel.configureBlocking(false);
                    datagramChannel.socket().setSoTimeout(SambaDiscovery.SOCKET_TIMEOUT);
                    datagramChannel.socket().bind(null);
                    datagramChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                } catch (IOException e) {
                    if (DBG) Log.e(TAG, "doTcpDiscovery: caught IOException", e);
                    else Log.w(TAG, "doTcpDiscovery: caught IOException");
                }
            }

            final long readStartTime = SystemClock.elapsedRealtime();
            while (!mAbort && (SystemClock.elapsedRealtime() - readStartTime) < mSocketReadDurationMs) {
                int readyChannels = 0;
                if (selector != null) {
                    try {
                        readyChannels = selector.select(SambaDiscovery.SOCKET_TIMEOUT);
                    } catch (IOException e) {
                        if (DBG) Log.e(TAG, "doTcpDiscovery: caught IOException", e);
                        else Log.w(TAG, "doTcpDiscovery: caught IOException");
                    }
                }
                if (readyChannels == 0) continue;
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    if (key.isValid() && key.isWritable()) {
                        if (nbSent >= nbToSend)
                            key.interestOps(SelectionKey.OP_READ);
                        else {
                            DatagramChannel currentChannel = (DatagramChannel) key.channel();
                            try {
                                request.addr = addresses.get(nbSent);
                                int size = request.writeWireFormat(snd_buf, 0);
                                if (currentChannel.send(ByteBuffer.wrap(snd_buf, 0, size), new InetSocketAddress(request.addr, SMB_NS_PORT)) != 0) {
                                    nbSent++;
                                    //Log.d(TAG, "sent " + nbSent + " with "+ currentChannel);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (key.isValid() && key.isReadable()) {
                        DatagramChannel currentChannel = (DatagramChannel) key.channel();
                        rcv_buf.rewind();
                        InetSocketAddress remoteAddress = null;
                        try {
                            remoteAddress = (InetSocketAddress) currentChannel.receive(rcv_buf);
                        } catch (IOException ignored) {
                        }
                        if (remoteAddress != null) {
                            NodeStatusResponse response = null;
                            try {
                                response = new NodeStatusResponse(configuration,
                                        cifsContext.getNameServiceClient().getByName(
                                                remoteAddress.getAddress().getHostAddress()).unwrap(NbtAddress.class));
                            } catch (UnknownHostException e) {
                                continue;
                            }
                            response.readWireFormat(rcv_buf.array(), 0);
                            addrs = response.addressArray;
                            readResponse(addrs, remoteAddress.getAddress());
                        }
                    }
                }
            }

            for (DatagramChannel datagramChannel : datagrams) {
                try {
                    datagramChannel.close();
                } catch (IOException ignored) {
                }
            }
            try {
                if (selector != null)
                    selector.close();
            } catch (IOException ignored) {
            }

            mListener.onInternalDiscoveryEnd(UdpDiscovery.this, mAbort);
        }
    }


    private void readResponse(NbtAddress[] addrs, InetAddress remoteAddr) {

        String workgroupName = Workgroup.NOGROUP;

        if (addrs != null) {
            // First loop to find workgroups
            for (NbtAddress addr : addrs) {
                try {
                    if (addr.isGroupAddress(JcifsUtils.getBaseContext(true)) && !addr.getHostName().equalsIgnoreCase(NbtAddress.MASTER_BROWSER_NAME)) {
                        workgroupName = addr.getHostName();
                        break;
                    }
                } catch (UnknownHostException ignored) {}
            }

            // Check server name and add to workgroup
            for (NbtAddress addr : addrs) {
                if (mAbort) {
                    break;
                }
                try {
                    if (!addr.isGroupAddress(JcifsUtils.getBaseContext(true))) {
                        final String shareName = addr.getHostName();
                        final String shareAddress = "smb://" + remoteAddr.getHostAddress() + '/';

                        if(DBG) Log.d(TAG, "found share " + shareName + " at " + shareAddress);
                        mListener.onShareFound(workgroupName, shareName, shareAddress);

                        // Update the JCIFS cache
                        addShareHostToCache(shareName, remoteAddr);

                        break;
                    }
                } catch (UnknownHostException ignored) {
                }
            }
        }
    }

    void addShareHostToCache(String shareName, InetAddress ip) {
        int ipv4Address = 0;
        byte[] ipAddress = ip.getAddress();
        if (ipAddress.length == 4) {
            for (int i = 0; i < 4; i++) {
                ipv4Address = (ipv4Address << 8) + (ipAddress[i] & 0xFF);
            }
            CIFSContext context = JcifsUtils.getBaseContext(true);
            Name name = new Name(context.getConfig(), shareName, 0x20, null );
            NbtAddress addr = new NbtAddress(name, ipv4Address, false, NbtAddress.B_NODE,
                    false, false, true, true,
                    UNKNOWN_MAC_ADDRESS );
            //we need to fake the broadcast hashcode for hitting cache
            addr.hostName.srcHashCode = context.getConfig().getBroadcastAddress().hashCode();
            NameServiceClientImpl impl = (NameServiceClientImpl) context.getNameServiceClient();
            impl.cacheAddress(name, addr);
            Log.d(TAG, "Check cache after insert " + impl.getCachedAddress(name).getHostName() + " at " + impl.getCachedAddress(name).getHostAddress());
        }
    }
}