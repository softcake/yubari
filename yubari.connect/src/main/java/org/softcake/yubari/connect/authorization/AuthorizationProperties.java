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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.MoreObjects;
import org.apache.commons.validator.routines.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The authorization properties to connect the authentication server.
 *
 * @author The softcake authors
 */
public class AuthorizationProperties {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationProperties.class);
    private final ClientMode clientMode;
    private final boolean preferIPv4Stack;
    private final String version;
    private final List<URL> loginUrl;
    private final List<URL> loginSrpSixUrl;
    private final boolean canUseSrpSix;
    private final boolean isLive;
    private final boolean isValid;
    private final List<String> loginUrlStr;

    public AuthorizationProperties() {

        this("", "", "", "", "");
    }

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
        this.loginUrl = isNullOrEmpty(loginUrl) ? new ArrayList<>() : getUrlsFromString(loginUrl);
        this.loginSrpSixUrl = isNullOrEmpty(srpSixLoginUrl) ? new ArrayList<>() : getUrlsFromString(srpSixLoginUrl);
        this.clientMode = isNullOrEmpty(clientMode) ? ClientMode.DEMO : ClientMode.valueOf(clientMode.trim()
                                                                                                     .toUpperCase());
        this.preferIPv4Stack = isNullOrEmpty(preferIPv4Stack) || Boolean.parseBoolean(preferIPv4Stack.trim());
        this.isLive = ClientMode.LIVE.equals(this.clientMode);
        this.isValid = !this.loginUrl.isEmpty() || !this.loginSrpSixUrl.isEmpty();
        this.canUseSrpSix = !this.loginSrpSixUrl.isEmpty();

        this.loginUrlStr = Arrays.asList(loginUrl.split(","));
    }

    private static List<URL> getUrlsFromString(final String values) {

        final List<String> urlArray = Arrays.asList(values.split(","));
        final List<URL> urls = new ArrayList<>(urlArray.size());
        for (final String url : urlArray) {
            final UrlValidator validator = new UrlValidator(new String[]{"http", "https"});

            try {
                if (validator.isValid(url)) {
                    urls.add(new URL(url));
                } else {
                    LOGGER.debug("The url has not a valid format like http://....., you supplied: {}", url);
                }

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
    public ClientMode clientMode() {

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
     * @return a list of {@link URL}
     */
    public List<URL> getLoginUrls() {

        return this.loginUrl;
    }

    /**
     * The SRP6 URL´s.
     *
     * @return a list of {@link URL}
     */
    public List<URL> getLoginSrpSixUrls() {

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

    public List<String> getLoginUrlStr() {

        return loginUrlStr;
    }
}
