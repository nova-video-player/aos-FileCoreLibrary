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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.archos.filecorelibrary.R;

public class SharedPasswordRequest extends Activity implements OnClickListener {

    static final public int SAMBA_PASSWORD          = 17;
    static final public int SAMBA_PASSWORD_CANCELED = 18;

    private String server, share;
    private EditText passwordET;
    private SambaSingleSetting sss;

    public SharedPasswordRequest() {
        super();
    }

    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        Bundle extras = i.getExtras();
        if ( extras.getString("server")!= null){
            if (extras.getString("username")!= null && extras.getString("password")!= null){
                passwordRequested(extras.getString("server"), extras.getString("share"),extras.getString("username"), extras.getString("password"));
            } else {
                passwordRequested(extras.getString("server"), extras.getString("share"));
            }
        } else if (extras.getString("path")!= null){
            passwordRequested(extras.getString("path"));
        }
    }

    public void passwordRequested(String path) {
        // path is something like smb://server/share
        // I just want /server/share which can be just /server and i don't know
        // the workgroup name.
        int index = 5;
        String serverAndShare = path.substring(index);
        index = serverAndShare.indexOf('/', 1);
        if (index == -1) {
            server = serverAndShare;
        } else {
            server = serverAndShare.substring(0, index);
            share = serverAndShare.substring(index);
        }
        this.sss = null;
        showDialog();
    }

    public void passwordRequested(String server, String share) {
        this.server = server;
        this.share = share;
        this.sss = null;
        showDialog();
    }
    public void passwordRequested(String server, String share, String username , String password) {
        this.server = server;
        this.share = share;
        this.sss = new SambaSingleSetting(server, share);
        sss.setUsername(username);
        sss.setPassword(password);
        showDialog();
    }

    public void passwordRequested(SambaSingleSetting sss) {
        this.sss = sss;
        server = sss.getServer();
        share = sss.getShare();
        showDialog();
    }

    private void showDialog() {
        final View view = LayoutInflater.from(this).inflate(R.layout.samba_password_request, null);
        final EditText usernameET = (EditText) view.findViewById(R.id.username_edit);
        usernameET.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        passwordET = (EditText) view.findViewById(R.id.password_edit);
        passwordET.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
                Log.d("XXX", "onEditorAction " + actionId);
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    dialogGo(usernameET, passwordET);
                    return true;
                }
                return false;
            }
        });

        if (sss != null) {
            usernameET.setText(Uri.decode(sss.getUsername()));
            passwordET.setText(Uri.decode(sss.getPassword()));
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.samba_password_request_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Log.d("XXX", "setPositiveButton onClick");
                        dialogGo(usernameET, passwordET);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Log.d("XXX", "setNegativeButton onClick");
                        sendNotification(SAMBA_PASSWORD_CANCELED);
                    }
                })
                .create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                Log.d("XXX", "OnCancelListener ");
                sendNotification(SAMBA_PASSWORD_CANCELED);
            }
        });
        dialog.setCancelable(false); // do not cancel with simple touch outside or back
        dialog.show();
    }

    private void dialogGo(EditText usernameET, EditText passwordET) {
        String username = Uri.encode(usernameET.getText().toString().trim()).replace("%2F", "/");
        String password = Uri.encode(passwordET.getText().toString().trim());
        if (sss == null) {
            sss = new SambaSingleSetting(server, share);
            sss.setUsername(server, username);
        } else {
            sss.setUsername(username);
        }
        sss.setPassword(password);
        SambaConfiguration.setSingleSetting(sss);
        sendNotification(SAMBA_PASSWORD);
    }

    private void sendNotification(int MessageId) {
        setResult(MessageId);
        finish();
    }

    @Override
    public void onClick(View v) {
        // TODO Auto-generated method stub
        
    }
}
