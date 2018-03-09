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


import static org.softcake.authentication.AuthorizationServerResponseCode.*;

import org.json.JSONException;
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

    public AuthorizationServerWLBOResponse(AuthorizationServerResponseCode code) {
        this.responseMessage = null;
        this.responseCode = code;
    }

    public AuthorizationServerWLBOResponse(String responseMessage, AuthorizationServerResponseCode code) {
        this.responseMessage = null;
        this.responseCode = code;
        this.init();
    }

    public AuthorizationServerWLBOResponse(String responseMessage, int code) {
        this.responseMessage = responseMessage;
        this.responseCode = fromValue(code);
        this.init();
    }

    public String getError() {
        return this.error;
    }

    protected void validateResponse(String response) {
        if (response != null && response.length() != 0) {
            try {

                //TODO  JSONObject jsonObject = new JSONObject(response, false);
                JSONObject jsonObject = new JSONObject(response);
                if (!jsonObject.isNull("error")) {
                    this.error = jsonObject.getString("error");
                    if (this.error != null) {
                        this.error.trim();
                    }
                }

                if (this.error != null && this.error.length() > 0) {
                    LOGGER.error("Error [{}]", response);
                    this.responseCode = WRONG_AUTH_RESPONSE;
                    return;
                }

                this.authTicket = jsonObject.getString(AUTH_TICKET_PARAM_NAME);
                this.sid = jsonObject.getString(SID_PARAM_NAME);
                this.readOnly = jsonObject.getBoolean(READONLY_PARAM_NAME);
                this.authApiURL = jsonObject.getString(AUTH_API_URL_PARAM_NAME);
                this.login = jsonObject.getString(LOGIN_PARAM_NAME);
                if (this.authApiURL != null) {
                    this.authApiURL = this.authApiURL.trim();
                }

                if (this.isEmpty(this.authTicket)) {
                    this.responseCode = WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong authTicket [{}]", response);
                    return;
                }

                if (this.isEmpty(this.sid)) {
                    this.responseCode = WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong sid [{}]", response);
                    return;
                }

                if (this.isEmpty(this.authApiURL)) {
                    this.responseCode = WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong authApiURL [{}]", response);
                    return;
                }

                if (this.isEmpty(this.login)) {
                    this.responseCode = WRONG_AUTH_RESPONSE;
                    LOGGER.error("Wrong login [{}]", response);
                    return;
                }
            } catch (JSONException ex) {
                this.responseCode = WRONG_AUTH_RESPONSE;
                LOGGER.error("Error occurred...", ex);
                LOGGER.error("Cannot parse the authorization answer [{}]", response);
            }
        } else {
            this.responseCode = EMPTY_RESPONSE;
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
