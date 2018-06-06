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


import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.LoginDialogBean;
import com.dukascopy.login.controller.PlatformEnvironment;
import com.dukascopy.login.controller.PlatformMode;
import com.dukascopy.login.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformParameters {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformParameters.class);
    public static final String CLIENT_USERNAME = "jnlp.client.username";
    public static final String CLIENT_PASSWORD = "jnlp.client.password";
    public static final String CLIENT_MODE = "jnlp.client.mode";
    public static final String PLATFORM_MODE = "jnlp.platform.mode";
    public static final String USE_PIN = "jnlp.use.pin";
    public static final String JNLP_PINHMTLCUT = "jnlp.pinhmtlcut";
    public static final String PLATFORM_NAME = "jnlp.platform.name";
    public static final String HTTP_PROXY_HOST = "http.proxyHost";
    public static final String HTTP_PROXY_PORT = "http.proxyPort";
    public static final String HTTPS_PROXY_HOST = "https.proxyHost";
    public static final String HTTPS_PROXY_PORT = "https.proxyPort";
    public static final String FTP_PROXY_HOST = "ftp.proxyHost";
    public static final String FTP_PROXY_PORT = "ftp.proxyPort";

    public PlatformParameters() {
    }

    public static boolean isUsePin() {
        String pinEntered = System.getProperty("jnlp.use.pin");
        boolean isPinEntered = Boolean.valueOf(pinEntered);
        return isPinEntered;
    }

    public static boolean isUsePinParameterExist() {
        return System.getProperty("jnlp.use.pin") != null;
    }

    public static String getUserName() {
        return System.getProperty("jnlp.client.username");
    }

    public static String getPassword() {
        return System.getProperty("jnlp.client.password");
    }

    public static String getClientMode() {
        return System.getProperty("jnlp.client.mode");
    }

    public static String getPlatformMode() {
        return System.getProperty("jnlp.platform.mode");
    }

    public static PlatformEnvironment getClientModeAsEnum() {
        String clientModeAsString = System.getProperty("jnlp.client.mode");
        if (clientModeAsString != null) {
            clientModeAsString = clientModeAsString.trim();
        }

        if (ObjectUtils.isNullOrEmpty(clientModeAsString)) {
            return null;
        } else {
            PlatformEnvironment platformEnvironmentAsEnum = PlatformEnvironment.fromValue(clientModeAsString);
            return platformEnvironmentAsEnum;
        }
    }

    public static PlatformMode getPlatformModeAsEnum() {
        String platformModeAsStrig = System.getProperty("jnlp.platform.mode");
        if (platformModeAsStrig != null) {
            platformModeAsStrig = platformModeAsStrig.trim();
        }

        if (ObjectUtils.isNullOrEmpty(platformModeAsStrig)) {
            return null;
        } else {
            PlatformMode platformModeAsEnum = PlatformMode.fromJNLPValue(platformModeAsStrig);
            return platformModeAsEnum;
        }
    }

    public static String getPlatformName() {
        String platformName = System.getProperty("jnlp.platform.name");
        return platformName;
    }

    public static void fill(LoginDialogBean loginDialogBean) {
        if (loginDialogBean == null) {
            LOGGER.error("Cannot fill the loginDialogBean, the loginDialogBean is null");
        } else {
            String userName = getUserName();
            String password = getPassword();
            PlatformMode platformModeAsEnum = getPlatformModeAsEnum();
            PlatformEnvironment platformEnvironmentAsEnum = getClientModeAsEnum();
            if (!ObjectUtils.isNullOrEmpty(userName)) {
                loginDialogBean.setLoginName(userName);
            }

            if (!ObjectUtils.isNullOrEmpty(password)) {
                loginDialogBean.setPassword(password);
            }

            if (!ObjectUtils.isNullOrEmpty(platformModeAsEnum)) {
                loginDialogBean.setPlatformMode(platformModeAsEnum);
            }

            if (!ObjectUtils.isNullOrEmpty(platformEnvironmentAsEnum)) {
                loginDialogBean.setPlatformEnvironment(platformEnvironmentAsEnum);
            }

            boolean dialogWithTwoViews = CompatibilityUtil.isDialogWithTwoViews(ILoginDialogController.class);
            if (!dialogWithTwoViews && isUsePinParameterExist()) {
                loginDialogBean.setPinSelected(isUsePin());
            }

        }
    }

    public static boolean isAutoLoginPossibleByPasswordAndUserName(LoginDialogBean loginDialogBean) {
        String loginName = loginDialogBean.getLoginName();
        String password = loginDialogBean.getPassword();
        if (loginName != null) {
            loginName = loginName.trim();
        }

        if (password != null) {
            password = password.trim();
        }

        return loginName != null && !loginName.isEmpty() && password != null && !password.isEmpty();
    }

    public static void setSystemProperty(String key, String value) {
        if (!ObjectUtils.isNullOrEmpty(key) && value != null) {
            System.getProperties().put(key, value);
        }

    }
}
