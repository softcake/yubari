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

import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

public abstract class AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = getLogger(AbstractAuthorizationServerResponse.class);
    protected String responseMessage = null;
    protected AuthorizationServerResponseCode responseCode = null;
    protected Properties platformProperties;
    protected boolean srp6requestWithProperties = false;

    public AbstractAuthorizationServerResponse() {
    }

    protected abstract void validateResponse(String var1);

    public String getResponseMessage() {
        return this.responseMessage;
    }

    public AuthorizationServerResponseCode getResponseCode() {
        return this.responseCode;
    }

    protected void init() {
        if (this.responseMessage != null) {
            this.responseMessage.trim();
        }

        if (AuthorizationServerResponseCode.SUCCESS_OK == this.responseCode) {
            if (this.responseMessage != null && this.responseMessage.length() > 0) {
                if (!this.responseMessage.equals("-1") && !this.responseMessage.equals("-2") && !this.responseMessage.equals("-3") && !this.responseMessage.equals("-500")) {
                    this.validateResponse(this.responseMessage);
                } else {
                    int responseCode = Integer.parseInt(this.responseMessage);
                    this.responseCode = AuthorizationServerResponseCode.fromValue(responseCode);
                }
            } else {
                this.validateResponse(this.responseMessage);
            }
        }

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
        try {
            if (this.platformProperties != null) {
                Set<Entry<Object, Object>> entrySet = this.platformProperties.entrySet();
                Iterator var2 = entrySet.iterator();

                while(var2.hasNext()) {
                    Entry<Object, Object> entry = (Entry)var2.next();
                    String key = (String)entry.getKey();
                    int length = key.length();
                    System.out.print(entry.getKey());

                    for(int i = length; i < 40; ++i) {
                        System.out.print(" ");
                    }

                    System.out.println(entry.getValue());
                }
            } else {
                System.err.println("The platformProperties is null");
            }
        } catch (Throwable var7) {
            LOGGER.error(var7.getMessage(), var7);
        }

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
