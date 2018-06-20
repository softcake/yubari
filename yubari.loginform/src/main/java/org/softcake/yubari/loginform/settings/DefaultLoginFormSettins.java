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
import org.softcake.yubari.loginform.controller.PlatformEnvironment;
import org.softcake.yubari.loginform.controller.PlatformMode;
import org.softcake.yubari.loginform.controller.PlatformThemeValue;
import org.softcake.yubari.loginform.controller.PlatformZoomValue;
import org.softcake.yubari.loginform.utils.ObjectUtils;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLoginFormSettins {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoginFormSettins.class);

    public DefaultLoginFormSettins() {
    }

    public Language getLanguage() {
        boolean localeIsDetected = false;
        Language systemLanguage = Language.ENGLISH;

        int var6;
        try {
            String systemLanguageAsString = System.getProperty("user.language");
            if (!ObjectUtils.isNullOrEmpty(systemLanguageAsString)) {
                Language[] var4 = Language.values();
                int var5 = var4.length;

                for(var6 = 0; var6 < var5; ++var6) {
                    Language language = var4[var6];
                    Locale locale = language.getLocale();
                    String languageAsString = locale.getLanguage();
                    if (languageAsString.equals(systemLanguageAsString)) {
                        systemLanguage = language;
                        localeIsDetected = true;
                        break;
                    }
                }
            }
        } catch (Throwable var12) {
            LOGGER.error(var12.getMessage(), var12);
        }

        if (!localeIsDetected) {
            try {
                Locale defaultLocale = Locale.getDefault();
                String systemLanguageAsString = defaultLocale.getLanguage();
                Language[] var15 = Language.values();
                var6 = var15.length;

                for(int var16 = 0; var16 < var6; ++var16) {
                    Language language = var15[var16];
                    Locale locale = language.getLocale();
                    String languageAsString = locale.getLanguage();
                    if (languageAsString.equals(systemLanguageAsString)) {
                        systemLanguage = language;
                        localeIsDetected = true;
                        break;
                    }
                }
            } catch (Throwable var11) {
                LOGGER.error(var11.getMessage(), var11);
            }
        }

        if (!localeIsDetected) {
            LOGGER.error("Cannot detect system language, the <" + systemLanguage + "> will be used.");
        }

        return systemLanguage;
    }

    public PlatformEnvironment getPlatformEnvironment() {
        return PlatformEnvironment.LIVE_3;
    }

    public PlatformMode getPlatformMode() {
        return PlatformMode.JFOREX;
    }

    public boolean isSystemProxyDetecting() {
        return true;
    }

    public String getMaxHeapSize() {
        return "-Xmx512m";
    }

    public boolean isShowJavaHeapSize() {
        return false;
    }

    public boolean isUseSystemMaxHeapSize() {
        return true;
    }

    public PlatformThemeValue getPlatformThemeValue() {
        return PlatformThemeValue.DARK_THEME;
    }

    public PlatformZoomValue getPlatformZoomValue() {
        PlatformZoomValue platformZoomValue = PlatformZoomValue.x100;

        try {
            GraphicsEnvironment localGraphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice defaultScreenDevice = localGraphicsEnvironment.getDefaultScreenDevice();
            DisplayMode displayMode = defaultScreenDevice.getDisplayMode();
            int screenWidth = displayMode.getWidth();
            if (screenWidth < 1900) {
                platformZoomValue = PlatformZoomValue.x100;
            } else if (screenWidth < 3000) {
                platformZoomValue = PlatformZoomValue.x110;
            } else {
                platformZoomValue = PlatformZoomValue.x125;
            }
        } catch (Throwable var6) {
            LOGGER.error(var6.getMessage(), var6);
        }

        return platformZoomValue;
    }

    public boolean isAstologin() {
        return false;
    }

    public LoginDialogBean getLoginDialogBean() {
        LoginDialogBean loginDialogBean = new LoginDialogBean();
        loginDialogBean.setPlatformLanguage(Language.ENGLISH);
        loginDialogBean.setPlatformEnvironment(PlatformEnvironment.LIVE);
        loginDialogBean.setPlatformMode(PlatformMode.JFOREX);
        loginDialogBean.setSystemProxyDetecting(this.isSystemProxyDetecting());
        loginDialogBean.setMaxHeapSize(this.getMaxHeapSize());
        loginDialogBean.setShowJavaHeapSize(this.isShowJavaHeapSize());
        loginDialogBean.setUseSystemMaxHeapSize(this.isUseSystemMaxHeapSize());
        loginDialogBean.setPlatformThemeValue(this.getPlatformThemeValue());
        loginDialogBean.setPlatformZoomValue(this.getPlatformZoomValue());
        loginDialogBean.setAutologin(this.isAstologin());
        return loginDialogBean;
    }
}
