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

public class AuthorizationServerStsTokenResponse extends AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerStsTokenResponse.class);
    private String stsToken;
    private String error;

    public AuthorizationServerStsTokenResponse(AuthorizationServerResponseCode authorizationServerResponseCode) {
        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
    }

    public AuthorizationServerStsTokenResponse(String responseMessage, AuthorizationServerResponseCode authorizationServerResponseCode) {
        this.responseMessage = null;
        this.responseCode = authorizationServerResponseCode;
        this.init();
    }

    public AuthorizationServerStsTokenResponse(String responseMessage, int authorizationServerResponseCode) {
        this.responseMessage = responseMessage;
        this.responseCode = AuthorizationServerResponseCode.fromValue(authorizationServerResponseCode);
        this.init();
    }

    public String getStsToken() {
        return this.stsToken;
    }

    public String getError() {
        return this.error;
    }

    protected void validateResponse(String authorizationResponse) {
        if (authorizationResponse != null && authorizationResponse.length() != 0) {
            try {
                // TODO JSONObject jsonObject = new JSONObject(authorizationResponse, false);
                JSONObject jsonObject = new JSONObject(authorizationResponse);
                this.stsToken = jsonObject.getString("result");
                if (!jsonObject.isNull("error")) {
                    this.error = jsonObject.getString("error");
                    if (this.error != null) {
                        this.error.trim();
                    }
                }

                if (this.stsToken == null || this.stsToken.isEmpty() || this.error != null) {
                    this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                }
            } catch (Throwable var3) {
                LOGGER.error(var3.getMessage(), var3);
                this.responseCode = AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;
                LOGGER.error("Cannot parse STS Token answer [" + authorizationResponse + "]");
            }
        } else {
            this.responseCode = AuthorizationServerResponseCode.EMPTY_RESPONSE;
        }

    }
}
