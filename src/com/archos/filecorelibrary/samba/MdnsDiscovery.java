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

package com.archos.filecorelibrary.samba;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Locale;

public class MdnsDiscovery implements InternalDiscovery {

    private static final Logger log = LoggerFactory.getLogger(MdnsDiscovery.class);

    private NsdManager mNsdManager;
    private MdnsListener mMdnsListener;
    private boolean mAlive = false;
    private InternalDiscoveryListener mSmbListener;
    private boolean mMdnsListenerRegistered = false;

    @SuppressWarnings("deprecation") // getHost() fallback for API < 34
    private static InetAddress getResolvedHost(NsdServiceInfo info) {
        if (Build.VERSION.SDK_INT >= 34 && !info.getHostAddresses().isEmpty()) {
            for (InetAddress address : info.getHostAddresses()) {
                if (address instanceof Inet4Address) {
                    return address;
                }
            }
            return info.getHostAddresses().get(0);
        }
        return info.getHost();
    }

    private class MdnsResolveListener implements  NsdManager.ResolveListener {
        private int mFailCount;
        private NsdServiceInfo mInfo;
        private MdnsResolveListener(NsdServiceInfo info, int failCount) {
            mFailCount = failCount;
            mInfo = info;
        }

        @SuppressWarnings("deprecation") // resolveService(NsdServiceInfo, ResolveListener) deprecated API 34; ServiceDiscoveryManager migration is a larger effort
        @Override
        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
            if (log.isDebugEnabled()) log.debug("onResolveFailed: Failed resolving {}, error code: {}", nsdServiceInfo, i);
            if(mFailCount < 10) {
                mNsdManager.resolveService(nsdServiceInfo, new MdnsResolveListener(mInfo, mFailCount + 1));
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
            if (log.isDebugEnabled()) log.debug("onServiceResolved: share found nogroup:{}:{}", nsdServiceInfo.getServiceName(), getResolvedHost(nsdServiceInfo).getHostAddress());
            try {
                InetAddress hostInetAddress = InetAddress.getByName(getResolvedHost(nsdServiceInfo).getHostAddress());
                byte[] addressInBytes = hostInetAddress.getAddress();
                //Inet6Address IPv6 = Inet6Address.getByAddress(getResolvedHost(nsdServiceInfo).getHostAddress(), addressInBytes, NetworkInterface.getByInetAddress(hostInetAddress));
                InetAddress IP = InetAddress.getByAddress(getResolvedHost(nsdServiceInfo).getHostAddress(), addressInBytes);
                if (!(IP instanceof Inet4Address)) {
                    log.error("Unsupported IPv6 for mdns SMB {}", nsdServiceInfo.getServiceName());
                    return;
                }
                Inet4Address IPv4 = (Inet4Address) Inet4Address.getByAddress(getResolvedHost(nsdServiceInfo).getHostAddress(), addressInBytes);
                if (log.isDebugEnabled()) log.debug("NsdServiceInfo: IPv4 address {}", IPv4.getHostAddress());
                //log.debug("NsdServiceInfo: IPv6 address {}", IPv6.getHostAddress());
                String uri = "smb://" + IPv4.getHostAddress() + "/";
                mSmbListener.onShareFound("nogroup", nsdServiceInfo.getServiceName().toUpperCase(Locale.ROOT), uri);
            } catch (UnknownHostException e) {
                log.error("onServiceResolved: caught UnknownHostException for {}/{}", nsdServiceInfo.getServiceName(), getResolvedHost(nsdServiceInfo).getHostAddress(), e);
            } catch (ClassCastException cce) {
                log.error("onServiceResolved: caught ClassCastException for {}/{}", nsdServiceInfo.getServiceName(), getResolvedHost(nsdServiceInfo).getHostAddress(), cce);
            }
        }
    }

    private class MdnsListener implements NsdManager.DiscoveryListener {

        @Override
        public void onStartDiscoveryFailed(String s, int i) {
            if (log.isDebugEnabled()) log.debug("onStartDiscoveryFailed: failed starting discovery...{}:{}", s, i);
        }

        @Override
        public void onStopDiscoveryFailed(String s, int i) {
            if (log.isDebugEnabled()) log.debug("onStopDiscoveryFailed: failed stopping discovery...{}:{}", s, i);
        }

        @Override
        public void onDiscoveryStarted(String s) {
            mAlive = true;
        }

        @Override
        public void onDiscoveryStopped(String s) {
            mAlive = false;
        }

        @SuppressWarnings("deprecation") // resolveService(NsdServiceInfo, ResolveListener) deprecated API 34; ServiceDiscoveryManager migration is a larger effort
        @Override
        public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
            if (log.isDebugEnabled()) log.debug("Found service {}", nsdServiceInfo);
            // MdnsResolveListener CAN NOT be reused across services
            mNsdManager.resolveService(nsdServiceInfo, new MdnsResolveListener(nsdServiceInfo, 0));
        }

        @Override
        public void onServiceLost(NsdServiceInfo nsdServiceInfo) {

        }
    }

    public MdnsDiscovery(InternalDiscoveryListener listener, Context ctxt, int socketReadDurationMs) {
        mNsdManager = (NsdManager)ctxt.getApplicationContext().getSystemService(Context.NSD_SERVICE);
        mMdnsListener = new MdnsListener();
        if (log.isDebugEnabled()) log.debug("MdnsDiscovery: created mdns discovery");
        mSmbListener = listener;
    }

    @Override
    public void start() {
        if (log.isDebugEnabled()) log.debug("start: starting discovering...");
        if (!mMdnsListenerRegistered) {
            mNsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, mMdnsListener);
            mMdnsListenerRegistered = true;
        } else {
            log.warn("start: listener already registered");
        }
    }

    @Override
    public void run_blocking() {
        log.warn("run_blocking: didn't expect this call");
    }

    @Override
    public void abort() {
        if (mMdnsListenerRegistered) {
            try {
                mNsdManager.stopServiceDiscovery(mMdnsListener);
            } catch (IllegalArgumentException e) {
                log.error("abort: caught IllegalArgumentException", e);
            } finally {
                mMdnsListenerRegistered = false;
            }
        }
    }

    @Override
    public boolean isAlive() {
        return false;
    }
}
