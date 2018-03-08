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

import java.util.HashMap;
import java.util.Map;

public class AuthorizationServerPinRequiredResponse extends AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerPinRequiredResponse.class);
    private static final String CHECK_PIN_NAME = "checkPin";
    private static final String WL_PARTNER_ID_NAME = "wlPartnerId";
    private JSONObject authResponseAsJsonObject;
    private Map<String, Object> responseValues = new HashMap();
    private Boolean checkPin = null;
    private Integer wlPartnerId = null;

    public AuthorizationServerPinRequiredResponse(JSONObject authResponseAsJsonObject) {
        this.responseMessage = null;
        this.authResponseAsJsonObject = authResponseAsJsonObject;
        this.init();
    }

    public AuthorizationServerPinRequiredResponse(AuthorizationClient.AuthorizationServerResponseCode authorizationServerResponseCode) {
        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
    }

    public AuthorizationServerPinRequiredResponse(int responseCode, String responseMessage) {
        this.responseCode = AuthorizationClient.AuthorizationServerResponseCode.fromValue(responseCode);
        this.responseMessage = responseMessage;
    }

    protected void init() {
        if (this.authResponseAsJsonObject == null) {
            this.responseCode = AuthorizationClient.AuthorizationServerResponseCode.EMPTY_RESPONSE;
        } else {
            Integer wlPartnerId = null;
            Boolean checkPin = null;

            try {
                if (!this.authResponseAsJsonObject.isNull("checkPin")) {
                    checkPin = this.authResponseAsJsonObject.getBoolean("checkPin");
                }

                if (!this.authResponseAsJsonObject.isNull("wlPartnerId")) {
                    wlPartnerId = this.authResponseAsJsonObject.getInt("wlPartnerId");
                }

                if (checkPin != null) {
                    this.responseValues.put("checkPin", checkPin);
                    this.checkPin = checkPin;
                } else {
                    this.responseCode = AuthorizationClient.AuthorizationServerResponseCode.EMPTY_RESPONSE;
                }

                if (wlPartnerId != null) {
                    this.responseValues.put("wlPartnerId", wlPartnerId);
                    this.wlPartnerId = wlPartnerId;
                }

                if (this.responseValues.get("checkPin") != null) {
                    this.responseCode = AuthorizationClient.AuthorizationServerResponseCode.SUCCESS_OK;
                }
            } catch (Throwable var4) {
                LOGGER.error(var4.getMessage(), var4);
                this.responseCode = AuthorizationClient.AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
            }
        }

    }

    public Boolean getCheckPin() {
        return this.checkPin;
    }

    public Integer getWlPartnerId() {
        return this.wlPartnerId;
    }

    public Map<String, Object> getResponseValues() {
        return this.responseValues;
    }

    protected void validateResponse(String authorizationResponse) {
    }
}
