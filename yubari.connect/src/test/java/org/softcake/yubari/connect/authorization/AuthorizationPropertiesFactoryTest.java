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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.softcake.lemon.core.tester.PrivateConstructorTester;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The AuthorizationPropertiesFactoryTest Test.
 *
 * @author The softcake authors
 */
class AuthorizationPropertiesFactoryTest {
    private static String VALID_URL = "https://platform.dukascopy.com/demo_3/jforex_3.jnlp";
    private static String INVALID_URL = "https:/platform.dukascopy.com/demo_3/jforex_3.jnlp";

    @Test
    @DisplayName("Test private Constructor")
    void authorizationPropertiesConstructor() {

        PrivateConstructorTester.forClass(AuthorizationPropertiesFactory.class)
                                .expectedExceptionType(IllegalAccessError.class, "Utility class")
                                .check();

    }

    @Test
    @DisplayName("Test that Exception throws if url has invalid format")
    void authorizationPropertiesConstructorInvalidUrl() {

        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {

            AuthorizationPropertiesFactory.getAuthorizationProperties(INVALID_URL);
        });

        assertEquals("The jnlp URL does not have a valid format like http://....., you supplied: " + INVALID_URL,
                     exception.getMessage());

    }

    @Test
    @DisplayName("Test that Properties is not null")
    void getAuthorizationProperties() {

        AuthorizationProperties properties = AuthorizationPropertiesFactory.getAuthorizationProperties(VALID_URL);
        Assertions.assertNotNull(properties);
    }
}
