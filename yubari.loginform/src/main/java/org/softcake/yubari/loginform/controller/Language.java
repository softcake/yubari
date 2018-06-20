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

import java.util.Locale;

public enum Language {
    PERSIAN(new Locale("fa"), "short.menu.item.lang.farsi", "menu.item.lang.farsi"),
    ARABIC(new Locale("ar"), "short.menu.item.lang.arabic", "menu.item.lang.arabic"),
    AZERBAIJAN(new Locale("az"), "short.menu.item.lang.azerbaijan", "menu.item.lang.azerbaijan"),
    BULGARIAN(new Locale("bg"), "short.menu.item.lang.bulgarian", "menu.item.lang.bulgarian"),
    CHINESE(Locale.CHINESE, "short.menu.item.lang.china", "menu.item.lang.china"),
    CZECH(new Locale("cs"), "short.menu.item.lang.czech", "menu.item.lang.czech"),
    ENGLISH(Locale.ENGLISH, "short.menu.item.lang.english", "menu.item.lang.english"),
    FRENCH(Locale.FRENCH, "short.menu.item.lang.french", "menu.item.lang.french"),
    GERMAN(Locale.GERMAN, "short.menu.item.lang.german", "menu.item.lang.german"),
    HUNGARIAN(new Locale("hu"), "short.menu.item.lang.hungarian", "menu.item.lang.hungarian"),
    ITALIAN(Locale.ITALIAN, "short.menu.item.lang.italian", "menu.item.lang.italian"),
    JAPANESE(Locale.JAPANESE, "short.menu.item.lang.japanese", "menu.item.lang.japanese"),
    POLISH(new Locale("pl"), "short.menu.item.lang.polish", "menu.item.lang.polish"),
    PORTUGUESE(new Locale("pt"), "short.menu.item.lang.portuguese", "menu.item.lang.portuguese"),
    ROMANIAN(new Locale("ro"), "short.menu.item.lang.romanian", "menu.item.lang.romanian"),
    RUSSIAN(new Locale("ru"), "short.menu.item.lang.russian", "menu.item.lang.russian"),
    SPANISH(new Locale("es"), "short.menu.item.lang.spanish", "menu.item.lang.spanish"),
    TURKISH(new Locale("tr"), "short.menu.item.lang.turkish", "menu.item.lang.turkish"),
    GEORGIAN(new Locale("ka"), "short.menu.item.lang.georgian", "menu.item.lang.georgian"),
    UKRAINIAN(new Locale("uk"), "short.menu.item.lang.ukrainian", "menu.item.lang.ukrainian"),
    KOREAN(new Locale("ko"), "short.menu.item.lang.korean", "menu.item.lang.korean");

    private final Locale locale;
    private final String shortKey;
    private final String longKey;

    private Language(Locale locale, String shortKey, String longKey) {
        this.locale = locale;
        this.shortKey = shortKey;
        this.longKey = longKey;
    }

    public Locale getLocale() {
        return this.locale;
    }

    public String getShortKey() {
        return this.shortKey;
    }

    public String getLongKey() {
        return this.longKey;
    }

    public String toString() {
        return LocalizationManager.getTextForLang(ENGLISH, this.shortKey);
    }
}
