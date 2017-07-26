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

import java.util.LinkedList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

public class Workgroup implements Parcelable {
    static public final String NOGROUP = "nogroup";

    private final String mName;

    private final List<Share> mShares = new LinkedList<Share>();

    public Workgroup(String name) {
        mName = name;
    }

    /**
     * Copy constructor
     * @param w
     */
    public Workgroup(Workgroup w) {
        mName = w.mName;
        for (Share s : w.mShares) {
            mShares.add(new Share(s));
        }
    }

    public Workgroup(Parcel in) {
        mName = in.readString();
        int size = in.readInt();
        Share[] shares =new Share[size];
        in.readTypedArray(shares,Share.CREATOR);
        for (Share s : shares) {
            mShares.add(s);
        }
    }

    public boolean isEmpty() {
        return mShares.isEmpty();
    }

    public void addShare(String shareName, String shareAddress) {
        mShares.add(new Share(shareName, shareAddress, mName));
    }

    public String getName() {
        return mName;
    }

    public List<Share> getShares() {
        return mShares;
    }

    public int getNumberOfShares() {
        return mShares.size();
    }

    public String getShareNamesSepratedBy(String separator) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Share share : mShares) {
            sb.append(sep);
            sb.append(share.getName());
            sep = separator;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mName);
        sb.append(" [ ");
        String separator = "";
        for (Share share : mShares) {
            sb.append(separator);
            sb.append(share.toString());
            separator = " ; ";
        }
        sb.append(" ]");
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeInt(mShares.size());
        Share[] shares = new Share[mShares.size()];
        mShares.toArray(shares);
        dest.writeTypedArray(shares,flags);
    }

    public static final Parcelable.Creator<Workgroup> CREATOR
    = new Parcelable.Creator<Workgroup>() {
        public Workgroup createFromParcel(Parcel in) {
            return new Workgroup(in);
        }

        public Workgroup[] newArray(int size) {
            return new Workgroup[size];
        }
    };
}