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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.regex.Matcher;

public class AuthorizationServerResponse extends AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerResponse.class);
    private String fastestAPIAndTicket;
    private String rememberMeToken;
    private int passwordLength;
    private int detailedStatusCode;

    public AuthorizationServerResponse(AuthorizationServerResponseCode code) {

        this.responseMessage = null;
        this.responseCode = code;
    }

    public AuthorizationServerResponse(AuthorizationServerResponseCode code, int detailedStatusCode) {

        this.responseMessage = null;
        this.responseCode = code;
        this.detailedStatusCode = detailedStatusCode;
    }

    public AuthorizationServerResponse(String responseMessage, AuthorizationServerResponseCode code) {

        this.responseMessage = responseMessage;
        this.responseCode = code;
        this.init();
    }

    public AuthorizationServerResponse(String responseMessage,
                                       AuthorizationServerResponseCode code,
                                       boolean srp6requestWithProperties,
                                       Properties platformProperties) {

        this.responseMessage = responseMessage;
        this.responseCode = code;
        this.srp6requestWithProperties = srp6requestWithProperties;
        this.platformProperties = platformProperties;
        this.init();
    }

    public AuthorizationServerResponse(String responseMessage, int code) {

        this.responseMessage = responseMessage;
        this.responseCode = fromValue(code);
        this.init();
    }

    public boolean isEmptyResponse() {

        return this.isOK() && EMPTY_RESPONSE == this.responseCode;
    }

    public boolean isTicketExpired() {

        return TICKET_EXPIRED == this.responseCode || MINUS_ONE_OLD_ERROR == this.responseCode;
    }

    public boolean isSystemError() {

        return SYSTEM_ERROR == this.responseCode || SYSTEM_ERROR_OLD == this.responseCode;
    }

    public boolean isWrongVersion() {

        return WRONG_VERSION_RESPONSE == this.responseCode || WRONG_VERSION_RESPONSE_OLD == this.responseCode;
    }

    public boolean isNoAPIServers() {

        return SERVICE_UNAVAILABLE == this.responseCode || SERVICE_UNAVAILABLE_OLD == this.responseCode;
    }

    public boolean isInternalError() {

        return INTERNAL_ERROR == this.responseCode;
    }

    public boolean isAuthorizationError() {

        return AUTHENTICATION_AUTHORIZATION_ERROR == this.responseCode;
    }

    protected void validateResponse(String response) {

        if (response != null && response.length() != 0) {
            Matcher matcher = AuthorizationClient.RESULT_PATTERN.matcher(response);
            if (!matcher.matches()) {
                LOGGER.error("Authorization procedure returned unexpected result [{}]", response);
                this.responseCode = WRONG_AUTH_RESPONSE;
            }
        } else {
            this.responseCode = EMPTY_RESPONSE;
        }

        if (this.responseCode == SUCCESS_OK && this.srp6requestWithProperties && (this.platformProperties == null
                                                                                  || this.platformProperties.isEmpty
            ())) {
            this.responseCode = NO_PROPERTIES_RECEIVED;
        }

    }

    public String getFastestAPIAndTicket() {

        return this.fastestAPIAndTicket;
    }

    public void setFastestAPIAndTicket(String fastestAPIAndTicket) {

        this.fastestAPIAndTicket = null;


        if (fastestAPIAndTicket != null) {
            fastestAPIAndTicket = fastestAPIAndTicket.trim();
        } else {
            fastestAPIAndTicket = "";
        }

        Matcher matcher = AuthorizationClient.RESULT_PATTERN.matcher(fastestAPIAndTicket);
        if (matcher.matches()) {
            this.fastestAPIAndTicket = fastestAPIAndTicket;
        } else {
            LOGGER.error("Wrong fastest API format: {}", fastestAPIAndTicket);
        }

    }

    public int getDetailedStatusCode() {

        return this.detailedStatusCode;
    }

    public void setDetailedStatusCode(int detailedStatusCode) {

        this.detailedStatusCode = detailedStatusCode;
    }

    public boolean isDetailedStatusCodeExists() {

        return this.detailedStatusCode != 0;
    }

    public String getMessage() {

        String message;
        if (this.responseCode == AUTHENTICATION_AUTHORIZATION_ERROR && this.detailedStatusCode == 1) {
            message = "Password has Expired";
        } else {
            message = this.responseCode.getMessage();
        }

        return message;
    }

    public String getRememberMeToken() {

        return this.rememberMeToken;
    }

    public void setRememberMeToken(String rememberMeToken) {

        this.rememberMeToken = rememberMeToken;
    }

    public int getPasswordLength() {

        return this.passwordLength;
    }

    public void setPasswordLength(int passwordLength) {

        this.passwordLength = passwordLength;
    }
}
