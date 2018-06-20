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

import org.softcake.yubari.loginform.controller.PlatformEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserBean {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserBean.class);
    public static final int DEFAULT_PASSWORD_LENGTH = 8;
    private PlatformEnvironment platformEnvironment;
    private String userName;
    private boolean rememberMe;
    private String rememberMeToken;
    private Boolean tokenSuccessfullyDecoded;
    private int passwordLength = 0;

    public UserBean(PlatformEnvironment platformEnvironment, String userName) {
        this.platformEnvironment = platformEnvironment;
        this.userName = userName;
    }

    public UserBean(PlatformEnvironment platformEnvironment, String userName, boolean rememberMe, String rememberMeToken) {
        this.platformEnvironment = platformEnvironment;
        this.userName = userName;
        this.rememberMe = rememberMe;
        this.rememberMeToken = rememberMeToken;
    }

    public PlatformEnvironment getPlatformEnvironment() {
        return this.platformEnvironment;
    }

    public void setPlatformEnvironment(PlatformEnvironment platformEnvironment) {
        this.platformEnvironment = platformEnvironment;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isRememberMe() {
        return this.rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    public String getRememberMeToken() {
        return this.rememberMeToken;
    }

    public void setRememberMeToken(String rememberMeToken) {
        this.rememberMeToken = rememberMeToken;
    }

    public boolean isAutoLoginAsRememberMePossible() {
        return this.rememberMe && this.rememberMeToken != null && !this.rememberMeToken.isEmpty();
    }

    public Boolean isTokenSuccessfullyDecoded() {
        return this.tokenSuccessfullyDecoded;
    }

    public void setTokenSuccessfullyDecoded(Boolean tokenSuccessfullyDecoded) {
        this.tokenSuccessfullyDecoded = tokenSuccessfullyDecoded;
    }

    public int getPasswordLength() {
        return this.passwordLength == 0 ? 8 : this.passwordLength;
    }

    public void setPasswordLength(int passwordLength) {
        this.passwordLength = passwordLength;
    }

    public String getFictivePassword() {
        String fictivePassword = "";

        try {
            StringBuilder buf = new StringBuilder(32);

            for(int i = 0; i < this.getPasswordLength(); ++i) {
                buf.append("0");
            }

            fictivePassword = buf.toString();
        } catch (Exception var4) {
            LOGGER.error(var4.getMessage(), var4);
        }

        if (fictivePassword == null || fictivePassword.isEmpty()) {
            fictivePassword = "12345678";
        }

        return fictivePassword;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(64);
        buf.append("UserBean:").append("\n").append("   ").append("Environment:").append(this.platformEnvironment).append("\n").append("   ").append("UserName:").append(this.userName).append("\n").append("   ").append("RememberMe:").append(this.rememberMe).append("\n").append("   ").append("rememberMeToken:").append(this.rememberMeToken);
        return buf.toString();
    }
}
