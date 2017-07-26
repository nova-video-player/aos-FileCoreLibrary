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

import android.os.Parcel;

public class Device implements android.os.Parcelable {

    private String deviceName;
    private String deviceAddress;
    private int deviceStatus;

    public Device() {
    }

    public Device(String deviceName, String deviceAddress, int deviceStatus) {
        super();
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
        this.deviceStatus = deviceStatus;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public void setDeviceAddress(String deviceAddress) {
        this.deviceAddress = deviceAddress;
    }

    public int getDeviceStatus() {
        return deviceStatus;
    }

    public void setDeviceStatus(int deviceStatus) {
        this.deviceStatus = deviceStatus;
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceName);
        dest.writeString(deviceAddress);
        dest.writeInt(deviceStatus);
    }

    /** Implement the Parcelable interface */
    public static final Creator<Device> CREATOR =
        new Creator<Device>() {
            public Device createFromParcel(Parcel in) {
                Device device = new Device();
                device.deviceName = in.readString();
                device.deviceAddress = in.readString();
                device.deviceStatus = in.readInt();
                return device;
            }

            public Device[] newArray(int size) {
                return new Device[size];
            }
        };
}
