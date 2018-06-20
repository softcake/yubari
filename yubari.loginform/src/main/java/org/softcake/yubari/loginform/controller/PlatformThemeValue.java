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

import org.softcake.yubari.loginform.localization.LocalizationManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum PlatformThemeValue {
    DEFAULT_THEME("menu.item.default"),
    DARK_THEME("menu.item.dark");

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformThemeValue.class);
    private String translationKey;

    private PlatformThemeValue(String translationKey) {
        this.translationKey = translationKey;
    }

    public static PlatformThemeValue fromValue(String valueParam) {
        PlatformThemeValue[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PlatformThemeValue enumer = var1[var3];
            if (enumer.name().equalsIgnoreCase(valueParam)) {
                return enumer;
            }
        }

        LOGGER.error("Invalid param: " + valueParam + ", default value (Default Theme) will be used.");
        return DEFAULT_THEME;
    }

    public String toString() {
        return LocalizationManager.getText(this.translationKey);
    }
}
