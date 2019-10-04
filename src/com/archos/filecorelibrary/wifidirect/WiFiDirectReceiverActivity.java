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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

import java.util.List;

public class WiFiDirectReceiverActivity extends Activity{
    private static String path;
    Intent serviceIntent;
    IFileTransferService mService;
    private TextView statusText;
    private static ProgressDialog progressDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifi_direct_receiver);

        statusText = (TextView) findViewById(R.id.status_text);
        serviceIntent = new Intent(this, FileTransferService.class);
        if (getIntent().hasExtra("path")){
            path = getIntent().getStringExtra("path");
        } else
            path = Environment.getExternalStorageDirectory().getPath().concat("/").concat(Environment.DIRECTORY_DOWNLOADS);
        serviceIntent.putExtra("client", false);
        serviceIntent.putExtra("path", path);
        startService(serviceIntent);
        progressDialog = null;
    }

    /* register the broadcast receiver with the intent values to be matched */
    @Override
    protected void onStart() {
        super.onStart();
        bindService(serviceIntent,
                mConnection, Context.BIND_AUTO_CREATE);
        if (!Utils.isWifiAvailable(this))
            noNetworkDialog();
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onStop() {
        super.onStop();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        unbindService(mConnection);
        if (mService != null) {
            try {
                mService.unregisterCallback(mCallback);
            } catch (RemoteException e) {}
        }
    }

    //If the activity is finished while uploading, it's an abort on back button press.
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

    public void updateThisDevice(Device device) {
        TextView view = (TextView) findViewById(R.id.my_name);
        view.setText(device.getDeviceName());
        view = (TextView) findViewById(R.id.my_status);
        view.setText(Utils.getDeviceStatus(device.getDeviceStatus()));
    }

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
                        progressDialog.setTitle(R.string.download_in_progress);
                        progressDialog.setMessage(getText(R.string.file_downloading));
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

    public void initProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        final Context context = this;
        this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog = new ProgressDialog(context);
                    progressDialog.setTitle(R.string.wifip2p_waiting);
                    progressDialog.setMessage(getText(R.string.server_wait));
                    progressDialog.show();
                }
        });
    }

    private IFileTransferServiceCallback mCallback = new IFileTransferServiceCallback.Stub() {

        //Peer list updated
        public void updatePeersList(List<Device> peers) {
           //We do nothing on server side for this
        }

        //Connection Done, ready to receive file
        public void connectionDone(){
            statusText.setText(R.string.download_in_progress);
            initProgressDialog();
            if (mService != null){
                try {
                    mService.rReceiveFile();
                } catch (RemoteException re){
                    Log.w(FileTransferService.TAG, "fail to begin receive", re);
                }
            }
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
}
