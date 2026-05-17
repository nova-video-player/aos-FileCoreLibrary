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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.archos.filecorelibrary.R;

import java.util.LinkedList;

public class SambaPreferencesFragment extends PreferenceFragmentCompat implements AdapterView.OnItemLongClickListener {

    static final private String KEY_PROFILE_LIST = "profile_list";

    static private LinkedList<String> mSingleSettings;
    private PreferenceCategory mProfiles;

    private final ActivityResultLauncher<Intent> mPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == 17 /*SharedPasswordRequest.SAMBA_PASSWORD*/)
                    refreshPreferences();
            });

    //@Override
    //public void onCreate(Bundle savedInstanceState) {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.samba_settings);

        mProfiles = (PreferenceCategory) findPreference(KEY_PROFILE_LIST);
        mProfiles.setOrderingAsAdded(true);

    }

    public boolean onItemLongClick(AdapterView<?> av, View v, int position, long id) {
        final String section = mSingleSettings.get(position - 1);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
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
        for (int index = 0; index < length; index++) {
            String section = mSingleSettings.get(index);
            SambaSingleSetting sss = SambaConfiguration.getSingleSetting(section);
            Preference pref = new Preference(getContext());
            pref.setTitle(section);
            pref.setSummary(sss.getUsername());
            pref.setOrder(index);
            mProfiles.addPreference(pref);
        }
    }

    //@Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        int index = preference.getOrder();
        String section = mSingleSettings.get(index);
        SambaSingleSetting sss = SambaConfiguration.getSingleSetting(section);
        Intent i = new Intent(getActivity(), SharedPasswordRequest.class);
        i.putExtra("server",sss.getSection());
        i.putExtra("share",sss.getShare());
        i.putExtra("username",sss.getUsername());
        i.putExtra("password",sss.getPassword());
        mPasswordLauncher.launch(i);

        return true;
    }

}
