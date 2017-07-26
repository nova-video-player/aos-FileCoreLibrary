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

package com.archos.filecorelibrary.wifidirect;

import com.archos.filecorelibrary.R;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Html;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class WiFiDirectSenderActivity extends ListActivity {
    WiFiPeerListAdapter mWiFiPeerListAdapter;
    private List<Device> peers = new ArrayList<Device>();
    private Device device;
    private static ProgressDialog progressDialog;
    String filePath;
    Intent serviceIntent;
    IFileTransferService mService;

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IFileTransferService.Stub.asInterface(service);
            try {
                mService.registerCallback(mCallback);
            } catch (RemoteException e) {}
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.wifi_direct_sender);
        mWiFiPeerListAdapter = new WiFiPeerListAdapter(this, R.layout.row_devices, peers);
        this.setListAdapter(mWiFiPeerListAdapter);
        serviceIntent = new Intent(this, FileTransferService.class);
        serviceIntent.putExtra("client", true);
        if (getIntent().hasExtra(Intent.EXTRA_STREAM)){
            filePath = Utils.getPathFromUri(getIntent(), this);
            serviceIntent.putExtra("path", filePath);
            startService(serviceIntent);
        }
        TextView tv = (TextView) (findViewById(android.R.id.empty));
        String text = String.format(getString(R.string.empty_message), getString(R.string.receive_p2p_here));
        tv.setText(Html.fromHtml(text));
        progressDialog = null;
    }

    /* register the broadcast receiver with the intent values to be matched */
    @Override
    protected void onStart() {
        super.onStart();
        bindService(serviceIntent,
                mConnection, Context.BIND_IMPORTANT);
        if (!Utils.isWifiAvailable(this))
            noNetworkDialog();
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onStop() {
        super.onStop();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
        if (mService != null) {
            try {
                mService.unregisterCallback(mCallback);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service
                // has crashed.
            }
        }
        unbindService(mConnection);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK){
            if (mService != null) {
                try {
                    mService.stop(true);
                } catch (RemoteException e) {}
            }
        }
        finish();
        return super.onKeyDown(keyCode, event);
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
//        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    private void connect(String deviceAddress){
        if (mService != null){
            try {
                mService.rConnect(deviceAddress);
            } catch (RemoteException re){}
        }
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(this, getText(R.string.back_cancel),
                "Connecting to :" + deviceAddress, true, true);
    }

    private void updateDialog(final int progress){
        if (progressDialog != null && progressDialog.isIndeterminate()) {
            if (progressDialog.isShowing())
                progressDialog.dismiss();
            progressDialog = null;
        }
        final Context context = this;
        this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (progressDialog == null){
                        progressDialog = new ProgressDialog(context);
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        progressDialog.setTitle(R.string.upload_in_progress);
                        progressDialog.setMessage(getText(R.string.file_uploading));
                        progressDialog.setIndeterminate(false);
                        progressDialog.setMax(100);
                        progressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {

                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                finish();
                            }
                        });
                        progressDialog.show();
                    }
                    progressDialog.setProgress(progress);
                }
            });
    }

    private void noNetworkDialog(){
        Builder dialogNoNetwork;

        dialogNoNetwork = new AlertDialog.Builder(this);
        dialogNoNetwork.setCancelable(true);
        dialogNoNetwork.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        dialogNoNetwork.setTitle(R.string.nonetwork_title);
        dialogNoNetwork.setMessage(getString(R.string.nonetwork_message));
        dialogNoNetwork.show();
    }

    public void initProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        final Context context = this;
        this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog = new ProgressDialog(context);
                    progressDialog.setTitle(R.string.connect_to_target);
                    progressDialog.setMessage(getText(R.string.wifip2p_waiting));
                    progressDialog.show();
                }
        });
    }

    public void connectionEstablished() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
        sendFile();
    }

    private void sendFile() {
        if (mService != null) {
            try {
                mService.rSendFile();
            } catch (RemoteException e) {}
        }
    }

    //PeerListListener
    public void onPeersAvailable(List<Device> peers) {
        setProgressBarIndeterminateVisibility(false);
        this.peers.clear();
        this.peers.addAll(peers);
        mWiFiPeerListAdapter.notifyDataSetChanged();
    }

    private void updateThisDevice(Device device) {
        this.device = device;
        TextView view = (TextView) findViewById(R.id.my_name);
        view.setText(device.getDeviceName());
        view = (TextView) findViewById(R.id.my_status);
        view.setText(Utils.getDeviceStatus(device.getDeviceStatus()));
    }

    private IFileTransferServiceCallback mCallback = new IFileTransferServiceCallback.Stub() {

        //Peer list updated
        public void updatePeersList(List<Device> peers) {
            onPeersAvailable(peers);
        }

        //Connection Done
        public void connectionDone(){
            connectionEstablished();
            ((View)findViewById(R.id.devices_list)).setVisibility(View.GONE);
            ((View)findViewById(R.id.device_detail)).setVisibility(View.VISIBLE);
            initProgressDialog();
        }

        public void operationDone(){
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
                progressDialog = null;
            }
            finish();
        }

        public void deviceUpdate(Device device){
            updateThisDevice(device);
        }

        public void transferInProgress(int progress){
            updateDialog(progress);
        }
    };

        /****************************************************************
         *                       List management                        *
         ****************************************************************/
    /**
     * Initiate a connection with the peer.
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        device = (Device) getListAdapter().getItem(position);
        connect(device.getDeviceAddress());
    }

    /**
     * Array adapter for ListFragment that maintains WifiP2pDevice list.
     */
    private class WiFiPeerListAdapter extends ArrayAdapter<Device> {

        private List<Device> items;

        /**
         * @param context
         * @param textViewResourceId
         * @param objects
         */
        public WiFiPeerListAdapter(Context context, int textViewResourceId,
                List<Device> objects) {
            super(context, textViewResourceId, objects);
            items = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.row_devices, null);
            }
            Device device = items.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.getDeviceName());
                }
                if (bottom != null) {
                    bottom.setText(Utils.getDeviceStatus(device.getDeviceStatus()));
                }
            }
            return v;
        }
    }
}
