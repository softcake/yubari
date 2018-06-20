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
import com.dukascopy.login.service.account.DeviceIDBean;
import com.dukascopy.login.service.account.UserBean;
import com.dukascopy.login.service.account.storage.api.IAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformAuthUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformAuthUtil.class);

    public PlatformAuthUtil() {

    }

    public static boolean authByRememberMeTokenIsPossible(final String platformEnvironmentAsString,
                                                          final ILoginDialogController loginDialogController) {

        final PlatformRememberMeAuthBean authByRememberMeTokenBean = getAuthByRememberMeTokenBean(platformEnvironmentAsString,
                                                                                                  loginDialogController);
        final boolean authByRememberMeTokenIsPossible = authByRememberMeTokenBean.authByRememberMeTokenIsPossible();
        return authByRememberMeTokenIsPossible;
    }

    public static PlatformRememberMeAuthBean getAuthByRememberMeTokenBean(final String platformEnvironmentAsString,
                                                                          final ILoginDialogController
                                                                              loginDialogController) {

        final PlatformRememberMeAuthBean platformRememberMeAuthBean = new PlatformRememberMeAuthBean();

        try {
            final boolean rememberMeSupported = true; //GreedContext.isRememberMeSupported(platformEnvironmentAsString);
            if (rememberMeSupported) {
                String autoLoginUserName = null;
                String rememberMeToken = null;
                final IAccountService accountService = loginDialogController.getAccountService();


                final PlatformEnvironment platformEnvironment = PlatformEnvironment.fromValue(platformEnvironmentAsString);
                final UserBean userBean = accountService.getLastLoggedInUserAsRememberMe(platformEnvironment);
                final LoginDialogBean loginDialogBean = loginDialogController.getLoginDialogBean();
                final boolean autoLoginFlag = loginDialogBean.isAutologin();
                final boolean autoLoginIgnore = loginDialogBean.isAutoLoginIgnore();
                if (userBean != null) {
                    autoLoginUserName = userBean.getUserName();
                    rememberMeToken = userBean.getRememberMeToken();
                }

                final DeviceIDBean deviceIDBean = accountService.getDeviceIDBean();
                platformRememberMeAuthBean.setAutoLoginIgnore(autoLoginIgnore);
                platformRememberMeAuthBean.setAutoLoginFlag(autoLoginFlag);
                platformRememberMeAuthBean.setAutoLoginUserName(autoLoginUserName);
                platformRememberMeAuthBean.setRememberMeToken(rememberMeToken);
                platformRememberMeAuthBean.setDeviceIDBean(deviceIDBean);
            }
        } catch (final Throwable var13) {
            LOGGER.error(var13.getMessage(), var13);
        }

        return platformRememberMeAuthBean;
    }
}
