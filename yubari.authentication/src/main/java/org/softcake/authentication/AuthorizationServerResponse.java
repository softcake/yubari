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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.regex.Matcher;

public class AuthorizationServerResponse extends AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerResponse.class);
    private String fastestAPIAndTicket;
    private String rememberMeToken = null;
    private int passwordLength = 0;
    private int detailedStatusCode;

    public AuthorizationServerResponse(AuthorizationServerResponseCode authorizationServerResponseCode) {

        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
    }

    public AuthorizationServerResponse(AuthorizationServerResponseCode authorizationServerResponseCode,
                                       int detailedStatusCode) {

        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
        this.detailedStatusCode = detailedStatusCode;
    }

    public AuthorizationServerResponse(String responseMessage,
                                       AuthorizationServerResponseCode authorizationServerResponseCode) {

        this.responseMessage = responseMessage;
        this.responseCode = authorizationServerResponseCode;
        this.init();
    }

    public AuthorizationServerResponse(String responseMessage,
                                       AuthorizationServerResponseCode authorizationServerResponseCode,
                                       boolean srp6requestWithProperties,
                                       Properties platformProperties) {

        this.responseMessage = responseMessage;
        this.responseCode = authorizationServerResponseCode;
        this.srp6requestWithProperties = srp6requestWithProperties;
        this.platformProperties = platformProperties;
        this.init();
    }

    public AuthorizationServerResponse(String responseMessage, int authorizationServerResponseCode) {

        this.responseMessage = responseMessage;
        this.responseCode = AuthorizationServerResponseCode.fromValue(authorizationServerResponseCode);
        this.init();
    }

    public boolean isEmptyResponse() {

        return this.isOK() && AuthorizationServerResponseCode.EMPTY_RESPONSE == this.responseCode;
    }

    public boolean isTicketExpired() {

        return AuthorizationServerResponseCode.TICKET_EXPIRED == this.responseCode
               || AuthorizationServerResponseCode.MINUS_ONE_OLD_ERROR == this.responseCode;
    }

    public boolean isSystemError() {

        return AuthorizationServerResponseCode.SYSTEM_ERROR == this.responseCode
               || AuthorizationServerResponseCode.SYSTEM_ERROR_OLD == this.responseCode;
    }

    public boolean isWrongVersion() {

        return AuthorizationServerResponseCode.WRONG_VERSION_RESPONSE == this.responseCode
               || AuthorizationServerResponseCode.WRONG_VERSION_RESPONSE_OLD == this.responseCode;
    }

    public boolean isNoAPIServers() {

        return AuthorizationServerResponseCode.SERVICE_UNAVAILABLE == this.responseCode
               || AuthorizationServerResponseCode.SERVICE_UNAVAILABLE_OLD == this.responseCode;
    }

    public boolean isInternalError() {

        return AuthorizationServerResponseCode.INTERNAL_ERROR == this.responseCode;
    }

    public boolean isAuthorizationError() {

        return AuthorizationServerResponseCode.AUTHENTICATION_AUTHORIZATION_ERROR == this.responseCode;
    }

    protected void validateResponse(String authorizationResponse) {

        if (authorizationResponse != null && authorizationResponse.length() != 0) {
            Matcher matcher = AuthorizationClient.RESULT_PATTERN.matcher(authorizationResponse);
            if (!matcher.matches()) {
                LOGGER.error("Authorization procedure returned unexpected result [" + authorizationResponse + "]");
                this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
            }
        } else {
            this.responseCode = AuthorizationServerResponseCode.EMPTY_RESPONSE;
        }

        if (this.responseCode == AuthorizationServerResponseCode.SUCCESS_OK
            && this.srp6requestWithProperties
            && (this.platformProperties == null || this.platformProperties.isEmpty())) {
            this.responseCode = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED;
        }

    }

    public String getFastestAPIAndTicket() {

        return this.fastestAPIAndTicket;
    }

    public void setFastestAPIAndTicket(String fastestAPIAndTicket) {

        this.fastestAPIAndTicket = null;

        try {
            if (fastestAPIAndTicket != null) {
                fastestAPIAndTicket = fastestAPIAndTicket.trim();
            }

            Matcher matcher = AuthorizationClient.RESULT_PATTERN.matcher(fastestAPIAndTicket);
            if (matcher.matches()) {
                this.fastestAPIAndTicket = fastestAPIAndTicket;
            } else {
                LOGGER.error("Wrong fastest API format:", fastestAPIAndTicket);
            }
        } catch (Throwable var3) {
            LOGGER.error("Wrong fastest API format:");
            LOGGER.error(var3.getMessage(), var3);
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

        String message = "Internal error";
        if (this.responseCode == AuthorizationServerResponseCode.AUTHENTICATION_AUTHORIZATION_ERROR
            && this.detailedStatusCode == 1) {
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
