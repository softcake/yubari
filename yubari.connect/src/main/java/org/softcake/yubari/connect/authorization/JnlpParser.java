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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@SuppressWarnings("CheckStyle")
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

            if (streamReader.getEventType() == XMLStreamReader.END_ELEMENT) {

                final String elementName = streamReader.getLocalName();
                if (RESOURCES.equals(elementName)) {
                    return properties;
                }

            } else if (streamReader.getEventType() == XMLStreamReader.START_ELEMENT) {

                final String elementName = streamReader.getLocalName();
                if (PROPERTY.equals(elementName)) {
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
                    final String res = "name=" + propertyName + " " + "value=" + propertyValue + " ";
                    LOGGER.debug(res);
                    properties.put(propertyName, propertyValue);
                }
            }
        }
        return properties;
    }

    public static AuthorizationProperties parse(final InputStream configFile) throws XMLStreamException {

        final XMLInputFactory factory = XMLInputFactory.newInstance();
        final XMLStreamReader streamReader = factory.createXMLStreamReader(configFile);

        while (streamReader.hasNext()) {
            streamReader.next();

            if (streamReader.getEventType() == XMLStreamReader.START_ELEMENT) {
                final String elementName = streamReader.getLocalName();
                if (RESOURCES.equals(elementName)) {
                    final HashMap<String, String> prop = parseProperties(streamReader);
                    return new AuthorizationProperties(prop.get(JNLP_CLIENT_MODE),
                                                       prop.get(JNLP_CLIENT_VERSION),
                                                       prop.get(JNLP_LOGIN_URL),
                                                       prop.get(JNLP_SRP6_LOGIN_URL),
                                                       prop.get(JAVA_NET_PREFER_IPV4_STACK));

                }
            }
        }
        throw new XMLStreamException("The jnlp file has no valid properties.");
    }

}
