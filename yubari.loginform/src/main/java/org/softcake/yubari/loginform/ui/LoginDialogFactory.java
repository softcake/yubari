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

package org.softcake.yubari.loginform.ui;

import org.softcake.yubari.loginform.controller.ILoginDialogController;
import org.softcake.yubari.loginform.controller.LoginDialogBean;
import org.softcake.yubari.loginform.controller.LoginDialogMode;
import org.softcake.yubari.loginform.resources.IResourcesProvider;
import org.softcake.yubari.loginform.ui.helper.AWTThreadExecutor;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginDialogFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginDialogFactory.class);
    private static LoginForm loginForm = null;

    public LoginDialogFactory() {
    }

    public static ILoginDialogController create() {
        (new AWTThreadExecutor() {
            public void invoke() {
                try {
                    LoginDialogFactory.loginForm = new LoginForm();
                } catch (Exception var2) {
                    LoginDialogFactory.LOGGER.error(var2.getMessage(), var2);
                }

            }
        }).executeAndWait();
        return loginForm.getLoginDialogController();
    }

    public static ILoginDialogController createWebStartVersion(IResourcesProvider resourcesProvider, LoginDialogBean loginDialogBean) {
        return create(LoginDialogMode.WEB_START, resourcesProvider, loginDialogBean, (Properties)null);
    }

    public static ILoginDialogController createStandAloneInstallationVersion(IResourcesProvider resourcesProvider, LoginDialogBean loginDialogBean) {
        return create(LoginDialogMode.STAND_ALONE, resourcesProvider, loginDialogBean, (Properties)null);
    }

    public static ILoginDialogController createStandAloneInstallationVersion(IResourcesProvider resourcesProvider, LoginDialogBean loginDialogBean, Properties properties) {
        return create(LoginDialogMode.STAND_ALONE, resourcesProvider, loginDialogBean, properties);
    }

    public static ILoginDialogController createStandAloneInstallationVersion() {
        return create(LoginDialogMode.STAND_ALONE, (IResourcesProvider)null, (LoginDialogBean)null, (Properties)null);
    }

    private static ILoginDialogController create(final LoginDialogMode loginDialogMode, final IResourcesProvider resourcesProvider, final LoginDialogBean loginDialogBean, final Properties properties) {
        (new AWTThreadExecutor() {
            public void invoke() {
                try {
                    LoginDialogController loginDialogController = new LoginDialogController();
                    if (loginDialogMode == LoginDialogMode.STAND_ALONE && properties != null) {
                        String demoBetaUrl = (String)properties.get("DEMO_3");
                        if (demoBetaUrl != null) {
                            demoBetaUrl = demoBetaUrl.trim();
                            if (!demoBetaUrl.isEmpty()) {
                                loginDialogMode.setDemoAndDemoBeta(true);
                            }
                        }

                        String liveBetaUrl = (String)properties.get("LIVE_3");
                        if (liveBetaUrl != null) {
                            liveBetaUrl = liveBetaUrl.trim();
                            if (!liveBetaUrl.isEmpty()) {
                                loginDialogMode.setLiveAndLiveBeta(true);
                            }
                        }
                    }

                    if (resourcesProvider != null) {
                        loginDialogController.setResourcesProvider(resourcesProvider);
                    }

                    loginDialogController.setLoginDialogMode(loginDialogMode);
                    if (loginDialogBean != null) {
                        loginDialogController.setLoginDialogBean(loginDialogBean, false);
                    }

                    LoginDialogFactory.loginForm = new LoginForm(loginDialogController);
                    if (loginDialogBean != null) {
                        loginDialogController.setLoginDialogBean(loginDialogBean);
                    }
                } catch (Exception var4) {
                    LoginDialogFactory.LOGGER.error(var4.getMessage(), var4);
                }

            }
        }).executeAndWait();
        return loginForm.getLoginDialogController();
    }
}
