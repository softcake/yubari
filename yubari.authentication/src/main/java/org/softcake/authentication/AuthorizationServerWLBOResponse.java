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

package org.softcake.authentication;


import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationServerWLBOResponse extends AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerStsTokenResponse.class);
    private static final String AUTH_TICKET_PARAM_NAME = "authTicket";
    private static final String SID_PARAM_NAME = "sid";
    private static final String READONLY_PARAM_NAME = "readOnly";
    private static final String AUTH_API_URL_PARAM_NAME = "authApiURL";
    private static final String LOGIN_PARAM_NAME = "login";
    private String authTicket;
    private String sid;
    private boolean readOnly;
    private String authApiURL;
    private String login;
    private String error;

    public AuthorizationServerWLBOResponse(AuthorizationServerResponseCode authorizationServerResponseCode) {
        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
    }

    public AuthorizationServerWLBOResponse(String responseMessage, AuthorizationServerResponseCode authorizationServerResponseCode) {
        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
        this.init();
    }

    public AuthorizationServerWLBOResponse(String responseMessage, int authorizationServerResponseCode) {
        this.responseMessage = responseMessage;
        this.responseCode = AuthorizationServerResponseCode.fromValue(authorizationServerResponseCode);
        this.init();
    }

    public String getError() {
        return this.error;
    }

    protected void validateResponse(String authorizationResponse) {
        if (authorizationResponse != null && authorizationResponse.length() != 0) {
            try {

                //TODO  JSONObject jsonObject = new JSONObject(authorizationResponse, false);
                JSONObject jsonObject = new JSONObject(authorizationResponse);
                if (!jsonObject.isNull("error")) {
                    this.error = jsonObject.getString("error");
                    if (this.error != null) {
                        this.error.trim();
                    }
                }

                if (this.error != null && this.error.length() > 0) {
                    LOGGER.error("Error [" + authorizationResponse + "]");
                    this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                    return;
                }

                this.authTicket = jsonObject.getString(AUTH_TICKET_PARAM_NAME);
                this.sid = jsonObject.getString(SID_PARAM_NAME);
                this.readOnly = jsonObject.getBoolean(READONLY_PARAM_NAME);
                this.authApiURL = jsonObject.getString("authApiURL");
                this.login = jsonObject.getString("login");
                if (this.authApiURL != null) {
                    this.authApiURL = this.authApiURL.trim();
                }

                if (this.isEmpty(this.authTicket)) {
                    this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong authTicket [" + authorizationResponse + "]");
                    return;
                }

                if (this.isEmpty(this.sid)) {
                    this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong sid [" + authorizationResponse + "]");
                    return;
                }

                if (this.isEmpty(this.authApiURL)) {
                    this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong authApiURL [" + authorizationResponse + "]");
                    return;
                }

                if (this.isEmpty(this.login)) {
                    this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong login [" + authorizationResponse + "]");
                    return;
                }
            } catch (Throwable var3) {
                LOGGER.error(var3.getMessage(), var3);
                this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                LOGGER.error("Cannot parse the authorization answer [" + authorizationResponse + "]");
            }
        } else {
            this.responseCode = AuthorizationServerResponseCode.EMPTY_RESPONSE;
        }

    }

    private boolean isEmpty(String message) {
        if (message != null) {
            message = message.trim();
        }

        return message == null || message.isEmpty();
    }

    public String getAuthTicket() {
        return this.authTicket;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public String getAuthApiURL() {
        return this.authApiURL;
    }

    public String getLogin() {
        return this.login;
    }

    public String getSid() {
        return this.sid;
    }
}
