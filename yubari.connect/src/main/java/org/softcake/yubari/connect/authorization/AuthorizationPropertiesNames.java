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

package org.softcake.yubari.connect.authorization;

/**
 * The enum constants for authorization properties.
 *
 * @author Rene Neubert
 */
public enum AuthorizationPropertiesNames {


    JNLP_LOGIN_URL("jnlp.login.url"),
    JNLP_SRP6_LOGIN_URL("jnlp.srp6.login.url"),
    JNLP_CLIENT_MODE("jnlp.client.mode"),
    JNLP_CLIENT_VERSION("jnlp.client.version"),
    JAVA_NET_PREFER_IPV4_STACK("java.net.preferIPv4Stack");


    private final String value;

    AuthorizationPropertiesNames(final String value) {

        this.value = value;
    }

    /**
     * The String representation of the AuthorizationPropertiesNames Enum.
     *
     * @return the AuthorizationPropertiesNames as String
     */
    public String getValue() {

        return value;
    }
}
