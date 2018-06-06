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
import com.dukascopy.login.controller.LoginDialogMode;
import com.dukascopy.login.controller.PlatformEnvironment;
import com.dukascopy.login.service.account.storage.api.IAccountService;
import com.dukascopy.login.settings.CurrentLoginFormSettins;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginFormCloseListener extends WindowAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginFormCloseListener.class);
    private ILoginDialogController loginDialogController;

    public LoginFormCloseListener(ILoginDialogController loginDialogController) {
        this.loginDialogController = loginDialogController;
    }

    public void windowClosing(WindowEvent e) {
        LoginDialogBean loginDialogBean = null;

        try {
            loginDialogBean = this.loginDialogController.getLoginDialogBean();
            LoginDialogMode loginDialogMode = this.loginDialogController.getLoginDialogMode();
            CurrentLoginFormSettins loginFormSettins = new CurrentLoginFormSettins();
            loginFormSettins.saveProperties(loginDialogBean, loginDialogMode);
        } catch (Throwable var10) {
            LOGGER.error(var10.getMessage(), var10);
        }

        boolean rememberMeSupported = false;
        PlatformEnvironment platformEnvironment = null;

        try {
            platformEnvironment = loginDialogBean.getPlatformEnvironment();
            rememberMeSupported = this.loginDialogController.isRememberMeSupported(platformEnvironment.getEnvironment());
        } catch (Throwable var9) {
            LOGGER.error(var9.getMessage(), var9);
        }

        if (rememberMeSupported) {
            try {
                String currentLoginName = loginDialogBean.getLoginName();
                if (currentLoginName != null && !currentLoginName.isEmpty()) {
                    boolean rememberMe = loginDialogBean.isRememberMe();
                    if (!rememberMe) {
                        IAccountService accountService = this.loginDialogController.getAccountService();
                        accountService.deleteRememberMeToken(platformEnvironment, currentLoginName);
                    }
                }
            } catch (Throwable var8) {
                LOGGER.error(var8.getMessage(), var8);
            }
        }

        System.exit(0);
    }
}
