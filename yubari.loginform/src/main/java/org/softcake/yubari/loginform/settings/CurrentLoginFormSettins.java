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

package org.softcake.yubari.loginform.settings;

import org.softcake.yubari.loginform.controller.Language;
import org.softcake.yubari.loginform.controller.LoginDialogBean;
import org.softcake.yubari.loginform.controller.LoginDialogMode;
import org.softcake.yubari.loginform.controller.PlatformEnvironment;
import org.softcake.yubari.loginform.controller.PlatformMode;
import org.softcake.yubari.loginform.controller.PlatformThemeValue;
import org.softcake.yubari.loginform.controller.PlatformZoomValue;
import org.softcake.yubari.loginform.utils.FilePathManager;
import org.softcake.yubari.loginform.utils.IFilePathManager;
import org.softcake.yubari.loginform.utils.ObjectUtils;



import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CurrentLoginFormSettins implements ILoginFormSettins {
    private static final Logger LOGGER = LoggerFactory.getLogger(CurrentLoginFormSettins.class);
    private static final String PROPERTIES_FILE_NAME = "login.form.properties";
    private Properties properties = null;

    public CurrentLoginFormSettins() {
    }

    public synchronized LoginDialogBean getLoginDialogBean() {
        DefaultLoginFormSettins defaultLoginFormSettins = new DefaultLoginFormSettins();
        if (this.properties == null) {
            this.properties = new Properties();
            LOGGER.error("The default login form setting are used.");
        }

        Language platformLanguage = defaultLoginFormSettins.getLanguage();
        PlatformEnvironment platformEnvironment = defaultLoginFormSettins.getPlatformEnvironment();
        PlatformMode platformMode = defaultLoginFormSettins.getPlatformMode();
        String maxHeapSizeDefault = defaultLoginFormSettins.getMaxHeapSize();
        boolean showJavaHeapSizeDefault = defaultLoginFormSettins.isShowJavaHeapSize();
        boolean useSystemMaxHeapSizeDefault = defaultLoginFormSettins.isUseSystemMaxHeapSize();
        PlatformThemeValue platformThemeDefault = defaultLoginFormSettins.getPlatformThemeValue();
        PlatformZoomValue platformZoomDefault = defaultLoginFormSettins.getPlatformZoomValue();
        boolean autologinDefault = defaultLoginFormSettins.isAstologin();
        String languageAsString = this.properties.getProperty("LANGUAGE");
        String environmentAsString = this.properties.getProperty("ENVIRONMENT");
        String modeAsString = this.properties.getProperty("MODE");
        String proxyHost = this.properties.getProperty("PROXY_ADDRESS_NAME");
        String proxyPort = this.properties.getProperty("PROXY_PORT");
        String systemProxyDetectingAsString = this.properties.getProperty("SYSTEM_PROXY_DETECTING");
        String proxyUserName = this.properties.getProperty("PROXY_USER_NAME_SETTING");
        String proxyPassword = this.properties.getProperty("PROXY_PASSWORD_SETTING");
        String proxyUserCredentialsUsedAsString = this.properties.getProperty("PROXY_USER_CREDENTIALS_USED");
        String maxHeapSize = JVMHeapSizeSettingsHelper.getJVMMaxHeapSize();
        String showJavaHeapSizeAsString = this.properties.getProperty("SHOW_JAVA_HEAP_SIZE");
        String useSystemMaxHeapSizeAsString = this.properties.getProperty("USE_SYSTEM_MAX_HEAP_SIZE");
        String platformThemeAsString = this.properties.getProperty("PLATFORM_THEME");
        String platformZoomAsString = this.properties.getProperty("PLATFORM_ZOOM");
        String autologinAsString = this.properties.getProperty("AUTO_LOGIN");
        String autoLoginIgnoreAsString = this.properties.getProperty("AUTO_LOGIN_IGNORE");
        if (languageAsString != null && !languageAsString.isEmpty()) {
            try {
                Language[] var27 = Language.values();
                int var28 = var27.length;

                for(int var29 = 0; var29 < var28; ++var29) {
                    Language language = var27[var29];
                    if (language.getLocale().getLanguage().equals(languageAsString)) {
                        platformLanguage = language;
                        break;
                    }
                }
            } catch (Throwable var48) {
                LOGGER.error(var48.getMessage(), var48);
            }
        }

        if (environmentAsString != null && !environmentAsString.isEmpty()) {
            try {
                platformEnvironment = PlatformEnvironment.fromValue(environmentAsString);
            } catch (Throwable var47) {
                LOGGER.error(var47.getMessage(), var47);
            }
        }

        if (modeAsString != null && !modeAsString.isEmpty()) {
            try {
                platformMode = PlatformMode.valueOf(modeAsString);
            } catch (Throwable var46) {
                LOGGER.error(var46.getMessage(), var46);
            }
        }

        Integer proxyPortAsInt = null;

        try {
            if (proxyPort != null) {
                proxyPort = proxyPort.trim();
                if (!proxyPort.isEmpty()) {
                    proxyPortAsInt = Integer.valueOf(proxyPort);
                }
            }
        } catch (Throwable var45) {
            LOGGER.error("Cannot get the proxy port from settins");
            LOGGER.error(var45.getMessage(), var45);
        }

        boolean systemProxyDetecting = true;

        try {
            if (systemProxyDetectingAsString != null) {
                systemProxyDetectingAsString = systemProxyDetectingAsString.trim();
            }

            if (!ObjectUtils.isNullOrEmpty(systemProxyDetectingAsString)) {
                systemProxyDetecting = Boolean.valueOf(systemProxyDetectingAsString);
            }
        } catch (Throwable var44) {
            LOGGER.error("Cannot get the <systemProxyDetecting> from settins");
            LOGGER.error(var44.getMessage(), var44);
        }

        boolean proxyUserCredentialsUsed = false;

        try {
            if (proxyUserCredentialsUsedAsString != null) {
                proxyUserCredentialsUsedAsString = proxyUserCredentialsUsedAsString.trim();
            }

            if (!ObjectUtils.isNullOrEmpty(proxyUserCredentialsUsedAsString)) {
                proxyUserCredentialsUsed = Boolean.valueOf(proxyUserCredentialsUsedAsString);
            }
        } catch (Throwable var43) {
            LOGGER.error("Cannot get the <proxyUserCredentialsUsed> from settins");
            LOGGER.error(var43.getMessage(), var43);
        }

        try {
            if (maxHeapSize != null) {
                maxHeapSize = maxHeapSize.trim();
            }

            if (ObjectUtils.isNullOrEmpty(maxHeapSize)) {
                maxHeapSize = maxHeapSizeDefault;
            }
        } catch (Throwable var42) {
            LOGGER.error(var42.getMessage(), var42);
        }

        boolean showJavaHeapSize = showJavaHeapSizeDefault;

        try {
            if (showJavaHeapSizeAsString != null) {
                showJavaHeapSizeAsString = showJavaHeapSizeAsString.trim();
            }

            if (!ObjectUtils.isNullOrEmpty(showJavaHeapSizeAsString)) {
                showJavaHeapSize = Boolean.valueOf(showJavaHeapSizeAsString);
            }
        } catch (Throwable var41) {
            LOGGER.error(var41.getMessage(), var41);
        }

        boolean useSystemMaxHeapSize = useSystemMaxHeapSizeDefault;

        try {
            if (useSystemMaxHeapSizeAsString != null) {
                useSystemMaxHeapSizeAsString = useSystemMaxHeapSizeAsString.trim();
            }

            if (!ObjectUtils.isNullOrEmpty(useSystemMaxHeapSizeAsString)) {
                useSystemMaxHeapSize = Boolean.valueOf(useSystemMaxHeapSizeAsString);
            }
        } catch (Throwable var40) {
            LOGGER.error(var40.getMessage(), var40);
        }

        PlatformThemeValue platformThemeValue = platformThemeDefault;
        if (!ObjectUtils.isNullOrEmpty(platformThemeAsString)) {
            try {
                platformThemeValue = PlatformThemeValue.fromValue(platformThemeAsString);
            } catch (Throwable var39) {
                LOGGER.error(var39.getMessage(), var39);
            }
        }

        PlatformZoomValue platformZoomValue = platformZoomDefault;
        if (!ObjectUtils.isNullOrEmpty(platformZoomAsString)) {
            try {
                int platformZoomAsInt = Integer.parseInt(platformZoomAsString);
                platformZoomValue = PlatformZoomValue.fromValue(platformZoomAsInt);
            } catch (Throwable var38) {
                LOGGER.error(var38.getMessage(), var38);
            }
        }

        boolean autologin = autologinDefault;
        if (!ObjectUtils.isNullOrEmpty(autologinAsString)) {
            try {
                autologin = Boolean.valueOf(autologinAsString);
            } catch (Throwable var37) {
                LOGGER.error(var37.getMessage(), var37);
            }
        }

        boolean autoLoginIgnore = false;
        if (!ObjectUtils.isNullOrEmpty(autoLoginIgnoreAsString)) {
            autoLoginIgnore = Boolean.valueOf(autoLoginIgnoreAsString);
        }

        LoginDialogBean loginDialogBean = new LoginDialogBean();
        loginDialogBean.setPlatformLanguage(platformLanguage);
        loginDialogBean.setPlatformEnvironment(platformEnvironment);
        loginDialogBean.setPlatformMode(platformMode);
        loginDialogBean.setProxyHost(proxyHost);
        loginDialogBean.setProxyPort(proxyPortAsInt);
        loginDialogBean.setSystemProxyDetecting(systemProxyDetecting);
        loginDialogBean.setProxyUserName(proxyUserName);
        loginDialogBean.setProxyPassword(proxyPassword);
        loginDialogBean.setProxyUserCredentialsUsed(proxyUserCredentialsUsed);
        loginDialogBean.setMaxHeapSize(maxHeapSize);
        loginDialogBean.setShowJavaHeapSize(showJavaHeapSize);
        loginDialogBean.setUseSystemMaxHeapSize(useSystemMaxHeapSize);
        loginDialogBean.setPlatformThemeValue(platformThemeValue);
        loginDialogBean.setPlatformZoomValue(platformZoomValue);
        loginDialogBean.setAutologin(autologin);
        loginDialogBean.setAutoLoginIgnore(autoLoginIgnore);
        return loginDialogBean;
    }

    public synchronized void saveProperties(LoginDialogBean loginDialogBean, LoginDialogMode loginDialogMode) {
        Properties properties = new Properties();
        FileOutputStream output = null;

        try {
            File propertiesFile = this.getPropertiesFile();
            if (propertiesFile == null) {
                LOGGER.error("Cannot save properties, the file is null.");
                return;
            }

            output = new FileOutputStream(propertiesFile);

            try {
                properties.put("LANGUAGE", loginDialogBean.getPlatformLanguage().getLocale().getLanguage());
            } catch (Exception var48) {
                LOGGER.error(var48.getMessage(), var48);
            }

            try {
                PlatformEnvironment platformEnvironment = loginDialogBean.getPlatformEnvironment();
                if (platformEnvironment != null) {
                    if (loginDialogMode == LoginDialogMode.WEB_START) {
                        if (platformEnvironment == PlatformEnvironment.DEMO) {
                            platformEnvironment = PlatformEnvironment.DEMO_3;
                        } else if (platformEnvironment == PlatformEnvironment.LIVE) {
                            platformEnvironment = PlatformEnvironment.LIVE_3;
                        }
                    }

                    properties.put("ENVIRONMENT", platformEnvironment.getEnvironment());
                }
            } catch (Exception var47) {
                LOGGER.error(var47.getMessage(), var47);
            }

            try {
                properties.put("MODE", loginDialogBean.getPlatformMode().getModeName());
            } catch (Exception var46) {
                LOGGER.error(var46.getMessage(), var46);
            }

            String storedMaxHeapSize;
            try {
                storedMaxHeapSize = loginDialogBean.getProxyHost();
                if (storedMaxHeapSize != null && !storedMaxHeapSize.isEmpty()) {
                    properties.put("PROXY_ADDRESS_NAME", storedMaxHeapSize);
                }
            } catch (Exception var45) {
                LOGGER.error(var45.getMessage(), var45);
            }

            try {
                Integer proxyPort = loginDialogBean.getProxyPort();
                if (proxyPort != null) {
                    properties.put("PROXY_PORT", String.valueOf(proxyPort));
                }
            } catch (Exception var44) {
                LOGGER.error(var44.getMessage(), var44);
            }

            boolean autoLoginIgnore;
            try {
                autoLoginIgnore = loginDialogBean.isSystemProxyDetecting();
                properties.put("SYSTEM_PROXY_DETECTING", String.valueOf(autoLoginIgnore));
            } catch (Exception var43) {
                LOGGER.error(var43.getMessage(), var43);
            }

            try {
                storedMaxHeapSize = loginDialogBean.getProxyUserName();
                if (storedMaxHeapSize != null && !storedMaxHeapSize.isEmpty()) {
                    properties.put("PROXY_USER_NAME_SETTING", storedMaxHeapSize);
                }
            } catch (Exception var42) {
                LOGGER.error(var42.getMessage(), var42);
            }

            try {
                storedMaxHeapSize = loginDialogBean.getProxyPassword();
                if (storedMaxHeapSize != null && !storedMaxHeapSize.isEmpty()) {
                    properties.put("PROXY_PASSWORD_SETTING", storedMaxHeapSize);
                }
            } catch (Exception var41) {
                LOGGER.error(var41.getMessage(), var41);
            }

            try {
                autoLoginIgnore = loginDialogBean.isProxyUserCredentialsUsed();
                properties.put("PROXY_USER_CREDENTIALS_USED", String.valueOf(autoLoginIgnore));
            } catch (Exception var40) {
                LOGGER.error(var40.getMessage(), var40);
            }

            try {
                if (loginDialogMode == LoginDialogMode.STAND_ALONE) {
                    storedMaxHeapSize = JVMHeapSizeSettingsHelper.getJVMMaxHeapSize();
                    if (loginDialogBean.isUseSystemMaxHeapSize()) {
                        if (!ObjectUtils.isNullOrEmpty(storedMaxHeapSize)) {
                            JVMHeapSizeSettingsHelper.deleteJVMMaxHeapSize();
                        }
                    } else {
                        String maxHeapSize = loginDialogBean.getMaxHeapSize();
                        if (!ObjectUtils.isEqual(storedMaxHeapSize, maxHeapSize)) {
                            JVMHeapSizeSettingsHelper.saveJVMMaxHeapSize(maxHeapSize);
                        }
                    }
                }
            } catch (Exception var39) {
                LOGGER.error(var39.getMessage(), var39);
            }

            try {
                autoLoginIgnore = loginDialogBean.isShowJavaHeapSize();
                properties.put("SHOW_JAVA_HEAP_SIZE", String.valueOf(autoLoginIgnore));
            } catch (Exception var38) {
                LOGGER.error(var38.getMessage(), var38);
            }

            try {
                autoLoginIgnore = loginDialogBean.isUseSystemMaxHeapSize();
                properties.put("USE_SYSTEM_MAX_HEAP_SIZE", String.valueOf(autoLoginIgnore));
            } catch (Exception var37) {
                LOGGER.error(var37.getMessage(), var37);
            }

            try {
                PlatformThemeValue platformThemeValue = loginDialogBean.getPlatformThemeValue();
                properties.put("PLATFORM_THEME", platformThemeValue.name());
            } catch (Exception var36) {
                LOGGER.error(var36.getMessage(), var36);
            }

            try {
                PlatformZoomValue platformZoomValue = loginDialogBean.getPlatformZoomValue();
                int platformZoomValueAsInt = platformZoomValue.getValue();
                properties.put("PLATFORM_ZOOM", String.valueOf(platformZoomValueAsInt));
            } catch (Exception var35) {
                LOGGER.error(var35.getMessage(), var35);
            }

            try {
                autoLoginIgnore = loginDialogBean.isAutologin();
                properties.put("AUTO_LOGIN", String.valueOf(autoLoginIgnore));
            } catch (Exception var34) {
                LOGGER.error(var34.getMessage(), var34);
            }

            try {
                autoLoginIgnore = loginDialogBean.isAutoLoginIgnore();
                properties.put("AUTO_LOGIN_IGNORE", String.valueOf(autoLoginIgnore));
            } catch (Exception var33) {
                LOGGER.error(var33.getMessage(), var33);
            }

            properties.store(output, (String)null);
            this.properties = properties;
        } catch (Throwable var49) {
            LOGGER.error(var49.getMessage(), var49);
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable var32) {
                    LOGGER.error(var32.getMessage(), var32);
                }
            }

        }

    }

    public synchronized void loadProperties() {
        this.properties = new Properties();
        FileInputStream input = null;

        try {
            File propertiesFile = this.getPropertiesFile();
            if (propertiesFile == null) {
                LOGGER.error("Cannot load properties, the file is null.");
                return;
            }

            if (propertiesFile.exists()) {
                input = new FileInputStream(propertiesFile);
                this.properties.load(input);
                return;
            }
        } catch (Throwable var14) {
            LOGGER.error(var14.getMessage(), var14);
            return;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable var13) {
                    LOGGER.error(var13.getMessage(), var13);
                }
            }

        }

    }

    private File getPropertiesFile() {
        File propertiesFile = null;

        try {
            IFilePathManager filePathManager = FilePathManager.getInstance();
            String defaultJForexFolderPath = filePathManager.getDefaultJForexFolderPath();
            File forexFolderPath = filePathManager.checkAndGetFolder(defaultJForexFolderPath);
            if (!forexFolderPath.isDirectory()) {
                LOGGER.error("Cannot create the JForex directory.");
                return null;
            }

            String propertiesFilePath = filePathManager.addPathSeparatorIfNeeded(defaultJForexFolderPath);
            propertiesFilePath = propertiesFilePath + "login.form.properties";
            propertiesFile = new File(propertiesFilePath);
        } catch (Throwable var6) {
            LOGGER.error(var6.getMessage(), var6);
        }

        return propertiesFile;
    }
}
