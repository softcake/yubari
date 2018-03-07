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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.xml.stream.XMLStreamException;

/**
 * The JnlpParser Test.
 *
 * @author The softcake authors
 */
class JnlpParserTest {
    private static final String INVALID_PROPERTIES_JNLP = "src/test/resources/invalidProperties.jnlp";
    private static final String INVALID_XMLFORMAT_JNLP = "src/test/resources/invalidXMLFormat.jnlp";
    private static final Logger LOGGER = LoggerFactory.getLogger(JnlpParserTest.class);

    @Test
    @DisplayName("Test that Exception throws if jnlp file has invalid properties")
    void parseExceptionTestInValidProperties() throws FileNotFoundException {

        File jnlpFile = new File(INVALID_PROPERTIES_JNLP);
        final FileInputStream inputStream = new FileInputStream(jnlpFile);

        Throwable exception = assertThrows(XMLStreamException.class, () -> JnlpParser.parse(inputStream));
        assertEquals("The jnlp file is invalid, parse XML not possible!", exception.getMessage());

    }


    @Test
    @DisplayName("Test that Exception throws if jnlp file has an invalid format")
    void parseExceptionTestInValidJnlpFile() throws FileNotFoundException {

        File jnlpFile = new File(INVALID_XMLFORMAT_JNLP);
        final FileInputStream inputStream = new FileInputStream(jnlpFile);

        XMLStreamException exception = assertThrows(XMLStreamException.class, () -> JnlpParser.parse(inputStream));
        assertEquals("The jnlp file is invalid, parse XML not possible!", exception.getMessage());

    }

    @Test
    @DisplayName("Test that Exception throws for private Constructor access")
    void constructorMustBePrivateAndThrowException_New() {

        PrivateConstructorTester.forClass(JnlpParser.class).expectedExceptionType(IllegalAccessError.class,
                                                                                  "Utility class").check();
    }
}
