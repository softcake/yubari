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

import com.google.common.base.Strings;

public enum AuthorizationServerResponseCode {
    SUCCESS_OK(200, "OK"),
    SUCCESS_CREATED(201, "Created"),
    SUCCESS_ACCEPTED(202, "Accepted"),
    SUCCESS_NONAUTHORITATIVEINFO(203, "Non-Authoritative Information"),
    SUCCESS_NOCONTENT(204, "No Content"),
    SUCCESS_RESETCONTENT(205, "Reset Content"),
    SUCCESS_PARTIALCONTENT(206, "Partial Content"),
    WRONG_REQUEST_ERROR(400, "Missing parameters in request"),
    AUTHENTICATION_AUTHORIZATION_ERROR(401, "Authentication failed. ##Please check your login details."),
    NOT_FOUND(404, "Not Found"),
    AUTHORIZATION_REQUEST_TIMEOUT(408, "Authorization request timeout"),
    WRONG_VERSION_RESPONSE(420, "Invalid application version.##Please launch the platform ## from www.dukascopy.com"),
    WRONG_VERSION_RESPONSE_OLD(-2,
                               "Invalid application version.##Please launch the platform ## from www.dukascopy.com"),
    TICKET_EXPIRED(421, "Ticket expired"),
    ACCOUNT_LOCKED_ERROR(423, "Account locked"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    ORIGIN_NOT_ALLOWED_ERROR(430, "Origin not allowed"),
    SYSTEM_ERROR(500, "Unexpected system error"),
    SYSTEM_ERROR_OLD(-500, "Unexpected system error"),
    SERVICE_UNAVAILABLE(503, "System is offline"),
    SERVICE_UNAVAILABLE_OLD(-3, "System is offline"),
    MINUS_ONE_OLD_ERROR(-1, "Authentication failed. ##Please check your login details."),
    EMPTY_RESPONSE(-4, "Empty response"),
    ERROR_INIT(-5, "Initialization error"),
    BAD_URL(-6, "Bad url"),
    ERROR_IO(-7, "Connection error occurred"),
    WRONG_AUTH_RESPONSE(-8, "Wrong authorization response"),
    INTERNAL_ERROR(-9, "Internal error"),
    AUTHORIZATION_TIMEOUT(-10, "SRP-6a authentication session timeout"),
    BAD_PUBLIC_VALUE(-11, "Invalid public client or server value"),
    BAD_CREDENTIALS(-12, "Invalid credentials"),
    NO_PROPERTIES_RECEIVED(-13, "Cannot receive account settings"),
    SESSION_EXPIRED_AUTH_TOKEN_USED(-14, "Session expired"),
    INTERRUPTED(-15, "Interrupted"),
    UNKNOWN_RESPONSE("Unknown response");

    private int code;
    private String message;

    private AuthorizationServerResponseCode(int code, String message) {

        this.code = code;
        this.message = message;
    }

    private AuthorizationServerResponseCode(String message) {

        this.message = message;
    }

    public static AuthorizationServerResponseCode fromValue(int code) {

        final AuthorizationServerResponseCode[] values = AuthorizationServerResponseCode.values();
        AuthorizationServerResponseCode enumer;
        for (final AuthorizationServerResponseCode value : values) {
            enumer = value;
            if (enumer.code == code) {
                return enumer;
            }
        }

        if (code > 0) {

            for (final AuthorizationServerResponseCode value : values) {
                enumer = value;
                if (enumer.code + AuthorizationClient.ERROR_CODE_OFFSET == code) {
                    enumer.code += AuthorizationClient.ERROR_CODE_OFFSET;
                    return enumer;
                }
            }

        }

        UNKNOWN_RESPONSE.setCode(code);
        return UNKNOWN_RESPONSE;
    }

    public int getCode() {

        return this.code;
    }

    public void setCode(int code) {

        if (this != UNKNOWN_RESPONSE) {
            throw new IllegalStateException("The error code can be set only for UNKNOWN_RESPONSE.");
        } else {
            this.code = code;
        }
    }

    public String getMessage() {

        return this.message;
    }

    public String getError() {

        StringBuffer sb = new StringBuffer(64);
        if (!Strings.isNullOrEmpty(this.message)) {
            sb.append(this.message);
            sb.append(".");
            sb.append(" ");
        }

        sb.append("Error: ");
        sb.append(this.code);
        return sb.toString();
    }
}