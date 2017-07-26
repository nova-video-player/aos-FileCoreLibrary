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

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class Share implements Parcelable {
    final String mName;
    final String mAddress;
    final String mWorkgroup;

    public Share(String name, String address, String workgroup) {
        mName = name;
        mAddress = address;
        mWorkgroup = workgroup;
    }

    /**
     * Copy constructor
     * @param s
     */
    public Share(Share s) {
        mName = s.mName;
        mAddress = s.mAddress;
        mWorkgroup = s.mWorkgroup;
    }

    public Share(Parcel in) {
        mName = in.readString();
        mAddress = in.readString();
        mWorkgroup = in.readString();
    }

    public String getName() {
        return mName;
    }

    public String getDisplayName() {
        String name = mName;
        if (name == null || name.isEmpty()) {
            name = mAddress;
            // Hack to get a simple clean IP to display while we get something like "smb://toto/" :-(
            final String prefix = "smb://";
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
            }
            final String suffix = "/";
            if (name.endsWith(suffix)) {
                name = name.substring(0, name.length()-suffix.length());
            }
        }
        return name;
    }

    /**
     * @return an address string looking like "smb://192.168.42.12/"
     */
    public String getAddress() {
        return mAddress;
    }

    public String getWorkgroup() {
        return mWorkgroup;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mName);
        //sb.append("|");
        //sb.append(mAddress);
        //sb.append("|");
        //sb.append(mWorkgroup);
        return sb.toString();
    }

    /**
     * @return an Uri built with the Host name (if it is not null) or with the IP if the Host name is null or empty
     */
    public Uri toUri() {
        if (mName==null || mName.isEmpty()) {
            return Uri.parse(mAddress);
        } else {
            return Uri.parse("smb://"+mName+"/");
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mAddress);
        dest.writeString(mWorkgroup);
    }

    public static final Parcelable.Creator<Share> CREATOR
    = new Parcelable.Creator<Share>() {
        public Share createFromParcel(Parcel in) {
            return new Share(in);
        }

        public Share[] newArray(int size) {
            return new Share[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (o instanceof Share) {
            Share other = (Share)o;
            // All fields must be equal
            return mName.equals(other.mName) && mAddress.equals(other.mAddress) && mWorkgroup.equals(other.mWorkgroup);
        }
        return false;
    }
}