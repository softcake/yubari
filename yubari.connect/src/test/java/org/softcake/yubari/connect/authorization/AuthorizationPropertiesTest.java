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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * The AuthorizationProperties Test.
 *
 * @author The softcake authors
 */
@SuppressWarnings({"indentation", "multiplestringliterals"})
class AuthorizationPropertiesTest {
    private static final String CLIENT_VERSION = "3.3.2";
    private static final String CLIENT_MODE = "DEMO";
    private static final String TRUE = "true";
    private static String
        LOGIN_URL =
        "https://login.dukascopy.com/authorization-1/demo,"
        + "https://login.online-trading-solutions.com/authorization-1/demo,"
        + "https://login.dukascopy.com/authorization-2/demo";
    private static String
        SRP6_LOGIN_URL =
        "https://login.dukascopy.com/authorization-1/demo,https://login.online-trading-solutions"
        + ".com/authorization-1/demo,https://login.dukascopy.com/authorization-2/demo";
    private static String
        ONE_INVALID_LOGIN_URL =
        "https://login.dukascopy.com/authorization-1/demo,"
        + "tps://login.online-trading-solutions.com/authorization-1/demo,"
        + "https://login.dukascopy.com/authorization-2/demo";

    @Test
    void testAll() throws MalformedURLException {

        AuthorizationProperties properties = new AuthorizationProperties(CLIENT_MODE,
                                                                         CLIENT_VERSION,
                                                                         LOGIN_URL,
                                                                         SRP6_LOGIN_URL,
                                                                         TRUE);

        List<URL> loginUrls = Arrays.asList(new URL("https://login.dukascopy.com/authorization-1/demo"),
                                            new URL("https://login.online-trading-solutions.com/authorization-1/demo"),
                                            new URL("https://login.dukascopy.com/authorization-2/demo"));

        List<URL> loginSrpSixUrls = Arrays.asList(new URL("https://login.dukascopy.com/authorization-1/demo"),
                                                  new URL("https://login.online-trading-solutions"
                                                          + ".com/authorization-1/demo"),
                                                  new URL("https://login.dukascopy.com/authorization-2/demo"));

        assertAll("Properties",
                  () -> assertEquals(ClientMode.DEMO, properties.clientMode()),
                  () -> assertEquals("3.3.2", properties.getVersion()),
                  () -> Assertions.assertArrayEquals(loginUrls.toArray(), properties.getLoginUrls().toArray()),
                  () -> Assertions.assertArrayEquals(loginSrpSixUrls.toArray(),
                                                     properties.getLoginSrpSixUrls().toArray()),
                  () -> assertEquals(true, properties.canUseSrpSix()),
                  () -> assertEquals(false, properties.isLive()),
                  () -> assertEquals(true, properties.isPreferIPv4Stack()),
                  () -> assertEquals(true, properties.isValid()));

    }

    @Test
    void testOneInvalidUrl() throws MalformedURLException {

        AuthorizationProperties properties = new AuthorizationProperties(CLIENT_MODE,
                                                                         CLIENT_VERSION,
                                                                         ONE_INVALID_LOGIN_URL,
                                                                         SRP6_LOGIN_URL,
                                                                         TRUE);
        List<URL> loginUrls = Arrays.asList(new URL("https://login.dukascopy.com/authorization-1/demo"),
                                            new URL("https://login.dukascopy.com/authorization-2/demo"));

        List<URL> loginSrpSixUrls = Arrays.asList(new URL("https://login.dukascopy.com/authorization-1/demo"),
                                                  new URL("https://login.online-trading-solutions"
                                                          + ".com/authorization-1/demo"),
                                                  new URL("https://login.dukascopy.com/authorization-2/demo"));

        assertAll("Properties",
                  () -> assertEquals(ClientMode.DEMO, properties.clientMode()),
                  () -> assertEquals("3.3.2", properties.getVersion()),
                  () -> Assertions.assertArrayEquals(loginUrls.toArray(), properties.getLoginUrls().toArray()),
                  () -> Assertions.assertArrayEquals(loginSrpSixUrls.toArray(),
                                                     properties.getLoginSrpSixUrls().toArray()),
                  () -> assertEquals(true, properties.canUseSrpSix()),
                  () -> assertEquals(false, properties.isLive()),
                  () -> assertEquals(true, properties.isPreferIPv4Stack()),
                  () -> assertEquals(true, properties.isValid()));

    }

    @Test
    void testAllInvalidUrls() {

        AuthorizationProperties properties = new AuthorizationProperties(CLIENT_MODE,
                                                                         CLIENT_VERSION,
                                                                         "ftp://192.168.0.1",
                                                                         "ftp://ftp.nothing.com",
                                                                         TRUE);
        List<URL> loginUrls = Arrays.asList();

        List<URL> loginSrpSixUrls = Arrays.asList();

        assertAll("Properties",
                  () -> assertEquals(ClientMode.DEMO, properties.clientMode()),
                  () -> assertEquals("3.3.2", properties.getVersion()),
                  () -> Assertions.assertArrayEquals(loginUrls.toArray(), properties.getLoginUrls().toArray()),
                  () -> Assertions.assertArrayEquals(loginSrpSixUrls.toArray(),
                                                     properties.getLoginSrpSixUrls().toArray()),
                  () -> assertEquals(false, properties.canUseSrpSix()),
                  () -> assertEquals(false, properties.isLive()),
                  () -> assertEquals(true, properties.isPreferIPv4Stack()),
                  () -> assertEquals(false, properties.isValid()));

    }
}
