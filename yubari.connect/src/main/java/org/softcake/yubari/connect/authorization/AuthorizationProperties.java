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
import static com.google.common.base.Strings.isNullOrEmpty;
import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.DEMO;
import static org.softcake.yubari.connect.authorization.AuthorizationPropertiesNames.LIVE;

import com.google.common.base.MoreObjects;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * The authorization properties to connect the authentication server.
 *
 * @author The softcake authors
 */
public class AuthorizationProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationProperties.class);
    private final String clientMode;
    private final boolean preferIPv4Stack;
    private final String version;
    private final URL[] loginUrl;
    private final URL[] loginSrpSixUrl;
    private final boolean canUseSrpSix;
    private final boolean isLive;
    private final boolean isValid;

    /**
     * The constructor.
     *
     * @param clientMode      the mode as String LIVE or DEMO
     * @param clientVersion   the current platform version
     * @param loginUrl        the standard login url
     * @param srpSixLoginUrl  the SRP6 url for save authentication
     * @param preferIPv4Stack to prefer the IPv4 protocol
     */
    public AuthorizationProperties(final String clientMode,
                                   final String clientVersion,
                                   final String loginUrl,
                                   final String srpSixLoginUrl,
                                   final String preferIPv4Stack) {

        this.version = isNullOrEmpty(clientVersion) ? "99.99.99" : clientVersion.trim();
        this.loginUrl = isNullOrEmpty(loginUrl) ? new URL[0] : getUrlsFromString(loginUrl);
        this.loginSrpSixUrl = isNullOrEmpty(srpSixLoginUrl) ? new URL[0] : getUrlsFromString(srpSixLoginUrl);
        this.clientMode = isNullOrEmpty(clientMode) ? DEMO : clientMode.trim().toUpperCase();
        this.preferIPv4Stack = isNullOrEmpty(preferIPv4Stack) || Boolean.parseBoolean(preferIPv4Stack.trim());
        this.isLive = LIVE.equals(this.clientMode);
        this.isValid = this.loginUrl.length != 0 || this.loginSrpSixUrl.length != 0;
        this.canUseSrpSix = this.loginSrpSixUrl.length != 0;
    }

    private static URL[] getUrlsFromString(final String values) {

        final String message = "The login from jnlp url has not a valid format like http://....., you supplied: %s.";
        final String[] urlArray = values.split(",");
        final URL[] urls = new URL[urlArray.length];

        for (int i = 0; i < urlArray.length; i++) {

            final String url = urlArray[i];
            checkArgument(new UrlValidator(new String[]{"http", "https"}).isValid(url), message, url);

            try {

                urls[i] = new URL(url);

            } catch (final MalformedURLException e) {
                LOGGER.error("Error occurred...", e);
            }
        }
        return urls;
    }

    /**
     * The client mode LIVE or DEMO.
     *
     * @return the mode as String
     */
    public String clientMode() {

        return this.clientMode;
    }

    /**
     * The platform version.
     *
     * @return the version as String
     */
    public String getVersion() {

        return this.version;
    }

    /**
     * The standard URL´s.
     *
     * @return an array of {@link URL}
     */
    public URL[] getLoginUrls() {

        return this.loginUrl;
    }

    /**
     * The SRP6 URL´s.
     *
     * @return an array of {@link URL}
     */
    public URL[] getLoginSrpSixUrls() {

        return this.loginSrpSixUrl;
    }

    /**
     * The truth if SPR6 is usable.
     *
     * @return true or false
     */
    public boolean canUseSrpSix() {

        return this.canUseSrpSix;
    }

    /**
     * The truth if the current platform is in LIVE mode.
     *
     * @return true or false
     */
    public boolean isLive() {

        return this.isLive;
    }

    /**
     * The truth if the properties are valid.
     *
     * @return true or false
     */
    public boolean isValid() {

        return this.isValid;
    }

    @Override
    public String toString() {

        return MoreObjects.toStringHelper(this)
                          .add("canUseSrpSix", this.canUseSrpSix)
                          .add("isLive", this.isLive)
                          .add("isValid",
                               this.isValid)
                          .add("loginSrpSixUrl", this.loginSrpSixUrl)
                          .add("loginUrl", this.loginUrl)
                          .add("mode",
                               this.clientMode)
                          .add("preferIPv4Stack", this.preferIPv4Stack)
                          .add("version", this.version)
                          .toString();
    }

    /**
     * The truth if IPv4 should use.
     *
     * @return true or false
     */
    public boolean isPreferIPv4Stack() {

        return this.preferIPv4Stack;
    }
}
