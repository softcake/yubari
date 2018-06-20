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

package org.softcake.yubari.loginform.service;

import org.softcake.yubari.loginform.controller.LoginDialogBean;
import org.softcake.yubari.loginform.controller.PlatformThemeValue;
import org.softcake.yubari.loginform.controller.PlatformZoomValue;

import java.awt.GraphicsEnvironment;

public class AppearanceThemesSettingsStorage implements IAppearanceThemesSettingsStorage {
    private LoginDialogBean loginDialogBean;

    public AppearanceThemesSettingsStorage() {
    }

    public String loadAppearanceThemeNameOrPath() {
        PlatformThemeValue platformThemeValue = this.loginDialogBean.getPlatformThemeValue();
        switch(platformThemeValue) {
        case DARK_THEME:
            return IAppearanceThemeManager.AppearanceTheme.DARK.name();
        case DEFAULT_THEME:
            return IAppearanceThemeManager.AppearanceTheme.DEFAULT.name();
        default:
            return IAppearanceThemeManager.AppearanceTheme.DEFAULT.name();
        }
    }

    public void putAppearanceThemeNameOrPath(String data) {
    }

    public IAppearanceThemeManager.ZoomMode loadZoomMode() {
        PlatformZoomValue platformZoomValue = this.loginDialogBean.getPlatformZoomValue();
        switch(platformZoomValue) {
        case x100:
            return IAppearanceThemeManager.ZoomMode.STANDARD;
        case x110:
            return IAppearanceThemeManager.ZoomMode.LARGE_110;
        case x125:
            return IAppearanceThemeManager.ZoomMode.LARGE_125;
        case x150:
            return IAppearanceThemeManager.ZoomMode.LARGE_150;
        default:
            return IAppearanceThemeManager.ZoomMode.STANDARD;
        }
    }

    private IAppearanceThemeManager.ZoomMode getDefaultMode() {
        int screenWidth = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getWidth();
        if (screenWidth < 2000) {
            return IAppearanceThemeManager.ZoomMode.STANDARD;
        } else {
            return screenWidth < 3000 ? IAppearanceThemeManager.ZoomMode.LARGE_110 : IAppearanceThemeManager.ZoomMode.LARGE_125;
        }
    }

    public void putZoomMode(IAppearanceThemeManager.ZoomMode zoomMode) {
    }

    public void saveDefaultChartTheme(String themeName) {
    }

    public String loadDefaultChartTheme() {
        return null;
    }

    public LoginDialogBean getLoginDialogBean() {
        return this.loginDialogBean;
    }

    public void setLoginDialogBean(LoginDialogBean loginDialogBean) {
        this.loginDialogBean = loginDialogBean;
    }
}
