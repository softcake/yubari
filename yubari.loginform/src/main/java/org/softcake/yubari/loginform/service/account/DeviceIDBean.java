/*
 * Copyright 2018 softcake.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softcake.yubari.loginform.service.account;

public class DeviceIDBean {
    private boolean isDeviceNameAttribute;
    private String deviceId;
    private String deviceName;
    private String deviceOS;

    public DeviceIDBean(String deviceName, String deviceOS, String deviceId) {
        this.deviceName = deviceName;
        this.deviceOS = deviceOS;
        this.deviceId = deviceId;
        if (this.deviceName != null) {
            this.deviceName = this.deviceName.trim();
        }

        if (this.deviceOS != null) {
            this.deviceOS = this.deviceOS.trim();
        }

        if (this.deviceId != null) {
            this.deviceId = this.deviceId.trim();
        }

    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        if (this.deviceId != null) {
            this.deviceId = this.deviceId.trim();
        }

    }

    public String getDeviceName() {
        return this.deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        if (this.deviceName != null) {
            this.deviceName = this.deviceName.trim();
        }

    }

    public String getDeviceOS() {
        return this.deviceOS;
    }

    public void setDeviceOS(String deviceOS) {
        this.deviceOS = deviceOS;
        if (this.deviceOS != null) {
            this.deviceOS = this.deviceOS.trim();
        }

    }

    public boolean isEmpty() {
        return this.deviceId == null || this.deviceId.isEmpty();
    }

    public boolean isDeviceNameAttribute() {
        return this.isDeviceNameAttribute;
    }

    public void setDeviceNameAttribute(boolean isDeviceNameAttribute) {
        this.isDeviceNameAttribute = isDeviceNameAttribute;
    }
}
