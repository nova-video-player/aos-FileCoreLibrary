package com.archos.filecorelibrary.samba;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

public class MdnsDiscovery implements InternalDiscovery {

    private static final Logger log = LoggerFactory.getLogger(MdnsDiscovery.class);

    private NsdManager mNsdManager;
    private MdnsListener mMdnsListener;
    private boolean mAlive = false;
    private InternalDiscoveryListener mSmbListener;

    private class MdnsResolveListener implements  NsdManager.ResolveListener {
        private int mFailCount;
        private NsdServiceInfo mInfo;
        private MdnsResolveListener(NsdServiceInfo info, int failCount) {
            mFailCount = failCount;
            mInfo = info;
        }

        @Override
        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
            log.error("onResolveFailed: Failed resolving " + nsdServiceInfo);
            if(mFailCount < 10) {
                mNsdManager.resolveService(nsdServiceInfo, new MdnsResolveListener(mInfo, mFailCount + 1));
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
            log.debug("onServiceResolved: share found nogroup:" + nsdServiceInfo.getServiceName() + ":" + nsdServiceInfo.getHost().getHostAddress());
            try {
                InetAddress hostInetAddress = InetAddress.getByName(nsdServiceInfo.getHost().getHostAddress());
                byte[] addressInBytes = hostInetAddress.getAddress();
                //Inet6Address IPv6 = Inet6Address.getByAddress(nsdServiceInfo.getHost().getHostAddress(), addressInBytes, NetworkInterface.getByInetAddress(hostInetAddress));
                Inet4Address IPv4 = (Inet4Address) Inet4Address.getByAddress(nsdServiceInfo.getHost().getHostAddress(), addressInBytes);
                log.debug("NsdServiceInfo: IPv4 address " + IPv4.getHostAddress());
                //log.debug("NsdServiceInfo: IPv6 address " + IPv6.getHostAddress());
                String uri = "smb://" + IPv4.getHostAddress() + "/";
                mSmbListener.onShareFound("nogroup", nsdServiceInfo.getServiceName().toUpperCase(), uri);
            } catch (UnknownHostException e) {
                log.error("onServiceResolved: caught UnknownHostException for " + nsdServiceInfo.getServiceName() + "/" + nsdServiceInfo.getHost().getHostAddress(), e);
            } catch (ClassCastException cce) {
                log.error("onServiceResolved: caught ClassCastException for " + nsdServiceInfo.getServiceName() + "/" + nsdServiceInfo.getHost().getHostAddress() , cce);
            }
        }
    }

    private class MdnsListener implements NsdManager.DiscoveryListener {

        @Override
        public void onStartDiscoveryFailed(String s, int i) {
            log.debug("onStartDiscoveryFailed: failed starting discovery..." + s + ":" + i);
        }

        @Override
        public void onStopDiscoveryFailed(String s, int i) {
            log.debug("onStopDiscoveryFailed: failed stopping discovery..." + s + ":" + i);
        }

        @Override
        public void onDiscoveryStarted(String s) {
            mAlive = true;
        }

        @Override
        public void onDiscoveryStopped(String s) {
            mAlive = false;
        }

        @Override
        public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
            log.debug("Found service " + nsdServiceInfo);
            // MdnsResolveListener CAN NOT be reused across services
            mNsdManager.resolveService(nsdServiceInfo, new MdnsResolveListener(nsdServiceInfo, 0));
        }

        @Override
        public void onServiceLost(NsdServiceInfo nsdServiceInfo) {

        }
    }

    public MdnsDiscovery(InternalDiscoveryListener listener, Context ctxt, int socketReadDurationMs) {
        mNsdManager = (NsdManager)ctxt.getSystemService(Context.NSD_SERVICE);
        mMdnsListener = new MdnsListener();
        log.debug("MdnsDiscovery: created mdns discovery");
        mSmbListener = listener;
    }

    @Override
    public void start() {
        log.debug("start: starting discovering...");
        mNsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, mMdnsListener);
    }

    @Override
    public void run_blocking() {
        log.warn("run_blocking: didn't expect this call");
    }

    @Override
    public void abort() {
        mNsdManager.stopServiceDiscovery(mMdnsListener);
    }

    @Override
    public boolean isAlive() {
        return false;
    }
}
