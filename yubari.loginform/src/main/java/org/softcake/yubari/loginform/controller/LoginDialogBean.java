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

package org.softcake.yubari.loginform.controller;







import java.util.List;

public class LoginDialogBean {
    private Language platformLanguage;
    private List<Language> platformLanguages;
    private PlatformEnvironment platformEnvironment;
    private boolean betaPlatformVersionSelected = true;
    private String loginName;
    private String password;
    private String fictivePassword;
    private String secureCode;
    private PlatformMode platformMode;
    private String platformVersion;
    private boolean pinSelected;
    private boolean pinSelectedHasDefaultValue;
    private String captchaId;
    private String proxyHost;
    private Integer proxyPort;
    private boolean systemProxyDetecting;
    private String maxHeapSize;
    private boolean useSystemMaxHeapSize;
    private boolean showJavaHeapSize;
    private PlatformThemeValue platformThemeValue;
    private PlatformZoomValue platformZoomValue;
    private boolean rememberMe;
    private String proxyUserName;
    private String proxyPassword;
    private boolean proxyUserCredentialsUsed;
    private Integer wlPartnerId;
    private boolean autologin;
    private boolean autoLoginIgnore;

    public LoginDialogBean() {
    }

    public String getLoginName() {
        return this.loginName;
    }

    public void setLoginName(String loginName) {
        if (loginName != null) {
            loginName = loginName.trim();
        }

        this.loginName = loginName;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
        this.fictivePassword = null;
    }

    public String getFictivePassword() {
        return this.fictivePassword;
    }

    public void setFictivePassword(String fictivePassword) {
        this.fictivePassword = fictivePassword;
        this.password = null;
    }

    public boolean isFictivePassword() {
        return this.fictivePassword != null;
    }

    public String getSecureCode() {
        return this.secureCode;
    }

    public void setSecureCode(String secureCode) {
        this.secureCode = secureCode;
    }

    public PlatformMode getPlatformMode() {
        return this.platformMode;
    }

    public void setPlatformMode(PlatformMode platformMode) {
        this.platformMode = platformMode;
    }

    public PlatformEnvironment getPlatformEnvironment() {
        return this.platformEnvironment;
    }

    public void setPlatformEnvironment(PlatformEnvironment platformEnvironment) {
        this.platformEnvironment = platformEnvironment;
    }

    public String getPlatformVersion() {
        return this.platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        this.platformVersion = platformVersion;
    }

    public Language getPlatformLanguage() {
        return this.platformLanguage;
    }

    public void setPlatformLanguage(Language platformLanguage) {
        this.platformLanguage = platformLanguage;
    }

    public List<Language> getPlatformLanguages() {
        return this.platformLanguages;
    }

    public void setPlatformLanguages(List<Language> platformLanguages) {
        this.platformLanguages = platformLanguages;
    }

    public boolean isPinSelected() {
        return this.pinSelected;
    }

    public void setPinSelected(boolean pinSelected) {
        this.pinSelected = pinSelected;
        this.pinSelectedHasDefaultValue = false;
    }

    public void setPinSelected(boolean pinSelected, boolean pinSelectedHasDefaultValue) {
        this.pinSelected = pinSelected;
        this.pinSelectedHasDefaultValue = pinSelectedHasDefaultValue;
    }

    public boolean isPinSelectedHasDefaultValue() {
        return this.pinSelectedHasDefaultValue;
    }

    public String getCaptchaId() {
        return this.captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getProxyHost() {
        return this.proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return this.proxyPort;
    }

    public String getProxyPortAsString() {
        return String.valueOf(this.proxyPort);
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public boolean isSystemProxyDetecting() {
        return this.systemProxyDetecting;
    }

    public void setSystemProxyDetecting(boolean systemProxyDetecting) {
        this.systemProxyDetecting = systemProxyDetecting;
    }

    public String getMaxHeapSize() {
        return this.maxHeapSize;
    }

    public void setMaxHeapSize(String maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
    }

    public boolean isShowJavaHeapSize() {
        return this.showJavaHeapSize;
    }

    public void setShowJavaHeapSize(boolean showJavaHeapSize) {
        this.showJavaHeapSize = showJavaHeapSize;
    }

    public boolean isUseSystemMaxHeapSize() {
        return this.useSystemMaxHeapSize;
    }

    public void setUseSystemMaxHeapSize(boolean useSystemMaxHeapSize) {
        this.useSystemMaxHeapSize = useSystemMaxHeapSize;
    }

    public PlatformThemeValue getPlatformThemeValue() {
        return this.platformThemeValue;
    }

    public void setPlatformThemeValue(PlatformThemeValue platformThemeValue) {
        this.platformThemeValue = platformThemeValue;
    }

    public PlatformZoomValue getPlatformZoomValue() {
        return this.platformZoomValue;
    }

    public void setPlatformZoomValue(PlatformZoomValue platformZoomValue) {
        this.platformZoomValue = platformZoomValue;
    }

    public boolean isRememberMe() {
        return this.rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }

    public String getProxyUserName() {
        return this.proxyUserName;
    }

    public void setProxyUserName(String proxyUserName) {
        this.proxyUserName = proxyUserName;
    }

    public String getProxyPassword() {
        return this.proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public boolean isProxyUserCredentialsUsed() {
        return this.proxyUserCredentialsUsed;
    }

    public void setProxyUserCredentialsUsed(boolean proxyUserCredentialsUsed) {
        this.proxyUserCredentialsUsed = proxyUserCredentialsUsed;
    }

    public boolean isProxyHostSettingsExist() {
        return this.proxyHost != null && !this.proxyHost.isEmpty() && this.proxyPort != null && this.proxyPort != 0;
    }

    public boolean isProxyCredentialsSettingsExist() {
        return this.proxyUserName != null && this.proxyPassword != null && !this.proxyUserName.isEmpty() && !this.proxyPassword.isEmpty();
    }

    public boolean isAutologin() {
        return this.autologin;
    }

    public void setAutologin(boolean autologin) {
        this.autologin = autologin;
    }

    public boolean isAutoLoginIgnore() {
        return this.autoLoginIgnore;
    }

    public void setAutoLoginIgnore(boolean autoLoginIgnore) {
        this.autoLoginIgnore = autoLoginIgnore;
    }

    public boolean isBetaPlatformVersionSelected() {
        return this.betaPlatformVersionSelected;
    }

    public void setBetaPlatformVersionSelected(boolean betaPlatformVersionSelected) {
        this.betaPlatformVersionSelected = betaPlatformVersionSelected;
    }

    public Integer getWlPartnerId() {
        return this.wlPartnerId;
    }

    public void setWlPartnerId(Integer wlPartnerId) {
        this.wlPartnerId = wlPartnerId;
    }
}
