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

import com.archos.filecorelibrary.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;

import java.util.LinkedList;

public class SambaPreferencesFragment extends PreferenceFragment  implements 
AdapterView.OnItemLongClickListener{

    static final private String KEY_PROFILE_LIST = "profile_list";

    static private LinkedList<String> mSingleSettings;
    private PreferenceCategory mProfiles;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.samba_settings);

        mProfiles = (PreferenceCategory) findPreference(KEY_PROFILE_LIST);
        mProfiles.setOrderingAsAdded(true);

        setHasOptionsMenu(true);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        //TODO fix me
        //getListView().setOnItemLongClickListener(this);
    }

    public boolean onItemLongClick(AdapterView<?> av, View v, int position, long id) {
        final String section = mSingleSettings.get(position - 1);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(section);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.samba_delete_settings);
        builder.setCancelable(false);
        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        SambaConfiguration.deleteSingleSetting(section);
                        refreshPreferences();
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();

        refreshPreferences();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void refreshPreferences() {
        mProfiles.removeAll();

        mSingleSettings = SambaConfiguration.getSingleSettingList();
        int length = mSingleSettings.size();
        Activity activity = getActivity();
        for (int index = 0; index < length; index++) {
            String section = mSingleSettings.get(index);
            SambaSingleSetting sss = SambaConfiguration.getSingleSetting(section);
            Preference pref = new Preference(activity);
            pref.setTitle(section);
            pref.setSummary(sss.getUsername());
            pref.setOrder(index);
            mProfiles.addPreference(pref);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        int index = preference.getOrder();
        String section = mSingleSettings.get(index);
        SambaSingleSetting sss = SambaConfiguration.getSingleSetting(section);
        Intent i = new Intent(getActivity(), SharedPasswordRequest.class);
        i.putExtra("server",sss.getSection());
        i.putExtra("share",sss.getShare());
        i.putExtra("username",sss.getUsername());
        i.putExtra("password",sss.getPassword());
        startActivityForResult(i, 0);

        return true;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if (resultCode == 17/*SharedPasswordRequest.SAMBA_PASSWORD*/){
            refreshPreferences();
        }
    }

//    @Override
//    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
//        super.onCreateOptionsMenu(menu, inflater);
//        MenuItem item = menu.add(0, 0, Menu.NONE, R.string.samba_add_server);
//        item.setIcon(android.R.drawable.ic_menu_add);
//        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS|MenuItem.SHOW_AS_ACTION_WITH_TEXT);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//        final View textEntryView = inflater.inflate(R.layout.shared_folder_path, null);
//
//        AlertDialog dialog = new AlertDialog.Builder(getActivity())
//                .setIcon(android.R.drawable.ic_dialog_alert)
//                .setTitle(R.string.samba_settings_title)
//                .setView(textEntryView)
//                .setPositiveButton(android.R.string.ok,
//                        new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int whichButton) {
//                                EditText serverET = (EditText) textEntryView
//                                        .findViewById(R.id.server_edit);
//                                String server = serverET.getText().toString().trim();
//                                EditText shareET = (EditText) textEntryView
//                    .findViewById(R.id.share_edit);
//                String share = shareET.getText().toString().trim();
//                Intent i = new Intent(getActivity(), SharedPasswordRequest.class);
//                i.putExtra("server",server);
//                i.putExtra("share",share);
//                startActivityForResult(i, 0);
//
//                            }
//                        }).setNegativeButton(android.R.string.cancel, null).create();
//        dialog.show();
//        return true;
//    }
}
