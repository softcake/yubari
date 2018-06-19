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

import org.softcake.authentication.AuthorizationClient;
import org.softcake.authentication.AuthorizationServerResponse;
import org.softcake.authentication.AuthorizationServerResponseCode;

import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.ILoginDialogController.LoginDialogState;
import com.dukascopy.login.controller.LoginDialogBean;
import com.dukascopy.login.service.account.DeviceIDBean;
import com.dukascopy.login.service.account.storage.api.IAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreedConnectionUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GreedConnectionUtils.class);

    public GreedConnectionUtils() {
    }

    public static AuthorizationServerResponse getTicketAndAPIUrl(ILoginDialogController controller, String captchaId, String pin, Boolean rememberMe, DeviceIDBean deviceIDBean, String rememberMeToken) {
        String accountName =  controller.getLoginDialogBean().getLoginName();
        String sessionId = System.getProperty("SESSION_ID");
        String password = controller.getLoginDialogBean().getPassword();
        AuthorizationServerResponse authorizationServerResponse = null;

        assert pin != null && pin.trim().length() > 0;

        AuthorizationClient ac = AuthorizationClient.getInstance();
        String platformType = "DDS3_JFOREX";
        String deviceId = null;
        String deviceName = null;
        String deviceOS = null;
        if (deviceIDBean != null) {
            deviceId = deviceIDBean.getDeviceId();
            deviceName = deviceIDBean.getDeviceName();
            deviceOS = deviceIDBean.getDeviceOS();
        }

        //TODO
        if (!"SRP".isEmpty()) {
            authorizationServerResponse =ac.getAPIsAndTicketUsingLogin_SRP6(accountName, password, captchaId, pin, sessionId, platformType, rememberMe, deviceId, rememberMeToken, deviceName, deviceOS);
        } else if (captchaId != null && !captchaId.trim().isEmpty() && pin != null && !pin.trim().isEmpty()) {
          //  authorizationServerResponse = ac.getAPIsAndTicketUsingLogin(accountName, password, captchaId, pin, sessionId, platformType);
        } else {
           // authorizationServerResponse = ac.getAPIsAndTicketUsingLogin(accountName, password, sessionId, platformType);
        }

        return authorizationServerResponse;
    }

    public static AuthorizationServerResponse getTicketAndAPIUrl(ILoginDialogController controller) {
        return getTicketAndAPIUrl(controller, null, null, null, null, null);
    }

    public static AuthorizationServerResponse getTicketAndAPIUrl(ILoginDialogController controller,String userName, DeviceIDBean deviceIDBean, String rememberMeToken) {
        return getTicketAndAPIUrl(controller, null, null, true, deviceIDBean, rememberMeToken);
    }

    public static void wrongAuth(ILoginDialogController loginDialogController, AuthorizationServerResponse authorizationServerResponse) {
        try {

            loginDialogController.setVisible(true);
            loginDialogController.setLoginDialogState(LoginDialogState.AUTHORIZATION_READY);
        } catch (Throwable var8) {
            LOGGER.error(var8.getMessage(), var8);
        }

        try {
            if (authorizationServerResponse.getResponseCode() == AuthorizationServerResponseCode.AUTHENTICATION_AUTHORIZATION_ERROR) {
                String platformEnvironment = loginDialogController.getLoginDialogBean().getPlatformEnvironment().getEnvironment(); //System.getProperty("jnlp.client.mode");
                boolean rememberMeSupported = loginDialogController.isRememberMeSupported(platformEnvironment);
                if (rememberMeSupported) {

                    IAccountService accountService = loginDialogController.getAccountService();
                    String userName = loginDialogController.getLoginDialogBean().getLoginName();
                    accountService.deleteRememberMeToken(platformEnvironment, userName);
                    LoginDialogBean loginDialogBean = loginDialogController.getLoginDialogBean();
                    if (loginDialogBean.isFictivePassword()) {
                        loginDialogController.setPassword("");
                    }
                }
            }
        } catch (Throwable var7) {
            LOGGER.error(var7.getMessage(), var7);
        }

       // GreedContext.publishEvent(new AuthFailedAction(authorizationServerResponse));
    }

    public static void resolveDnsAheadOfTheTime() {
      /*  try {
            Properties properties = (Properties)GreedContext.get("properties");
            URL url = new URL(properties.getProperty("services1.url"));
            HostResolver hr = new HostResolver(url.getHost());
            hr.start();
        } catch (Exception var3) {
            LOGGER.warn("GET to services1.url failed:" + var3.getMessage());
        }*/

    }
}
