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


import org.softcake.yubari.loginform.ui.components.IAppearanceThemeObservable;

import java.awt.Container;
import java.awt.Font;

public interface IAppearanceThemeManager {
    void loadInitialLookAndFeel(IAppearanceThemesSettingsStorage var1);

    IAppearanceThemeManager.AppearanceTheme getCurrentTheme();

    IAppearanceThemeManager.ZoomMode getCurrentZoomMode();

    void changeAppearanceTheme(IAppearanceThemeManager.AppearanceTheme var1, String var2);

    void changeFontMode(int var1);

    void changeZoomMode(IAppearanceThemeManager.ZoomMode var1);

    void addAppearanceThemeChangeListener(IAppearanceThemeObservable var1);

    Font getFont(String var1);

    Font getFont(String var1, IAppearanceThemeManager.ZoomMode var2);

    Font deriveFont(Font var1);

    Font deriveFont(Font var1, IAppearanceThemeManager.ZoomMode var2);

    void processContainerRecursively(Container var1, boolean var2, boolean var3);

    void updateFont();

    public static enum AppearanceTheme {
        DEFAULT("default_theme.xml"),
        DARK("dark_theme.xml"),
        CUSTOM((String)null);

        private final String fileName;

        private AppearanceTheme(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return this.fileName;
        }
    }

    public static enum ZoomMode {
        STANDARD(1.0F),
        LARGE_110(1.1F),
        LARGE_125(1.25F),
        LARGE_150(1.5F);

        private final float ratio;

        private ZoomMode(float ratio) {
            this.ratio = ratio;
        }

        public float getRatio() {
            return this.ratio;
        }
    }
}
