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

import static org.slf4j.LoggerFactory.getLogger;

import com.google.common.base.Strings;
import org.slf4j.Logger;

import java.util.Properties;

public abstract class AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = getLogger(AbstractAuthorizationServerResponse.class);
    protected String responseMessage;
    protected AuthorizationServerResponseCode responseCode;
    protected Properties platformProperties;
    protected boolean srp6requestWithProperties;

    public AbstractAuthorizationServerResponse() {

    }

    protected abstract void validateResponse(String response);

    public String getResponseMessage() {

        return this.responseMessage;
    }

    public AuthorizationServerResponseCode getResponseCode() {

        return this.responseCode;
    }

    protected void init() {

        if (this.responseMessage != null) {
            this.responseMessage = this.responseMessage.trim();
        }

        if (AuthorizationServerResponseCode.SUCCESS_OK == this.responseCode) {
            if (!Strings.isNullOrEmpty(this.responseMessage)) {
                if (isNotErrorMessage()) {
                    this.validateResponse(this.responseMessage);
                } else {
                    int code = Integer.parseInt(this.responseMessage);
                    this.responseCode = AuthorizationServerResponseCode.fromValue(code);
                }
            } else {
                this.validateResponse(this.responseMessage);
            }
        }
    }

    private boolean isNotErrorMessage() {

        return !this.responseMessage.equals("-1") && !this.responseMessage.equals("-2") && !this.responseMessage.equals(
            "-3") && !this.responseMessage.equals("-500");
    }

    public boolean isOK() {

        return AuthorizationServerResponseCode.SUCCESS_OK == this.responseCode;
    }

    public boolean isSrp6requestWithProperties() {

        return this.srp6requestWithProperties;
    }

    public Properties getPlatformProperties() {

        return this.platformProperties;
    }

    public void printProperties() {

            if (this.platformProperties != null) {
                this.platformProperties.entrySet().stream().map(e -> e.getKey()
                                                                     + getBlanks(e.getKey().toString())
                                                                     + ": "
                                                                     + e.getValue()).forEach(System.out::println);

            } else {
                System.err.println("The platformProperties is null");
            }

    }

    private String getBlanks(final String key) {

        String result = "";
        for (int i = key.length(); i < 50; ++i) {
            result += " ";
        }
        return result;
    }

    public String toString() {

        String message = "no AUTH response";
        if (this.responseMessage != null) {
            message = this.responseMessage;
        }

        if (this.responseCode != null) {
            message = message + " (" + this.responseCode.getCode() + " " + this.responseCode.getMessage() + ")";
        }

        return message;
    }
}
