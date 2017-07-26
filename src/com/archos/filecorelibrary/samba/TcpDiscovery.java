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

import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

public class TcpDiscovery implements InternalDiscovery {
    private static final String TAG = "TcpDiscovery";
    private static final boolean DBG = false;

    private final Thread mThread;
    private final InternalDiscoveryListener mListener;
    private final String mIpAddress;
    private final int mSocketReadDurationMs;
    private final int mStartDelayMs;

    private boolean mAbort = false;

    /**
     *
     * @param ipAddress
     * @param socketReadDurationMs
     * @param startDelayMs the delay before the discovery actually starts after start() is called
     */
    public TcpDiscovery(InternalDiscoveryListener listener, String ipAddress, int socketReadDurationMs, int startDelayMs) {
        mThread = new TcpDiscoveryThread();
        mListener = listener;
        mIpAddress = ipAddress;
        mSocketReadDurationMs = socketReadDurationMs;
        mStartDelayMs = startDelayMs;
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

    private class TcpDiscoveryThread extends Thread {
        @Override
        public void run() {
            try {
                sleep(mStartDelayMs);
            } catch (InterruptedException ignored) {}

            // We can check if there is an abort after the sleep
            if (mAbort) {
                return;
            }

            final String netRange = mIpAddress.substring(0, mIpAddress.lastIndexOf(".") + 1);
            doTcpDiscovery(netRange);

            mListener.onInternalDiscoveryEnd(TcpDiscovery.this, mAbort);
        }
    }

    private void doTcpDiscovery(String netRange) {
        LinkedList<SocketChannel> sockets = new LinkedList<>();
        Selector selector = null;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            Log.e(TAG, "abort TcpDiscovery: no selector");
            return;
        }

        for (int i = 1; i < 255; i++) {
            final String ip = netRange.concat(String.valueOf(i));
            //try to connect to tcp port 445
            SocketChannel socketChannel = null;
            try {
                socketChannel = SocketChannel.open();
            } catch (IOException ignored) {}
            if (socketChannel == null) continue;
            sockets.add(socketChannel);

            try {
                socketChannel.configureBlocking(false);
                socketChannel.register(selector, SelectionKey.OP_CONNECT);
                socketChannel.connect(new InetSocketAddress(ip, 445));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        final long readStartTime = SystemClock.elapsedRealtime();
        while (!mAbort && (SystemClock.elapsedRealtime() - readStartTime) < mSocketReadDurationMs) {
            int readyChannels = 0;
            if (selector != null) {
                try {
                    readyChannels = selector.select(SambaDiscovery.SOCKET_TIMEOUT);
                } catch (IOException ignored) {}
            }
            if (readyChannels == 0) continue;
            Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                keyIterator.remove();
                if (key.isValid() && key.isConnectable()) {
                    SocketChannel currentChannel = (SocketChannel) key.channel();

                    boolean v = false;
                    try {
                        v = currentChannel.finishConnect();
                    }
                    catch (IOException ignored) {}
                    finally {
                        try {
                            currentChannel.close();
                        } catch (IOException ignored) {}
                    }

                    if (v) {
                        int ipNumber = sockets.indexOf(currentChannel) + 1;
                        final String ip = netRange.concat(String.valueOf(ipNumber));
                        final String shareAddress = "smb://" + ip + '/';
                        if(DBG) Log.d(TAG, "found share at " + ip);
                        mListener.onShareFound(Workgroup.NOGROUP, "", shareAddress); // TCP discovery does not give the share name
                    }
                }
            }
        }

        for(SocketChannel socketChannel: sockets) {
            try {
                socketChannel.close();
            } catch (IOException ignored) {}
        }
        try {
            if (selector != null)
                selector.close();
        } catch (IOException ignored) {}
    }
}