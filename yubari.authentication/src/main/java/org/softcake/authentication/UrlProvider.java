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

package org.softcake.authentication;

import org.softcake.cherry.core.base.PreCheck;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Pattern;

/**
 * @author The softcake authors
 */
public final class UrlProvider {
    public static final String JCLIENT_PLATFORM = "DDS3_JCLIENT";
    public static final String JFOREX_PLATFORM = "DDS3_JFOREX";
    public static final String JFOREXSDK_PLATFORM = "DDS3_JFOREXSDK";
    public static final String AUTHORIZATION_URL = "jnlp.login.url";
    public static final String AUTHORIZATION_URL_SRP6 = "jnlp.srp6.login.url";
    public static final String TICKET = "licentio";
    public static final Pattern RESULT_PATTERN = Pattern.compile("(([^:@]+(:\\d+)?)(@([^:@]+(:\\d+)?))*)@(.+)");
    public static final int URL_GROUP = 1;
    public static final int TICKET_GROUP = 7;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationClient.class);
    private static final String FO_REPORTS_ACTION = "open_reports";
    private static final String OPEN_CHAT_ACTION = "open_chat";
    private static final String SSL_IGNORE_ERRORS = "auth.ssl.ignore.errors";
    private static final String MESSAGE_DIGEST_ENCODING = "iso-8859-1";
    private static final String MESSAGE_DIGEST_ALGORITHM = "SHA-1";
    private static final String LOGIN_PARAM = "appello";
    private static final String PASSWORD_PARAM = "specialis";
    private static final String CAPTCHA_PARAM = "verbum_id";
    private static final String PIN_PARAM = "sententia";
    private static final String CAPTCHA_HEADER = "X-CaptchaID";
    private static final String VERSION_PARAM = "versio";
    private static final String SESSION_ID_PARAM = "sermo";
    private static final String PLATFORM_PARAM = "platform";
    private static final String STNGS_PARAM = "stngs";
    private static final String REGION_PARAM = "region";
    private static final String WILL_PING_PARAM = "willPing";
    private static final String API_MUNUS = "munus=api&";
    private static final String RELOGIN_MUNUS = "munus=relogin&";
    private static final String SETTINGS_MUNUS = "munus=stngs&";
    private static final String STS_MUNUS = "munus=sts";
    private static final String AUTH_CONTEXT = "/auth?munus=api&";
    private static final String RELOGIN_AUTH_CONTEXT = "/auth?munus=relogin&";
    private static final String SETTINGS_CONTEXT = "/auth?munus=stngs&";
    private static final String STS_CONTEXT = "auth?munus=sts";
    private static final String STS_WLBO = "auth?munus=sts_token";
    private static final String USER_AGENT_VALUE = "JForex";
    private static final String USER_AGENT_KEY = "User-Agent";
    private static final String LINES_SPLITTER = "##";
    private static final String IP_HTTP_HEADER = "Client-IP";
    private static final String CLIENT_COUNTRY = "Client-country";
    public static final int CONNECT_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = 15000;
    public static int ERROR_CODE_OFFSET = 400;
    private UrlProvider() {
    }




    private static String encodeAll(String password, String capthaId, String login) {

        String toCode;
        if (capthaId != null) {
            toCode = encodeString(password) + capthaId + login;
        } else {
            toCode = encodeString(password) + login;
        }

        return encodeString(toCode);
    }
    /**
     * algorithm: SHA-1 and encoding: iso-8859-1
     *
     * @param string
     * @return
     */
    public static String encodeString(String string) {
        return Hashing.sha1().newHasher().putString(string, Charsets.UTF_8).hash().toString().toUpperCase();
    }
    public static String getFormedUrl999(final URL baseUrl,
                                          final String login,
                                          final String oldTicket,
                                          final String sessionId,
                                          final String platform,
                                          final String ver) throws UnsupportedEncodingException {

        return baseUrl
               + RELOGIN_AUTH_CONTEXT
               + PLATFORM_PARAM
               + "="
               + platform
               + "&"
               + LOGIN_PARAM
               + "="
               + URLEncoder.encode(login, "UTF-8")
               + "&"
               + TICKET
               + "="
               + oldTicket
               + "&"
               + SESSION_ID_PARAM
               + "="
               + sessionId
               + "&"
               + VERSION_PARAM
               + "="
               + URLEncoder.encode(ver, "UTF-8")
               + "&"
               + WILL_PING_PARAM
               + "=true";
    }

    public static String getFormedUrl988(final URL ur, final String ticket, final String login, final String sessionId)
        throws UnsupportedEncodingException {

        return ur
               + SETTINGS_CONTEXT
               + LOGIN_PARAM
               + "="
               + URLEncoder.encode(login, "UTF-8")
               + "&"
               + TICKET
               + "="
               + ticket
               + "&"
               + SESSION_ID_PARAM
               + "="
               + sessionId
               + "&"
               + STNGS_PARAM
               + "=1";
    }

    /**
     * The report URL.
     *
     * @param reportKey the key
     * @param baseUrl   the url
     * @param stsToken  the requiered token
     * @return the report url as String
     */
    public static String getReportURL(String reportKey, String baseUrl, String stsToken) {

        PreCheck.parameterNotNullOrEmpty(reportKey, "reportKey");
        PreCheck.parameterNotNullOrEmpty(baseUrl, "baseUrl");
        PreCheck.parameterNotNullOrEmpty(stsToken, "stsToken");
        reportKey = reportKey.trim();

        StringBuilder reportUrl = new StringBuilder(256);
        reportUrl.append(baseUrl)
                 .append("/fo/reports/trader/auth/?")
                 .append("type")
                 .append("=")
                 .append("secure")
                 .append("&")
                 .append("token")
                 .append("=")
                 .append(stsToken)
                 .append("&")
                 .append("report")
                 .append("=")
                 .append(reportKey);
        return reportUrl.toString();
    }

    /**
     * @param baseUrl
     * @param stsToken
     * @param mode
     * @return
     */
    public static String getChatURL(String baseUrl, String stsToken, String mode) {

        PreCheck.parameterNotNullOrEmpty(stsToken, "stsToken");
        PreCheck.parameterNotNullOrEmpty(baseUrl, "baseUrl");
        PreCheck.parameterNotNullOrEmpty(mode, "mode");
        StringBuilder chatUrl = new StringBuilder();
        char separator = baseUrl.contains("#") ? '&' : '?';
        chatUrl.append(baseUrl).append(separator).append("token").append("=").append(mode).append(stsToken);
        return chatUrl.toString();
    }

}
