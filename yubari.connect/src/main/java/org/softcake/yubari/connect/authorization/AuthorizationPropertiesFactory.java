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

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.stream.XMLStreamException;

/**
 * The factory for the production of authorization properties  {@link AuthorizationProperties}.
 *
 * @author The softcake authors
 */
public class AuthorizationPropertiesFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationPropertiesFactory.class);
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 15000;
    private final String url;

    /**
     * The Constructor.
     *
     * @param url for request the jnlp file from Dukascopy.
     */
    public AuthorizationPropertiesFactory(final String url) {

        final String message = "The jnlp URL does not have a valid format " + "like http://....., you supplied: %s";

        final UrlValidator validator = new UrlValidator(new String[]{"http", "https"});

        checkArgument(validator.isValid(url), message, url);

        this.url = url;
    }

    private static HttpURLConnection getConnection(final URL url) throws IOException {

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);

        return connection;
    }

    private static AuthorizationProperties getAuthServerUrls(final String jnlp) {

        AuthorizationProperties properties = null;
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        URL jnlpUrl;

        try {
            jnlpUrl = new URL(jnlp);
            connection = getConnection(jnlpUrl);
            inputStream = connection.getInputStream();
            properties = JnlpParser.parse(inputStream);

        } catch (IOException | XMLStreamException e) {
            LOGGER.error("Error occurred...", e);
        } finally {
            if (inputStream != null) {

                try {
                    inputStream.close();
                } catch (final IOException e) {
                    LOGGER.error("Error occurred while closing Inputstream!", e);
                }
            }

            if (connection != null) {
                connection.disconnect();
            }
        }

        return properties;
    }

    /**
     * Getter for the authorization properties {@link AuthorizationProperties}.
     *
     * @return the properties
     */
    public AuthorizationProperties getAuthorizationProperties() {

        return getAuthServerUrls(this.url);
    }
}
