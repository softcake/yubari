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

import org.softcake.yubari.loginform.service.IAppearanceThemeManager;

import java.awt.*;
import java.util.Locale;

public class WikiParametersBean {
    private String absoluteUrl;
    private String baseUrl;
    private String partOfUrl;
    private Window window;
    private boolean modal;
    private Locale locale;
    private float ratio;

    public WikiParametersBean() {
        this.ratio = IAppearanceThemeManager.ZoomMode.STANDARD.getRatio();
    }

    public WikiParametersBean withAbsoluteUrl(String absoluteUrl) {
        this.absoluteUrl = absoluteUrl;
        return this;
    }

    public WikiParametersBean withBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return this;
    }

    public WikiParametersBean withPartOfUrl(String partOfUrl) {
        this.partOfUrl = partOfUrl;
        return this;
    }

    public WikiParametersBean withComponent(Window window) {
        this.window = window;
        return this;
    }

    public WikiParametersBean withModal(boolean modal) {
        this.modal = modal;
        return this;
    }

    public WikiParametersBean withLanguage(Locale locale) {
        this.locale = locale;
        return this;
    }

    public WikiParametersBean withRatio(float ratio) {
        this.ratio = ratio;
        return this;
    }

    public String getAbsoluteUrl() {
        return this.absoluteUrl;
    }

    public boolean isModal() {
        return this.modal;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public String getPartOfUrl() {
        return this.partOfUrl;
    }

    public Locale getLocale() {
        return this.locale;
    }

    public Window getWindow() {
        return this.window;
    }

    public float getRatio() {
        return this.ratio;
    }
}
