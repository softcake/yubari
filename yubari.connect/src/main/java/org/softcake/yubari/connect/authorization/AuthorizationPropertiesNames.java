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

   /* JNLP_PLATFORM_LOGO_URL("jnlp.platform.logo.url"),
    JNLP_COMPANY_LOGO_URL("jnlp.company.logo.url"),*/
    JNLP_PLATFORM_MODE("jnlp.platform.mode"),
    JNLP_CLIENT_VERSION("jnlp.client.version"),
    JNLP_CLIENT_MODE("jnlp.client.mode"),
    JNLP_PACK_ENABLED("jnlp.packEnabled"),
    SUN_JAVA2D_D3D("sun.java2d.d3d"),
    JNLP_PLATFORM_NAME("jnlp.platform.name"),
    JNLP_LOCALIZE_REG_FORM_URL("jnlp.localize.reg.form.url"),
    JAVA_NET_PREFER_IPV4_STACK("java.net.preferIPv4Stack"),
    JNLP_HREF("jnlp.href"),
   JNLP_LOGIN_URL("jnlp.login.url"),
    JNLP_SRP6_LOGIN_URL("jnlp.srp6.login.url"),
    JNLP_REGISTER_NEW_DEMO_URL("jnlp.register.new.demo.url"),
    JNLP_FORGOTTEN_URL("jnlp.forgotten.url"),
    JNLP_OPEN_LIVE_URL("jnlp.open.live.url");


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
