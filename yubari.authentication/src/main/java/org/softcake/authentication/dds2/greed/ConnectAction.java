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

import org.softcake.authentication.AuthorizationServerResponse;
import org.softcake.authentication.AuthorizationServerResponseCode;

import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.ILoginDialogController.LoginDialogState;
import com.dukascopy.login.controller.IPlatformAuthorizationController;
import com.dukascopy.login.service.account.DeviceIDBean;
import com.dukascopy.login.service.account.storage.api.IAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectAction  {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectAction.class);
    private final ILoginDialogController controller;
    private final String pin;
    private String captchaId;
    private Boolean rememberMe = null;
    private DeviceIDBean deviceIDBean = null;
    String rememberMeToken = null;

    public ConnectAction(ILoginDialogController controller, String captchaId, String pin, Boolean rememberMe, DeviceIDBean deviceIDBean, String rememberMeToken) {

        this.controller = controller;
        this.pin = pin;
        this.captchaId = captchaId;
        this.rememberMe = rememberMe;
        this.deviceIDBean = deviceIDBean;
        this.rememberMeToken = rememberMeToken;
    }

    public void updateGuiBefore() {
    }

    public void doAction() {
        //NotificationUtils.postConnectMessage(this, ConnectMessageType.AUTHENTICATING);
        this.ticketAuth();
    }

    public void ticketAuth() {
        (new Thread() {
            public void run() {
                AuthorizationServerResponse authorizationServerResponse = null;
                boolean isRememberMeAuthorization = false;
                boolean isRememberMeTokenRequest = false;

                try {
                    isRememberMeAuthorization = ConnectAction.this.rememberMeToken != null && !ConnectAction.this.rememberMeToken.isEmpty() && ConnectAction.this.rememberMe != null && ConnectAction.this.rememberMe && ConnectAction.this.deviceIDBean != null && !ConnectAction.this.deviceIDBean.isEmpty();
                    isRememberMeTokenRequest = ConnectAction.this.rememberMe != null && ConnectAction.this.rememberMe && ConnectAction.this.deviceIDBean != null && !ConnectAction.this.deviceIDBean.isEmpty();
                    authorizationServerResponse = GreedConnectionUtils.getTicketAndAPIUrl(controller,ConnectAction.this.captchaId, ConnectAction.this.pin, ConnectAction.this.rememberMe, ConnectAction.this.deviceIDBean, ConnectAction.this.rememberMeToken);




                } catch (Throwable var11) {
                    ConnectAction.LOGGER.error(var11.getMessage(), var11);
                    authorizationServerResponse = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                    GreedConnectionUtils.wrongAuth(controller, authorizationServerResponse);
                    return;
                }

                IPlatformAuthorizationController platformAuthorizationController = controller.getPlatformAuthorizationController();
                if (isRememberMeAuthorization || isRememberMeTokenRequest) {
                    if (authorizationServerResponse.isOK()) {
                       // GreedContext.setAuthorizedAsRememberMe(true);
                        ConnectAction.this.saveRememberMeToken(authorizationServerResponse);
                    } else {
                        ConnectAction.this.deleteRememberMeToken(authorizationServerResponse);
                    }
                }

                if (platformAuthorizationController.isCanceling()) {
                   // GreedContext.publishEvent(new DisconnectAction(this, DisconnectActionType.CANCEL_LOGIN_PROCESS));
                } else if (!authorizationServerResponse.isOK()) {
                    GreedConnectionUtils.wrongAuth(controller, authorizationServerResponse);
                } else {
                    String httpResponse = authorizationServerResponse.getFastestAPIAndTicket();
                    String[] urlAndTicket = httpResponse.split("@");
                    ILoginDialogController loginDialogController = null;

                    try {
                        loginDialogController = controller; //GreedContext.getLoginDialogController();
                        loginDialogController.setLoginDialogState(LoginDialogState.AUTHORIZATION_CANCEL_DISABLE);
                       // GreedContext.publishEvent(new PlatformInitAction(this, urlAndTicket[1], urlAndTicket[0], authorizationServerResponse));
                    } catch (NoSuchMethodError var9) {
                        ConnectAction.LOGGER.error(var9.getMessage(), var9);
                        loginDialogController.addErrorMessage("platform.is.not.compatible");
                        loginDialogController.setCancelMode(false);
                    } catch (Exception var10) {
                        ConnectAction.LOGGER.error(var10.getMessage(), var10);
                    }

                }
            }
        }).start();
    }

    private void saveRememberMeToken(AuthorizationServerResponse authorizationServerResponse) {
        try {
            //TODO ???
            String platformEnvironment = controller.getLoginDialogBean().getPlatformEnvironment().getEnvironment(); //System.getProperty("jnlp.client.mode");
            boolean rememberMeSupported = controller.isRememberMeSupported(platformEnvironment);
            if (rememberMeSupported) {
                boolean isRememberMeRequest = this.rememberMe != null && this.rememberMe && this.deviceIDBean != null && !this.deviceIDBean.isEmpty() && this.rememberMeToken == null;
                boolean isRememberMeAuth = this.rememberMe != null && this.rememberMe && this.deviceIDBean != null && !this.deviceIDBean.isEmpty() && this.rememberMeToken != null && !this.rememberMeToken.isEmpty();
                if (isRememberMeRequest || isRememberMeAuth) {
                    String rememberMeToken = authorizationServerResponse.getRememberMeToken();
                    int passwordLength = authorizationServerResponse.getPasswordLength();

                    IAccountService accountService = controller.getAccountService();
                    String userName = controller.getLoginDialogBean().getLoginName();
                    accountService.saveAuthorizedUser(platformEnvironment, userName, this.rememberMe, rememberMeToken, passwordLength);
                }
            }
        } catch (Throwable var11) {
            LOGGER.error("Cannot save the RememberMe token");
            LOGGER.error(var11.getMessage(), var11);
        }

    }

    private void deleteRememberMeToken(AuthorizationServerResponse authorizationServerResponse) {
        try {
            String platformEnvironment = controller.getLoginDialogBean().getPlatformEnvironment().getEnvironment(); //System.getProperty("jnlp.client.mode");
            boolean rememberMeSupported = controller.isRememberMeSupported(platformEnvironment);
            if (rememberMeSupported) {
                String accountName = controller.getLoginDialogBean().getLoginName();
                if (accountName == null || accountName.isEmpty()) {
                    throw new IllegalArgumentException("The accountName is absent or empty");
                }


                IAccountService accountService = controller.getAccountService();
                boolean success = accountService.deleteRememberMeToken(platformEnvironment, accountName);
                if (!success) {
                    LOGGER.error("Cannot delete the RememberMeToken token");
                }
            }
        } catch (Throwable var8) {
            LOGGER.error(var8.getMessage(), var8);
        }

    }
}
