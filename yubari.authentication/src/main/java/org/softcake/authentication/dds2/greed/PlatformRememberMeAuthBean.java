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

package org.softcake.authentication.dds2.greed;

import com.dukascopy.login.service.account.DeviceIDBean;

public class PlatformRememberMeAuthBean {
    private String autoLoginUserName = null;
    private DeviceIDBean deviceIDBean = null;
    private String rememberMeToken = null;
    private Boolean autoLoginIgnore = null;
    private Boolean autoLoginFlag = null;

    public PlatformRememberMeAuthBean(String autoLoginUserName, DeviceIDBean deviceIDBean, String rememberMeToken, boolean autoLoginIgnore, boolean autologinFlag) {
        this.autoLoginUserName = autoLoginUserName;
        this.deviceIDBean = deviceIDBean;
        this.rememberMeToken = rememberMeToken;
        this.autoLoginIgnore = autoLoginIgnore;
        this.autoLoginFlag = autologinFlag;
    }

    public PlatformRememberMeAuthBean() {
    }

    public boolean autoAuthByRememberMeTokenIsPossible() {
        if (!this.validate()) {
            return false;
        } else {
            return !this.autoLoginIgnore && this.autoLoginFlag;
        }
    }

    public boolean authByRememberMeTokenIsPossible() {
        return this.validate();
    }

    private boolean validate() {
        if (this.autoLoginUserName != null && this.deviceIDBean != null && this.rememberMeToken != null && this.autoLoginIgnore != null && this.autoLoginFlag != null) {
            return !this.autoLoginUserName.isEmpty() && !this.rememberMeToken.isEmpty() && !this.deviceIDBean.isEmpty();
        } else {
            return false;
        }
    }

    public String getAutoLoginUserName() {
        return this.autoLoginUserName;
    }

    public void setAutoLoginUserName(String autoLoginUserName) {
        this.autoLoginUserName = autoLoginUserName;
    }

    public DeviceIDBean getDeviceIDBean() {
        return this.deviceIDBean;
    }

    public void setDeviceIDBean(DeviceIDBean deviceIDBean) {
        this.deviceIDBean = deviceIDBean;
    }

    public String getRememberMeToken() {
        return this.rememberMeToken;
    }

    public void setRememberMeToken(String rememberMeToken) {
        this.rememberMeToken = rememberMeToken;
    }

    public boolean isAutoLoginIgnore() {
        return this.autoLoginIgnore;
    }

    public void setAutoLoginIgnore(boolean autoLoginIgnore) {
        this.autoLoginIgnore = autoLoginIgnore;
    }

    public boolean isAutologinFlag() {
        return this.autoLoginFlag;
    }

    public void setAutoLoginFlag(boolean autoLoginFlag) {
        this.autoLoginFlag = autoLoginFlag;
    }
}
