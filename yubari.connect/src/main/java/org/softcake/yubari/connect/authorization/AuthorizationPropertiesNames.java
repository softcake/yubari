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
 * The constants for authorization properties.
 *
 * @author Rene Neubert
 */
public final class AuthorizationPropertiesNames {


    public static final String JNLP_LOGIN_URL = "jnlp.login.url";
    public static final String JNLP_SRP6_LOGIN_URL = "jnlp.srp6.login.url";
    public static final String JNLP_CLIENT_MODE = "jnlp.client.mode";
    public static final String JNLP_CLIENT_VERSION = "jnlp.client.version";
    public static final String JAVA_NET_PREFER_IPV4_STACK = "java.net.preferIPv4Stack";
    public static final String DEMO = "DEMO";
    public static final String LIVE = "LIVE";

    private AuthorizationPropertiesNames() {

        throw new IllegalAccessError("Utility class");
    }
}
