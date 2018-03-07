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

import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.JAVA_NET_PREFER_IPV4_STACK;
import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.JNLP_CLIENT_MODE;
import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.JNLP_CLIENT_VERSION;
import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.JNLP_LOGIN_URL;
import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.JNLP_SRP6_LOGIN_URL;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


/**
 * A parser for jnlp file from Dukascopy.
 *
 * @author The softcake authors
 */
final class JnlpParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(JnlpParser.class);
    private static final String RESOURCES = "resources";
    private static final String PROPERTY = "property";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    private JnlpParser() {

        throw new IllegalAccessError("Utility class");
    }

    private static HashMap<String, String> parseProperties(final XMLStreamReader streamReader)
        throws XMLStreamException {

        final HashMap<String, String> properties = new HashMap<>();

        while (streamReader.hasNext()) {
            streamReader.next();

            if (isEndElement(streamReader)) {
                return properties;
            }

            if (isStartElement(streamReader)) {
                continue;
            }

            extractAndSetProperties(streamReader, properties);

        }
        return properties;
    }

    private static void extractAndSetProperties(final XMLStreamReader streamReader,
                                                final HashMap<String, String> properties) {

        if (PROPERTY.equals(streamReader.getLocalName())) {

            String propertyName = "";
            String propertyValue = "";

            for (int i = 0; i < streamReader.getAttributeCount(); i++) {
                final String name = streamReader.getAttributeLocalName(i);

                if (name.trim().equalsIgnoreCase(NAME)) {
                    propertyName = streamReader.getAttributeValue(i);
                }
                if (name.trim().equalsIgnoreCase(VALUE)) {
                    propertyValue = streamReader.getAttributeValue(i);
                }

            }
            LOGGER.debug("name={} | value={}", propertyName, propertyValue);
            properties.put(propertyName, propertyValue);
        }
    }

    private static boolean isStartElement(final XMLStreamReader streamReader) {

        return streamReader.getEventType() != XMLStreamReader.START_ELEMENT;
    }

    private static boolean isEndElement(final XMLStreamReader streamReader) {

        if (streamReader.getEventType() == XMLStreamReader.END_ELEMENT) {

            final String elementName = streamReader.getLocalName();
            return RESOURCES.equals(elementName);
        }
        return false;
    }

    public static AuthorizationProperties parse(final InputStream configFile) throws XMLStreamException {

        final XMLInputFactory factory = XMLInputFactory.newInstance();
        XMLStreamReader streamReader = null;
        AuthorizationProperties properties = null;
        try {
            streamReader = factory.createXMLStreamReader(configFile);

            while (streamReader.hasNext()) {
                streamReader.next();

                if (streamReader.getEventType() == XMLStreamReader.START_ELEMENT) {
                    final String elementName = streamReader.getLocalName();
                    if (RESOURCES.equals(elementName)) {

                        final HashMap<String, String> prop = parseProperties(streamReader);

                        properties = new AuthorizationProperties(getProperty(JNLP_CLIENT_MODE, prop),
                                                                 getProperty(JNLP_CLIENT_VERSION, prop),
                                                                 getProperty(JNLP_LOGIN_URL, prop),
                                                                 getProperty(JNLP_SRP6_LOGIN_URL, prop),
                                                                 getProperty(JAVA_NET_PREFER_IPV4_STACK, prop));

                    }
                }
            }
        } catch (final XMLStreamException e) {

            throw new XMLStreamException("The jnlp file is invalid, parse XML not possible!", e);
        } finally {
            if (streamReader != null) {
                streamReader.close();
            }
            try {
                configFile.close();
            } catch (final IOException e) {
                LOGGER.error("Error occurred while closing jnlp file!", e);
            }
        }
        if (properties == null) {
            throw new XMLStreamException("The jnlp file has invalid properties");
        }
        return properties;
    }

    private static String getProperty(final AuthorizationPropertiesNames name, final HashMap<String, String> properties)
        throws XMLStreamException {

        String value = properties.get(name.getValue());
        if (Strings.isNullOrEmpty(value)) {
            throw new XMLStreamException(String.format("The jnlp file has an invalid property: %s", name));
        }
        return value;
    }

}
