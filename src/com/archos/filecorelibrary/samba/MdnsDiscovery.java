package com.archos.filecorelibrary.samba;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

public class MdnsDiscovery implements InternalDiscovery {
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
            Log.e("PHH", "Failed resolving " + nsdServiceInfo);
            if(mFailCount < 10) {
                mNsdManager.resolveService(nsdServiceInfo, new MdnsResolveListener(mInfo, mFailCount + 1));
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
            Log.d("PHH", "Share found nogroup:" + nsdServiceInfo.getServiceName() + ":" + nsdServiceInfo.getHost().getHostAddress());
            String uri = "smb://" + nsdServiceInfo.getHost().getHostAddress() + "/";
            mSmbListener.onShareFound("nogroup", nsdServiceInfo.getServiceName(), uri);
        }
    }

    private class MdnsListener implements NsdManager.DiscoveryListener {

        @Override
        public void onStartDiscoveryFailed(String s, int i) {
            Log.d("PHH", "Failed starting discovery..." + s + ":" + i);
        }

        @Override
        public void onStopDiscoveryFailed(String s, int i) {
            Log.d("PHH", "Failed stopping discovery..." + s + ":" + i);
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
            Log.d("PHH", "Found service " + nsdServiceInfo);
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
        Log.d("PHH","Created mdns discovery");
        mSmbListener = listener;
    }

    @Override
    public void start() {
        Log.d("PHH","Starting discovering...");
        mNsdManager.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, mMdnsListener);
    }

    @Override
    public void run_blocking() {
        Log.wtf("PHH", "Didn't expect this call");
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
