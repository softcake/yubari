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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Properties;

public class AuthorizationServerPropertiesResponse extends AbstractAuthorizationServerResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerPropertiesResponse.class);

    public AuthorizationServerPropertiesResponse(AuthorizationServerResponseCode authorizationServerResponseCode) {
        this.responseCode = authorizationServerResponseCode;
        this.responseMessage = null;
        this.init();
    }

    public AuthorizationServerPropertiesResponse(int authorizationServerResponseCode, String platformPropertiesAsString) {
        this.responseCode = AuthorizationServerResponseCode.fromValue(authorizationServerResponseCode);
        this.responseMessage = platformPropertiesAsString;
        this.init();
    }

    protected void validateResponse(String authorizationResponse) {
        if (authorizationResponse != null && authorizationResponse.length() != 0) {
            try {
                byte[] decodedBytes = Base64.decode(authorizationResponse);
                if (decodedBytes == null || decodedBytes.length == 0) {
                    this.responseCode = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED;
                    return;
                }

                ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(decodedBytes));
                Object propertiesAsObject = inputStream.readObject();
                Properties properties = (Properties)propertiesAsObject;
                if (properties.isEmpty()) {
                    this.responseCode = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED;
                    return;
                }

                this.platformProperties = properties;
            } catch (Throwable var6) {
                this.responseCode = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED;
                LOGGER.error(var6.getMessage(), var6);
            }

        } else {
            this.responseCode = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED;
        }
    }
}
