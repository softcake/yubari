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

/*import com.dukascopy.api.JFException;
import com.dukascopy.api.JFException.Error;
import com.dukascopy.api.instrument.AvailableInstrumentProvider;*/


import static org.softcake.authentication.AuthorizationServerResponseCode.AUTHORIZATION_REQUEST_TIMEOUT;
import static org.softcake.authentication.AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT;
import static org.softcake.authentication.AuthorizationServerResponseCode.EMPTY_RESPONSE;
import static org.softcake.authentication.AuthorizationServerResponseCode.ERROR_IO;
import static org.softcake.authentication.AuthorizationServerResponseCode.NOT_FOUND;
import static org.softcake.authentication.AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED;
import static org.softcake.authentication.AuthorizationServerResponseCode.SERVICE_UNAVAILABLE;
import static org.softcake.authentication.AuthorizationServerResponseCode.TOO_MANY_REQUESTS;
import static org.softcake.authentication.AuthorizationServerResponseCode.UNKNOWN_RESPONSE;
import static org.softcake.authentication.AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE;

import org.softcake.cherry.core.base.PreCheck;

import com.dukascopy.auth.client.AuthServerException;
import com.dukascopy.auth.client.AuthServerResponse;
import com.dukascopy.auth.client.ISRPClientProtocol;
import com.dukascopy.auth.client.SRPAuthClient;
import com.dukascopy.auth.client.SingleRequestAuthClient;
import com.dukascopy.auth.client.common.AccountType;
import com.dukascopy.auth.client.common.AuthParameterDefinition;
import com.dukascopy.auth.client.param.AbstractBusinessSRPAuthClientParamsBuilder;
import com.dukascopy.auth.client.param.ApiClientParamsBuilder;
import com.dukascopy.auth.client.param.LoginInfoParamsBuilder;
import com.dukascopy.auth.client.param.RememberMeClientParamsBuilder;
import com.dukascopy.auth.client.param.SettingsClientParamsBuilder;
import com.dukascopy.auth.client.param.SettingsClientParamsBuilder.SettingsClientParams;
import com.dukascopy.auth.client.protocol.ApiClientProtocol;
import com.dukascopy.auth.client.protocol.LoginInfoClientProtocol;
import com.dukascopy.auth.client.protocol.RememberMeClientProtocol;
import com.dukascopy.auth.client.protocol.SettingsClientProtocol;
import com.dukascopy.auth.client.response.ServerResponseObject;
import com.dukascopy.auth.client.response.SingleServerResponse;
import com.dukascopy.auth.client.response.Step3ServerResponse;
import com.dukascopy.auth.client.transport.http.HttpAuthTransport;
import com.google.common.base.Strings;
import com.nimbusds.srp6.SRP6Exception;
import com.nimbusds.srp6.SRP6Exception.CauseType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.imageio.ImageIO;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

@SuppressWarnings("ALL")
public class AuthorizationClient {
    public static final String JCLIENT_PLATFORM = "DDS3_JCLIENT";
    public static final String JFOREX_PLATFORM = "DDS3_JFOREX";
    public static final String JFOREXSDK_PLATFORM = "DDS3_JFOREXSDK";
    public static final String AUTHORIZATION_URL = "jnlp.login.url";
    public static final String AUTHORIZATION_URL_SRP6 = "jnlp.srp6.login.url";
    public static final String TICKET = "licentio";
    public static final Pattern RESULT_PATTERN = Pattern.compile("(([^:@]+(:\\d+)?)(@([^:@]+(:\\d+)?))*)@(.+)");
    public static final int URL_GROUP = 1;
    public static final int TICKET_GROUP = 7;
    public static final int CONNECT_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = 15000;
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
    public static int ERROR_CODE_OFFSET = 400;
    private static AuthorizationClient authClient;
    private final AuthorizationConfigurationPool configurationPool = new AuthorizationConfigurationPool();
    private final String version;
    private volatile AlsMessageBean ipInfoAlsMessageBean = null;
    private volatile boolean firstApiRequest = true;
    private AtomicReference<String> srp6PlatformSessionKey = new AtomicReference();

    private AuthorizationClient(Collection<String> authServerUrls, String version) throws MalformedURLException {

        Iterator var3 = authServerUrls.iterator();

        while (var3.hasNext()) {
            String authServerUrl = (String) var3.next();
            this.configurationPool.add(authServerUrl);
        }

        this.version = version;
    }

    public static synchronized AuthorizationClient getInstance(Collection<String> authServerUrls, String version) {

        if (authClient == null) {
            try {
                authClient = new AuthorizationClient(authServerUrls, version);
            } catch (MalformedURLException var4) {
                LOGGER.error(var4.getMessage(), var4);
            }
        } else {
            try {
                authClient.updateConfigurationPool(authServerUrls);
            } catch (MalformedURLException var3) {
                LOGGER.error(var3.getMessage(), var3);
            }
        }

        return authClient;
    }

    public static synchronized AuthorizationClient getInstance() {

        if (authClient == null) {
            throw new IllegalStateException("The AuthorizationClient is not initialized.");
        } else {
            return authClient;
        }
    }

    private static AuthorizationServerResponse getAuthorizationServerResponse(URL url) throws IOException {

        LOGGER.debug(">> [{}]", url);
        String response = null;

        AuthorizationServerResponse authorizationServerResponse = null;

        try {
            int authorizationServerResponseCode = 0;
            HttpURLConnection connection = getConnection(url);

            authorizationServerResponseCode = connection.getResponseCode();


            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                throw new IOException();
                // response = reader.readLine();
            } catch (IOException var10) {

            }

            authorizationServerResponse = new AuthorizationServerResponse(response, authorizationServerResponseCode);
        } finally {


        }

        LOGGER.debug("<< [{}]", response);
        return authorizationServerResponse;
    }

    public static String selectFastestAPIServer(String apiServersAndTicket)
        throws ApiUrlServerFormatException, InterruptedException, IOException {

        LOGGER.info("Selecting the best server...");


        Matcher matcher = RESULT_PATTERN.matcher(apiServersAndTicket);
        boolean matches = matcher.matches();

        if (!matches) {
            throw new ApiUrlServerFormatException("1. Wrong format of api url:" + apiServersAndTicket);
        } else {


            String apiUrls = matcher.group(1);
            LinkedHashMap<String, InetSocketAddress> addresses = new LinkedHashMap<>();
            StringTokenizer tokenizer = new StringTokenizer(apiUrls, "@");
            if (tokenizer.countTokens() == 0) {
                throw new ApiUrlServerFormatException("2. Wrong format of api url:" + apiServersAndTicket);
            } else if (tokenizer.countTokens() == 1) {
                return apiServersAndTicket;
            } else {
                while (tokenizer.hasMoreTokens()) {

                    String url = tokenizer.nextToken();
                    addresses.put(url, getInetSocketAddress(url));
                }
                Map<String, Long[]> pingTimesMap = Ping.ping(addresses);
                LinkedHashMap<Long, String> pingTimeLinkedMap = new LinkedHashMap<>();
                pingTimesMap.forEach((s, longs) -> pingTimeLinkedMap.put(getBestTime(longs), s));
                Long bestTime = pingTimeLinkedMap.keySet().stream().min(Long::compareTo).orElse(Long.MAX_VALUE);

                String bestURL = pingTimeLinkedMap.get(bestTime);
                LOGGER.debug("Best api url [{}] with time [{}]", bestURL, bestTime);
                addIPAndBestPing(bestTime, bestURL);
                String address = bestURL + "@" + matcher.group(7);
                if (validAPIServerAddressFormat(address)) {
                    return address;
                } else {
                    throw new ApiUrlServerFormatException("4. Wrong format of api url:" + address);
                }
            }

        }
    }

    /**
     * @param url
     * @return
     */
    private static InetSocketAddress getInetSocketAddress(final String url) {

        String host;
        int port;
        int semicolonIndex = url.indexOf(":");
        if (semicolonIndex != -1) {
            host = url.substring(0, semicolonIndex);
            if (semicolonIndex + 1 >= url.length()) {
                LOGGER.warn("Port is not set, using default 443");
                port = 443;
            } else {
                port = Integer.parseInt(url.substring(semicolonIndex + 1));
            }
        } else {
            LOGGER.warn("Port is not set, using default 443");
            host = url;
            port = 443;
        }
        return new InetSocketAddress(host, port);
    }

    /**
     * @param pingTimes
     * @return
     */
    private static Long getBestTime(Long[] pingTimes) {

        Long bestTime = 0L;
        for (Long time : pingTimes) {
            if (time < 0 || time == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            } else {
                bestTime += time;
            }
        }
        return bestTime / pingTimes.length;
    }

    private static void addIPAndBestPing(long bestTime, String bestURL) {

        if (authClient.ipInfoAlsMessageBean != null) {
            authClient.ipInfoAlsMessageBean.addParameter("PING_TIME", bestTime).addParameter("API_IP_ADDRESS", bestURL);
            //  PlatformServiceProvider.getInstance().setIpInfoAlsMessageBean(authClient.ipInfoAlsMessageBean);
            authClient.ipInfoAlsMessageBean = null;
        } else {
            LOGGER.error("The ipInfoAlsMessageBean is null.");
        }

    }

    /**
     * @param url
     * @return
     * @throws IOException
     */
    private static AuthorizationServerPropertiesResponse requestPlatformProperties(URL url) throws IOException {

        AuthorizationServerPropertiesResponse response = null;
        HttpURLConnection connection = getConnection(url);
        int responseCode = connection.getResponseCode();

        String clientIpAddress = connection.getHeaderField(IP_HTTP_HEADER);
        if (clientIpAddress == null || clientIpAddress.trim().isEmpty()) {
            clientIpAddress = "n/a";
        }

        String clientCountry = connection.getHeaderField(CLIENT_COUNTRY);
        if (clientCountry == null || clientCountry.trim().isEmpty()) {
            clientCountry = "n/a";
        }

        //TODO
        if (authClient.ipInfoAlsMessageBean != null) {
            authClient.ipInfoAlsMessageBean.addParameter("CLIENT_IP_ADDRESS", clientIpAddress).addParameter(
                "CLIENT_COUNTRY",
                clientCountry);
            //                    PlatformServiceProvider.getInstance().setIpInfoAlsMessageBean(authClient
            // .ipInfoAlsMessageBean);
            //                    PlatformServiceProvider.getInstance().setClientCountry(clientCountry);
            authClient.ipInfoAlsMessageBean = null;
        } else {
            LOGGER.error("The ipInfoAlsMessageBean is null.");
        }

        String propertiesAsString = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {

            StringBuilder sb = new StringBuilder(8192);
            String line;
            do {
                line = reader.readLine();
                sb.append(line);
            } while (line != null);

            propertiesAsString = sb.toString().trim();
        }

        return new AuthorizationServerPropertiesResponse(responseCode, propertiesAsString);

    }

    /**
     * @param errorMessage
     * @throws Exception
     */
    private static void handleNoAccountSettings(String errorMessage) throws Exception {

        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED.getMessage();
        }

        LOGGER.error("General error! System will exit! Message: {}", errorMessage);

        //        if (PlatformServiceProvider.getInstance().isSdkMode()) {
        //            throw new JFException(Error.NO_ACCOUNT_SETTINGS_RECEIVED, errorMessage);
        //        } else {
        //            showErrorMessageInEDT(errorMessage);
        //            System.exit(0);
        //        }
    }

    private static void showErrorMessage(String errorMessage) {
        //        CommonOptionPane.showErrorMessage((Component)null, "Error", errorMessage, (Icon)null);
    }

    private static void showErrorMessageInEDT(final String errorMessage) {
        //        if (!PlatformServiceProvider.getInstance().isSdkMode()) {
        //            if (!SwingUtilities.isEventDispatchThread()) {
        //                try {
        //                    SwingUtilities.invokeAndWait(new Runnable() {
        //                        public void run() {
        //                            AuthorizationClient.showErrorMessage(errorMessage);
        //                        }
        //                    });
        //                } catch (Throwable var2) {
        //                    LOGGER.error(var2.getMessage(), var2);
        //                }
        //            } else {
        //                showErrorMessage(errorMessage);
        //            }
        //
        //        }
    }

    private static HttpURLConnection getConnection(URL url) throws IOException {


        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection instanceof HttpsURLConnection && Boolean.getBoolean(SSL_IGNORE_ERRORS)) {
            LOGGER.debug("Ignoring SSL errors");
            ((HttpsURLConnection) connection).setHostnameVerifier(new DummyHostNameVerifier());
        } else {
            connection.setRequestProperty(USER_AGENT_KEY, USER_AGENT_VALUE);
        }
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        return connection;

    }


    /**
     * @param address
     * @return
     */
    public static boolean validAPIServerAddressFormat(String address) {

        PreCheck.parameterNotNullOrEmpty(address, "address");
        Matcher e = RESULT_PATTERN.matcher(address);
        return e.matches();
    }

    public static void writeToRememberMeLog(String currentToken,
                                            String nextToken,
                                            boolean authResult,
                                            String deviceID,
                                            boolean rememberMe,
                                            String login,
                                            String platform,
                                            String deviceName,
                                            String deviceOS,
                                            String platformVersion,
                                            String sessionId,
                                            String authorizationUrl) {

        try {
            if (Strings.isNullOrEmpty(currentToken)) {
                currentToken = "n/a";
            }

            if (Strings.isNullOrEmpty(nextToken)) {
                nextToken = "n/a";
            }

            if (Strings.isNullOrEmpty(deviceID)) {
                deviceID = "n/a";
            }
/*
            long currentTimeMillis = System.currentTimeMillis();
            SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentDate = DATE_FORMAT.format(new Date(currentTimeMillis));
            String info = String.format("%s %s%-10s %s%-65s %s%-65s %s%-5s %s%s %s%-5s %s%-15s %s%-15s %s%-20s
            %s%-15s %s%-40s %s%s", currentDate, "login=", login, "currentToken=", currentToken, "nextToken=",
            nextToken, "authResult=", String.valueOf(authResult), "deviceID=", deviceID, "rememberMe=", rememberMe,
            "platform=", platform, "deviceName=", deviceName, "deviceOS=", deviceOS, "platformVersion=",
            platformVersion, "sessionId=", sessionId, "authorizationUrl=", authorizationUrl);
            IFilePathManager filePathManager = FilePathManager.getInstance();
            String defaultJForexFolderPath = filePathManager.getDefaultJForexFolderPath();
            Path autologinLogPath = Paths.get(defaultJForexFolderPath, "autologin-log.txt");
            LOGGER.info("autologin-log-path=" + autologinLogPath);
            StringBuffer buf = new StringBuffer(128);
            buf.append(info).append(System.lineSeparator());
            boolean sizeExceeded = false;
            if (Files.exists(autologinLogPath, new LinkOption[0])) {
                File f = new File(autologinLogPath.toString());
                long length = f.length();
                long kilobytes = length / 1024L;
                sizeExceeded = kilobytes > 100L;
            }

            StandardOpenOption opt;
            if (Files.exists(autologinLogPath, new LinkOption[0]) && !sizeExceeded) {
                opt = StandardOpenOption.APPEND;
            } else if (Files.exists(autologinLogPath, new LinkOption[0]) && sizeExceeded) {
                opt = StandardOpenOption.TRUNCATE_EXISTING;
            } else {
                opt = StandardOpenOption.CREATE_NEW;
            }

            Files.write(autologinLogPath, buf.toString().getBytes(), new OpenOption[]{opt});*/
        } catch (Throwable var27) {
            LOGGER.error(var27.getMessage(), var27);
        }

    }

    /**
     * @param authServerUrls
     * @throws MalformedURLException
     */
    public void updateConfigurationPool(Collection<String> authServerUrls) throws MalformedURLException {

        this.configurationPool.clear();

        for (final String url : authServerUrls) {
            this.configurationPool.add(url);
        }
    }


    /**
     * The report URL.
     *
     * @param reportKey the key
     * @param baseUrl   the url
     * @param stsToken  the requiered token
     * @return the report url as String
     */
    public String getReportURL(String reportKey, String baseUrl, String stsToken) {

        return UrlProvider.getReportURL(reportKey, baseUrl, stsToken);
    }

    /**
     * @param baseUrl
     * @param stsToken
     * @param mode
     * @return
     */
    public String getChatURL(String baseUrl, String stsToken, String mode) {

        return UrlProvider.getChatURL(baseUrl, stsToken, mode);
    }

    /**
     * @param apello
     * @param sermo
     * @param licentio
     * @return
     */
    public AuthorizationServerStsTokenResponse getReportSTSToken(String apello, String sermo, String licentio) {

        return this.getSTSToken(apello, sermo, licentio, FO_REPORTS_ACTION);
    }

    /**
     * @param apello
     * @param sermo
     * @param licentio
     * @return
     */
    public AuthorizationServerStsTokenResponse getChatSTSToken(String apello, String sermo, String licentio) {

        return this.getSTSToken(apello, sermo, licentio, OPEN_CHAT_ACTION);
    }

    public AuthorizationServerStsTokenResponse getSTSToken(String apello,
                                                           String sermo,
                                                           String licentio,
                                                           String action) {

        String to = "FO";
        AuthorizationServerStsTokenResponse authorizationServerStsTokenResponse = null;

        for (int retryCount = 0; retryCount < this.configurationPool.size(); ++retryCount) {
            try {
                String baseUrl = this.getBaseUrl().toString();
                String slash = "";
                if (!baseUrl.endsWith("/")) {
                    slash = "/";
                }

                StringBuilder stsTokenURLBuf = new StringBuilder(256);
                stsTokenURLBuf.append(baseUrl)
                              .append(slash)
                              .append(STS_CONTEXT)
                              .append("&")
                              .append("appello=")
                              .append(apello)
                              .append("&")
                              .append("sermo=")
                              .append(sermo)
                              .append("&")
                              .append("licentio=")
                              .append(licentio)
                              .append("&")
                              .append("to=")
                              .append("FO")
                              .append("&")
                              .append("action=")
                              .append(action);


                URL stsTokenURL = new URL(stsTokenURLBuf.toString());
                String response = null;

                int authorizationServerResponseCode = 0;
                HttpURLConnection connection = getConnection(stsTokenURL);
                authorizationServerResponseCode = connection.getResponseCode();


                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    response = reader.readLine();
                } catch (IOException e) {
                    LOGGER.error("Error occurred...", e);
                }

                authorizationServerStsTokenResponse = new AuthorizationServerStsTokenResponse(response,
                                                                                              authorizationServerResponseCode);
                if (authorizationServerStsTokenResponse.isOK()) {
                    return authorizationServerStsTokenResponse;
                }

                LOGGER.error("Cannot get STS token: {}", stsTokenURLBuf.toString());
                LOGGER.error(authorizationServerStsTokenResponse.toString());
            } catch (MalformedURLException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerStsTokenResponse = new AuthorizationServerStsTokenResponse(
                    AuthorizationServerResponseCode.BAD_URL);
                return authorizationServerStsTokenResponse;
            } catch (IOException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerStsTokenResponse = new AuthorizationServerStsTokenResponse(ERROR_IO);
            } catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerStsTokenResponse = new AuthorizationServerStsTokenResponse(
                    AuthorizationServerResponseCode.INTERNAL_ERROR);
                return authorizationServerStsTokenResponse;
            }

            this.configurationPool.markLastUsedAsBad();
        }

        return authorizationServerStsTokenResponse;
    }

    public Map<String, BufferedImage> getImageCaptcha() throws IOException {

        return this.getImageCaptcha((CaptchaParametersBean) null);
    }

    public Map<String, BufferedImage> getImageCaptcha(CaptchaParametersBean captchaParametersBean) throws IOException {

        HashMap<String, BufferedImage> output = null;
        int retryCount = 0;

        while (retryCount < this.configurationPool.size() && output == null) {
            String baseURL = this.getBaseUrl().toString();
            StringBuffer buf = new StringBuffer(128);
            buf.append(baseURL);
            if (!baseURL.endsWith("/")) {
                buf.append("/");
            }

            buf.append("captcha");
            if (captchaParametersBean != null) {
                buf.append(captchaParametersBean.getURLParametersPart());
            }

            String formedUrl = buf.toString();
            LOGGER.debug(">> [{}]", formedUrl);
            URL captchaUrl = new URL(formedUrl);
            InputStream inputStream = null;

            try {
                URLConnection connection = getConnection(captchaUrl);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                inputStream = connection.getInputStream();
                BufferedImage captchaImage = ImageIO.read(inputStream);
                if (captchaImage == null) {
                    LOGGER.error("Empty captchaImage");
                    this.configurationPool.markLastUsedAsBad();
                    ++retryCount;
                    continue;
                }

                String captchaId = connection.getHeaderField(CAPTCHA_HEADER);
                if (captchaId != null) {
                    captchaId = captchaId.trim();
                }

                if (captchaId == null || captchaId.isEmpty()) {
                    LOGGER.error("Empty captchaId");
                    this.configurationPool.markLastUsedAsBad();
                    ++retryCount;
                    continue;
                }

                LOGGER.debug("<< [{} : {}]", CAPTCHA_HEADER, captchaId);
                output = new HashMap();
                output.put(captchaId, captchaImage);
            } catch (Throwable var23) {
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error(var23.getMessage(), var23);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception var22) {
                    ;
                }

            }

            ++retryCount;
        }

        return output;
    }

    public AuthorizationServerResponse getAPIsAndTicketUsingLogin(String login,
                                                                  String password,
                                                                  String sessionId,
                                                                  String platform) {

        return this.getAPIsAndTicketUsingLogin(login, password, sessionId, true, platform);
    }

    /**
     * @param retryCount
     * @param response
     * @return
     */
    private boolean continueObtainingAPIServers(int retryCount,
                                                AuthorizationServerResponse response) {

        return response == null || (retryCount < this.configurationPool.size()
                                                       && response.isEmptyResponse()
                                                       || retryCount < this.configurationPool.size() && (
            response.getResponseCode() == TOO_MANY_REQUESTS
            || response.getResponseCode() == ERROR_IO
            || response.getResponseCode() == EMPTY_RESPONSE
            || response.getResponseCode() == NOT_FOUND
            || response.getResponseCode() == WRONG_AUTH_RESPONSE
            || response.getResponseCode() == AUTHORIZATION_REQUEST_TIMEOUT
            || response.getResponseCode() == AUTHORIZATION_TIMEOUT
            || response.getResponseCode() == NO_PROPERTIES_RECEIVED
            || response.getResponseCode() == SERVICE_UNAVAILABLE
            || response.getResponseCode() == UNKNOWN_RESPONSE
            || response.isSystemError()));
    }

    /**
     * @param retryCount
     * @param authorizationServerResponse
     * @return
     */
    private boolean continueCheckIfPinRequired(int retryCount,
                                               AuthorizationServerPinRequiredResponse authorizationServerResponse) {

        if (authorizationServerResponse == null) {
            return true;
        } else {
            return retryCount < configurationPool.size() && (authorizationServerResponse.getResponseCode()
                                                             == TOO_MANY_REQUESTS
                                                             || authorizationServerResponse.getResponseCode()
                                                                == ERROR_IO
                                                             || authorizationServerResponse.getResponseCode()
                                                                == EMPTY_RESPONSE
                                                             || authorizationServerResponse.getResponseCode()
                                                                == NOT_FOUND
                                                             || authorizationServerResponse.getResponseCode()
                                                                == WRONG_AUTH_RESPONSE
                                                             || authorizationServerResponse.getResponseCode()
                                                                == AUTHORIZATION_REQUEST_TIMEOUT
                                                             || authorizationServerResponse.getResponseCode()
                                                                == AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT
                                                             || authorizationServerResponse.getResponseCode()
                                                                == AuthorizationServerResponseCode.SERVICE_UNAVAILABLE
                                                             || authorizationServerResponse.getResponseCode()
                                                                == AuthorizationServerResponseCode.INTERRUPTED);
        }
    }

    /**
     * @param attempts
     */
    private void resetSameAuthUrlAttempts(Map<AuthorizationServerResponseCode, SameAuthUrlAttemptsBean> attempts) {


        if (attempts != null) {
            Collection<SameAuthUrlAttemptsBean> values = attempts.values();
            for (SameAuthUrlAttemptsBean value : values) {
                value.resetCurrentAttempts();
            }

        }
    }

    /**
     * @param authorizationServerResponseCode
     * @param attempts
     * @return
     */
    private boolean useTheSameAuthorizationURL(AuthorizationServerResponseCode authorizationServerResponseCode,
                                               Map<AuthorizationServerResponseCode, AuthorizationClient
                                                   .SameAuthUrlAttemptsBean> attempts) {

        AuthorizationClient.SameAuthUrlAttemptsBean authUrlAttemptsBean = attempts.get(authorizationServerResponseCode);
        return authUrlAttemptsBean != null ? authUrlAttemptsBean.isNextAttemptAvailable() : false;
    }

    public Properties getPlatformPropertiesFromAuthServer(String login, String clientVersion, String sessionId) {

        int i = 0;
        boolean printDebugInfo = false;
        String debugPrefix = "PropertiesRequest:";
        int retryCount = 0;
        Properties properties = null;
        String password = (String) this.srp6PlatformSessionKey.get();
        if (password == null || password.isEmpty()) {
            password = System.getProperty("jnlp.auth.ticket");
        }

        while (retryCount++ < this.configurationPool.size()) {
            try {
                String authorizationUrl = this.getBaseUrl().toString();
                Logger var24 = LOGGER;
                Object[] var10002 = new Object[5];
                ++i;
                //                var10002[0] = i;
                //                var10002[1] = "PropertiesRequest:";
                //                var10002[2] = "HttpBase";
                //                var10002[3] = "=";
                //                var10002[4] = authorizationUrl;
                //                var24.debug(String.format("%d %s%-17s%s%s", var10002));
                SettingsClientParamsBuilder builder = new SettingsClientParamsBuilder();
                ((SettingsClientParamsBuilder) ((SettingsClientParamsBuilder) ((SettingsClientParamsBuilder) (
                    (SettingsClientParamsBuilder) ((SettingsClientParamsBuilder) ((SettingsClientParamsBuilder) (
                        (SettingsClientParamsBuilder) ((SettingsClientParamsBuilder) ((SettingsClientParamsBuilder) (
                            (SettingsClientParamsBuilder) builder
                    .setAuthTransport(new HttpAuthTransport())).setSessionId(sessionId)).setPlatform(JFOREX_PLATFORM)
                        ).setLogin(
                    login)).setPassword(password)).setPlatformVersion(clientVersion)).setAccountType(AccountType.TRADER))
                    .setUserAgent(USER_AGENT_VALUE)).setZipSettings(true)).setLogger(LOGGER)).setHttpBase(
                    authorizationUrl);
                SettingsClientParams params = builder.build();
                SettingsClientProtocol protocol = new SettingsClientProtocol(params);
                SRPAuthClient client = new SRPAuthClient(protocol, LOGGER);
                ServerResponseObject<Step3ServerResponse> result = client.authenticate();
                Step3ServerResponse passwordServerResponse = (Step3ServerResponse) result.getPasswordServerResponse();
                String serverResponse = passwordServerResponse.getServerResponse();

                //TODO JSONObject jsonObject = new JSONObject(serverResponse, true);
                JSONObject jsonObject = new JSONObject(serverResponse);
                String settings = jsonObject.getString(AuthParameterDefinition.SETTINGS.getName(AccountType.TRADER));
                byte[] compressedBytes = Base64.decode(settings);
                byte[] settingsBytes = this.unzip(compressedBytes);
                ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(settingsBytes));
                properties = (Properties) inputStream.readObject();
                if (properties != null) {
                    if (authClient.isSnapshotVersion()) {
                        LOGGER.debug("Properties received as a separate request << [{}]", properties);
                    }
                    break;
                }
            } catch (Throwable var23) {
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error(var23.getMessage(), var23);
            }
        }

        return properties;
    }

    public AuthorizationServerResponse getAPIsAndTicketUsingLogin_SRP6(String login,
                                                                       String password,
                                                                       String captchaId,
                                                                       String pin,
                                                                       String sessionId,
                                                                       String platform) {

        return this.getAPIsAndTicketUsingLogin_SRP6(login,
                                                    password,
                                                    captchaId,
                                                    pin,
                                                    sessionId,
                                                    platform,
                                                    (Boolean) null,
                                                    (String) null,
                                                    (String) null,
                                                    (String) null,
                                                    (String) null);
    }

    public AuthorizationServerResponse getAPIsAndTicketUsingLogin_SRP6(String login,
                                                                       String password,
                                                                       String captchaId,
                                                                       String pin,
                                                                       String sessionId,
                                                                       String platform,
                                                                       Boolean rememberMe,
                                                                       String deviceId,
                                                                       String previousRememberMeToken,
                                                                       String deviceName,
                                                                       String deviceOS) {

        try {
            //            Object platformInstanceId = CommonContext.getConfig("PLATFORM_INSTANCE_ID");
            //            LOGGER.info("environment(jnlp.client.mode)={}, platformInstanceId={}", System.getProperty
            // ("jnlp.client.mode"), platformInstanceId);
        } catch (Throwable var42) {
            LOGGER.error(var42.getMessage(), var42);
        }

        platform = JFOREX_PLATFORM;
        String AUTH_API_URLS_KEY = "authApiURLs";
        AuthorizationServerResponse authorizationServerResponse = null;
        int retryCount = 0;
        if (pin != null && pin.isEmpty() || pin == null) {
            pin = null;
            captchaId = null;
        }

        if (password != null && !password.isEmpty()) {
            previousRememberMeToken = null;
        }

        boolean isRememberMeTokenRequest = rememberMe != null
                                           && rememberMe
                                           && deviceId != null
                                           && !deviceId.isEmpty()
                                           && previousRememberMeToken == null;
        boolean isRememberMeTokenRemove = rememberMe != null
                                          && !rememberMe
                                          && deviceId != null
                                          && !deviceId.isEmpty()
                                          && previousRememberMeToken == null;
        boolean isRememberMeTokenAuthorization = previousRememberMeToken != null
                                                 && !previousRememberMeToken.isEmpty()
                                                 && rememberMe != null
                                                 && rememberMe
                                                 && deviceId != null
                                                 && !deviceId.isEmpty();
        AuthorizationClient.SameAuthUrlAttemptsBean authUrlAttemptsBean = null;
        Map<AuthorizationServerResponseCode, AuthorizationClient.SameAuthUrlAttemptsBean>
            sameAuthUrlAttempts
            = new HashMap();
        authUrlAttemptsBean = new AuthorizationClient.SameAuthUrlAttemptsBean(3);
        sameAuthUrlAttempts.put(AUTHORIZATION_REQUEST_TIMEOUT, authUrlAttemptsBean);
        authUrlAttemptsBean = new AuthorizationClient.SameAuthUrlAttemptsBean(3);
        sameAuthUrlAttempts.put(AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT, authUrlAttemptsBean);
        authUrlAttemptsBean = new AuthorizationClient.SameAuthUrlAttemptsBean(2);
        sameAuthUrlAttempts.put(AuthorizationServerResponseCode.SERVICE_UNAVAILABLE, authUrlAttemptsBean);

        while (true) {
            label191:
            while (true) {
                if (!this.continueObtainingAPIServers(retryCount,

                                                      authorizationServerResponse)) {
                    return authorizationServerResponse;
                }

                if (this.firstApiRequest) {
                    this.firstApiRequest = false;
                } else {
                    try {
                        sessionId = this.getNewSessionId();
                    } catch (Throwable var41) {
                        LOGGER.error(var41.getMessage(), var41);
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                        return authorizationServerResponse;
                    }
                }

                String authorizationUrl = null;

                CauseType authProtocol;
                String serverResponse;
                try {
                    authorizationUrl = this.getBaseUrl().toString();
                    AbstractBusinessSRPAuthClientParamsBuilder<?, ?> builder = null;
                    if (isRememberMeTokenAuthorization) {
                        builder = new RememberMeClientParamsBuilder();
                    } else {
                        builder = new ApiClientParamsBuilder();
                    }

                    ((AbstractBusinessSRPAuthClientParamsBuilder) ((AbstractBusinessSRPAuthClientParamsBuilder) (
                        (AbstractBusinessSRPAuthClientParamsBuilder) ((AbstractBusinessSRPAuthClientParamsBuilder)
                                                                          builder)
                        .setAuthTransport(new HttpAuthTransport())).setHttpBase(authorizationUrl)).setLogin(login)
                                                                                                  .setPassword(password)
                                                                                                  .setCaptchaId(
                                                                                                      captchaId)
                                                                                                  .setPin(pin)
                                                                                                  .setAccountType(
                                                                                                      AccountType
                                                                                                          .TRADER)
                                                                                                  .setSessionId(
                                                                                                      sessionId)
                                                                                                  .setPlatform(platform)
                                                                                                  .setPlatformVersion(
                                                                                                      this.version)
                                                                                                  .setWillPing(true)
                                                                                                  .setNeedSettings(true)
                                                                                                  .setUserAgent(
                                                                                                      USER_AGENT_VALUE))
                        .setLogger(LOGGER);
                    if (isRememberMeTokenRemove) {
                        ((ApiClientParamsBuilder) builder).setRememberMe(false).setDeviceId(deviceId).setDeviceName(
                            deviceName).setDeviceOS(deviceOS).setAccountType(AccountType.TRADER);
                    }

                    if (isRememberMeTokenRequest) {
                        ((ApiClientParamsBuilder) builder).setRememberMe(rememberMe)
                                                          .setDeviceId(deviceId)
                                                          .setDeviceName(deviceName)
                                                          .setDeviceOS(deviceOS)
                                                          .setAccountType(AccountType.TRADER);
                    }

                    if (isRememberMeTokenAuthorization) {
                        ((RememberMeClientParamsBuilder) ((RememberMeClientParamsBuilder) builder).setDeviceId(deviceId)
                                                                                                  .setDeviceName(
                                                                                                      deviceName)
                                                                                                  .setDeviceOS(deviceOS)
                                                                                                  .setAccountType(
                                                                                                      AccountType
                                                                                                          .TRADER))
                            .setPassword(previousRememberMeToken);
                    }

                    authProtocol = null;
                    Object authProtocol2;
                    if (isRememberMeTokenAuthorization) {
                        authProtocol2 = new RememberMeClientProtocol(((RememberMeClientParamsBuilder) builder).build());
                    } else {
                        authProtocol2 = new ApiClientProtocol(((ApiClientParamsBuilder) builder).build());
                    }

                    SRPAuthClient client = new SRPAuthClient((ISRPClientProtocol) authProtocol2, LOGGER);
                    ServerResponseObject<Step3ServerResponse> result = client.authenticate();
                    if (result != null) {
                        String
                            newRememberMeToken
                            = ((Step3ServerResponse) result.getPasswordServerResponse()).getSessionKey();
                        this.srp6PlatformSessionKey.set(newRememberMeToken);
                        this.retrieveDataForALS(result);
                        Step3ServerResponse
                            passwordServerResponse
                            = (Step3ServerResponse) result.getPasswordServerResponse();
                        serverResponse = passwordServerResponse.getServerResponse();
                        String sessionKey = passwordServerResponse.getSessionKey();
                        // TODO  JSONObject jsonObject = new JSONObject(serverResponse, true);
                        JSONObject jsonObject = new JSONObject(serverResponse);
                        JSONArray jsonArray = jsonObject.getJSONArray("authApiURLs");
                        StringBuilder sb = new StringBuilder(1024);

                        for (int i = 0; i < jsonArray.length(); ++i) {
                            sb.append(jsonArray.get(i));
                            sb.append("@");
                        }

                        sb.append(sessionKey);
                        Properties properties = null;

                        String fastestAPIAndTicket;
                        try {
                            fastestAPIAndTicket = AuthParameterDefinition.SETTINGS.getName(AccountType.TRADER);
                            String encodedString = jsonObject.getString(fastestAPIAndTicket);
                            byte[] decodedBytes = Base64.decode(encodedString);
                            ObjectInputStream
                                inputStream
                                = new ObjectInputStream(new ByteArrayInputStream(decodedBytes));
                            properties = (Properties) inputStream.readObject();
                            if (authClient.isSnapshotVersion()) {
                                LOGGER.debug("Properties received << [{}]", properties);
                            }

                            // AvailableInstrumentProvider.INSTANCE.init(properties);
                        } catch (Throwable var40) {
                            LOGGER.error(var40.getMessage(), var40);
                        }

                        authorizationServerResponse = new AuthorizationServerResponse(sb.toString(),
                                                                                      AuthorizationServerResponseCode
                                                                                          .SUCCESS_OK,
                                                                                      true,
                                                                                      properties);
                        if (authorizationServerResponse.isOK()) {
                            if (isRememberMeTokenRequest || isRememberMeTokenAuthorization) {
                                authorizationServerResponse.setRememberMeToken(newRememberMeToken);
                                if (password != null && !password.isEmpty()) {
                                    authorizationServerResponse.setPasswordLength(password.length());
                                }
                            }

                            fastestAPIAndTicket
                                = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                            authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                        }

                        try {
                            //                            if (ObjectUtils.isEqual(CommonContext.CLIENT_VERSION,
                            // "99.99.99") && isRememberMeTokenAuthorization) {
                            //                                writeToRememberMeLog(previousRememberMeToken,
                            // newRememberMeToken, authorizationServerResponse.isOK(), deviceId, rememberMe, login,
                            // platform, deviceName, deviceOS, this.version, sessionId, authorizationUrl);
                            //                            }
                        } catch (Throwable var39) {
                            LOGGER.error(var39.getMessage(), var39);
                        }
                        break;
                    }

                    authorizationServerResponse = new AuthorizationServerResponse(EMPTY_RESPONSE);
                    this.configurationPool.markLastUsedAsBad();
                    ++retryCount;
                } catch (AuthServerException var43) {
                    LOGGER.error(var43.getMessage(), var43);

                    try {
                        //                        if (ObjectUtils.isEqual(CommonContext.CLIENT_VERSION, "99.99.99")
                        // && isRememberMeTokenAuthorization) {
                        //                            writeToRememberMeLog(previousRememberMeToken, (String)this
                        // .srp6PlatformSessionKey.get(), false, deviceId, rememberMe, login, platform, deviceName,
                        // deviceOS, this.version, sessionId, authorizationUrl);
                        //                        }
                    } catch (Throwable var38) {
                        LOGGER.error(var38.getMessage(), var38);
                    }

                    AuthServerResponse httpServerResponse = var43.getAuthServerResponse();
                    if (httpServerResponse != null) {
                        int responseCode = httpServerResponse.getResponseCode();
                        AuthorizationServerResponseCode
                            authorizationServerResponseCode
                            = AuthorizationServerResponseCode.fromValue(responseCode);
                        authorizationServerResponse = new AuthorizationServerResponse(authorizationServerResponseCode);

                        try {
                            Map<String, List<String>> responseHeaderFields = httpServerResponse.getHeaderFields();
                            List<String> detailedStatusCodeInfo = (List) responseHeaderFields.get("DetailedStatusCode");
                            if (detailedStatusCodeInfo != null) {
                                if (detailedStatusCodeInfo.size() == 1) {
                                    serverResponse = (String) detailedStatusCodeInfo.get(0);
                                    int detailedStatusCode = Integer.valueOf(serverResponse);
                                    authorizationServerResponse.setDetailedStatusCode(detailedStatusCode);
                                } else if (detailedStatusCodeInfo.size() > 1) {
                                    LOGGER.error("Cannot get the DetailedStatusCode, size="
                                                 + detailedStatusCodeInfo.size());
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error occurred...", e);
                        }
                    } else {
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode.UNKNOWN_RESPONSE);
                    }
                    break;
                } catch (SRP6Exception e) {
                    LOGGER.error("Error occurred...", e);
                    authProtocol = e.getCauseType();
                    switch (authProtocol) {
                        case BAD_CREDENTIALS:
                            authorizationServerResponse = new AuthorizationServerResponse(
                                AuthorizationServerResponseCode.BAD_CREDENTIALS);
                            break label191;
                        case BAD_PUBLIC_VALUE:
                            authorizationServerResponse = new AuthorizationServerResponse(
                                AuthorizationServerResponseCode.BAD_PUBLIC_VALUE);
                            break label191;
                        case TIMEOUT:
                            authorizationServerResponse = new AuthorizationServerResponse(
                                AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT);
                            break label191;
                        default:
                            authorizationServerResponse = new AuthorizationServerResponse(
                                AuthorizationServerResponseCode.UNKNOWN_RESPONSE);
                            break label191;
                    }
                } catch (IOException e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerResponse = new AuthorizationServerResponse(ERROR_IO);
                    break;
                } catch (InterruptedException e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
                    break;
                } catch (Throwable e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                    return authorizationServerResponse;
                }
            }

            if (!this.useTheSameAuthorizationURL(authorizationServerResponse.getResponseCode(), sameAuthUrlAttempts)) {
                this.resetSameAuthUrlAttempts(sameAuthUrlAttempts);
                ++retryCount;
                if (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
                    this.configurationPool.markLastUsedAsBad();
                }
            }
        }
    }

    public AuthorizationServerResponse getAPIsAndTicketUsingLogin(String login,
                                                                  String password,
                                                                  String sessionId,
                                                                  boolean encodePassword,
                                                                  String platform) {

        AuthorizationServerResponse authorizationServerResponse = null;
        int retryCount = 0;

        while (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
            if (this.firstApiRequest) {
                this.firstApiRequest = false;
            } else {
                try {
                    sessionId = this.getNewSessionId();
                } catch (Throwable e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                    return authorizationServerResponse;
                }
            }

            try {


                String formedUrl = UrlProvider.getFormedUrl(this.getBaseUrl(),
                                                            login,
                                                            password,
                                                            sessionId,
                                                            encodePassword,
                                                            platform,
                                                            "",
                                                            this.version);
                URL url = new URL(formedUrl);
                authorizationServerResponse = getAuthorizationServerResponse(url);
                if (authorizationServerResponse.isOK()) {
                    String
                        fastestAPIAndTicket
                        = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                    authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                }
            } catch (MalformedURLException e) {
                LOGGER.error("Error occurred...", e);
                return new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_URL);

            } catch (UnsupportedEncodingException e) {
                LOGGER.error("Error occurred...", e);
                return new AuthorizationServerResponse(AuthorizationServerResponseCode.ERROR_INIT);

            } catch (NoSuchAlgorithmException e) {
                LOGGER.error("Error occurred...", e);

                return new AuthorizationServerResponse(AuthorizationServerResponseCode.ERROR_INIT);

            } catch (IOException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse = new AuthorizationServerResponse(ERROR_IO);
            } catch (InterruptedException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                return authorizationServerResponse;
            }

            ++retryCount;
            if (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
                this.configurationPool.markLastUsedAsBad();
            }
        }

        return authorizationServerResponse;
    }


    public AuthorizationServerResponse getAPIsAndTicketUsingLogin(String login,
                                                                  String password,
                                                                  String captchaId,
                                                                  String pin,
                                                                  String sessionId,
                                                                  String platform) {

        AuthorizationServerResponse authorizationServerResponse = null;
        int retryCount = 0;

        while (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
            if (this.firstApiRequest) {
                this.firstApiRequest = false;
            } else {
                try {
                    sessionId = this.getNewSessionId();
                } catch (Throwable e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                    return authorizationServerResponse;
                }
            }

            try {

                String urlString = UrlProvider.getFormedUrl(this.getBaseUrl(),
                                                            login,
                                                            password,
                                                            sessionId,
                                                            true,
                                                            platform,
                                                            captchaId,
                                                            this.version);
                if (captchaId != null) {
                    urlString = urlString + "&verbum_id=" + captchaId;
                }

                if (pin != null) {
                    urlString = urlString + "&sententia=" + URLEncoder.encode(pin, MESSAGE_DIGEST_ENCODING);
                }

                URL url = new URL(urlString);
                authorizationServerResponse = getAuthorizationServerResponse(url);
                if (authorizationServerResponse.isOK()) {
                    String
                        fastestAPIAndTicket
                        = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                    authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                }
            } catch (MalformedURLException | NoSuchAlgorithmException var14) {
                LOGGER.error(var14.getMessage(), var14);
                if (var14 instanceof MalformedURLException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_URL);
                }

                if (var14 instanceof NoSuchAlgorithmException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.ERROR_INIT);
                }

                return authorizationServerResponse;
            } catch (IOException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse = new AuthorizationServerResponse(ERROR_IO);
            } catch (InterruptedException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                return authorizationServerResponse;
            }

            ++retryCount;
            if (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
                this.configurationPool.markLastUsedAsBad();
            }
        }

        return authorizationServerResponse;
    }

    public AuthorizationServerResponse getAPIsAndTicketUsingRelogin(String login,
                                                                    String oldTicket,
                                                                    String sessionId,
                                                                    String platform) {

        AuthorizationServerResponse authorizationServerResponse = null;
        int retryCount = 0;

        while (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
            try {

                String formedUrl = UrlProvider.getFormedUrl999(this.getBaseUrl(),
                                                               login,
                                                               oldTicket,
                                                               sessionId,
                                                               platform,
                                                               this.version);
                URL url = new URL(formedUrl);
                authorizationServerResponse = getAuthorizationServerResponse(url);
                if (authorizationServerResponse.isOK()) {
                    String
                        fastestAPIAndTicket
                        = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                    authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                }
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("Error occurred...", e);

                return new AuthorizationServerResponse(AuthorizationServerResponseCode.ERROR_INIT);

            } catch (MalformedURLException e) {
                LOGGER.error("Error occurred...", e);
                return new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_URL);

            } catch (IOException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse = new AuthorizationServerResponse(ERROR_IO);
            } catch (InterruptedException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                return authorizationServerResponse;
            }

            ++retryCount;
            if (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
                this.configurationPool.markLastUsedAsBad();
            }
        }

        return authorizationServerResponse;
    }


    public AuthorizationServerPinRequiredResponse checkIfPinRequired(String loginName) {

        AuthorizationServerPinRequiredResponse authorizationServerResponse = null;
        int retryCount = 0;

        while (this.continueCheckIfPinRequired(retryCount, authorizationServerResponse)) {
            try {
                URL baseUrl = this.getBaseUrl();
                String baseUrlAsString = baseUrl.toString();
                SingleRequestAuthClient
                    client
                    = new SingleRequestAuthClient(new LoginInfoClientProtocol(((LoginInfoParamsBuilder) (
                        (LoginInfoParamsBuilder) ((LoginInfoParamsBuilder) (new LoginInfoParamsBuilder())
                    .setAccountType(AccountType.TRADER)).setAuthTransport(new HttpAuthTransport())).setHttpBase(
                    baseUrlAsString)).setLogin(loginName).build()));
                SingleServerResponse singleServerResponse = client.getServerResponse();
                JSONObject responseAsJson = singleServerResponse.getResponse();
                authorizationServerResponse = new AuthorizationServerPinRequiredResponse(responseAsJson);
            } catch (MalformedURLException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode.BAD_URL);
            } catch (IOException var10) {
                LOGGER.error(var10.getMessage(), var10);
                authorizationServerResponse = new AuthorizationServerPinRequiredResponse(ERROR_IO);
            } catch (InterruptedException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (AuthServerException e) {
                LOGGER.error("Error occurred...", e);
                AuthServerResponse authServerResponse = e.getAuthServerResponse();
                if (authServerResponse != null) {
                    int responseCode = authServerResponse.getResponseCode();
                    String responseMessage = authServerResponse.getResponse();
                    authorizationServerResponse = new AuthorizationServerPinRequiredResponse(responseCode,
                                                                                             responseMessage);
                } else {
                    authorizationServerResponse = new AuthorizationServerPinRequiredResponse(
                        AuthorizationServerResponseCode.INTERNAL_ERROR);
                }
            } catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
            }

            ++retryCount;
            if (this.continueCheckIfPinRequired(retryCount, authorizationServerResponse)) {
                this.configurationPool.markLastUsedAsBad();
            }
        }

        return authorizationServerResponse;
    }

    public AuthorizationServerWLBOResponse getResponeForTheWLBOAutoLogin(String authToken) {

        AuthorizationServerWLBOResponse authorizationServerWLBOResponse = null;
        if (this.configurationPool.size() == 0) {
            LOGGER.error("No authorization url provided.");
            authorizationServerWLBOResponse
                = new AuthorizationServerWLBOResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
            return authorizationServerWLBOResponse;
        } else {
            for (int retryCount = 0; retryCount < this.configurationPool.size(); ++retryCount) {
                try {
                    String baseUrl = this.getBaseUrl().toString();
                    String slash = "";
                    if (!baseUrl.endsWith("/")) {
                        slash = "/";
                    }

                    StringBuilder authURLBuf = new StringBuilder(256);
                    authURLBuf.append(baseUrl).append(slash).append(STS_WLBO).append("&").append("indicium=").append(
                        authToken);
                    LOGGER.debug("Authorization url={}", authURLBuf);
                    URL stsTokenURL = new URL(authURLBuf.toString());
                    String response = null;
                    BufferedReader reader = null;
                    int authorizationServerResponseCode = 0;
                    HttpURLConnection connection = getConnection(stsTokenURL);

                        authorizationServerResponseCode =  connection.getResponseCode();


                    try {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        response = reader.readLine();
                    } catch (Throwable var24) {
                        ;
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (Throwable e) {
                                LOGGER.error("Error occurred...", e);
                            }
                        }

                    }

                    authorizationServerWLBOResponse = new AuthorizationServerWLBOResponse(response,
                                                                                          authorizationServerResponseCode);
                    if (authorizationServerWLBOResponse.isOK()) {
                        return authorizationServerWLBOResponse;
                    }

                    LOGGER.error("Wrong response, URL={}, response={}"
                                 ,authURLBuf,authorizationServerWLBOResponse);
                } catch (MalformedURLException e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerWLBOResponse = new AuthorizationServerWLBOResponse(
                        AuthorizationServerResponseCode.BAD_URL);
                    return authorizationServerWLBOResponse;
                } catch (IOException e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerWLBOResponse = new AuthorizationServerWLBOResponse(ERROR_IO);
                } catch (Throwable e) {
                    LOGGER.error("Error occurred...", e);
                    authorizationServerWLBOResponse = new AuthorizationServerWLBOResponse(
                        AuthorizationServerResponseCode.INTERNAL_ERROR);
                    return authorizationServerWLBOResponse;
                }

                this.configurationPool.markLastUsedAsBad();
            }

            return authorizationServerWLBOResponse;
        }
    }

    public String getFeedUrlAndTicket(String login, String platformTicket, String sessionId)
        throws IOException, NoSuchAlgorithmException {

        String result = null;
        return (String) result;
    }

    public Properties getAllProperties(String login, String ticket, String sessionId)
        throws IOException, NoSuchAlgorithmException, Exception {

        int retryCount = 1;
        AuthorizationServerPropertiesResponse propertiesResponse = null;
        SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formedUrl = null;

        Properties platformProperties;
        while (propertiesResponse == null || !propertiesResponse.isOK()) {
            try {


                formedUrl = UrlProvider.getFormedUrl988(this.getBaseUrl(), ticket, login, sessionId);
                URL url = new URL(formedUrl);
                LOGGER.info("Account settings request: retry count = {}, time = {}",
                            retryCount,
                            DATE_FORMAT.format(System.currentTimeMillis()));
                LOGGER.debug(">> [{}]", url);
                propertiesResponse = requestPlatformProperties(url);
                if (propertiesResponse.isOK()) {
                    LOGGER.info("Account settings received: retry count = {}, time = {}",
                                retryCount,
                                DATE_FORMAT.format(System.currentTimeMillis()));
                    platformProperties = propertiesResponse.getPlatformProperties();
                    if (authClient.isSnapshotVersion()) {
                        LOGGER.debug("Account settings << [{}]", platformProperties);
                    }

                    try {
                        int[] times = (int[]) ((int[]) platformProperties.get("marketTimes"));
                        if (times != null) {
                            LOGGER.debug("MarketTimes from "
                                         + times[0]
                                         + ":"
                                         + times[1]
                                         + " to "
                                         + times[2]
                                         + ":"
                                         + times[3]);
                        }
                    } catch (NullPointerException e) {
                        LOGGER.error("Error occurred...", e);
                    }
                    break;
                }

                this.configurationPool.markLastUsedAsBad();
            } catch (IOException e) {
                LOGGER.info("Cannot get the platform properties, url={}", formedUrl);
                propertiesResponse = new AuthorizationServerPropertiesResponse(ERROR_IO);
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error("Error occurred...", e);
            } catch (Throwable e) {
                LOGGER.info("Cannot get the platform properties, url={}", formedUrl);
                propertiesResponse
                    = new AuthorizationServerPropertiesResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error("Error occurred...", e);
            }

            if (propertiesResponse.getResponseCode() == AuthorizationServerResponseCode.TICKET_EXPIRED) {
                handleNoAccountSettings(propertiesResponse.getResponseCode().getError());
            }

            if (retryCount >= this.configurationPool.size()) {
                AuthorizationServerResponseCode responseCode = propertiesResponse.getResponseCode();
                if (responseCode != null) {
                    String errorMessage = responseCode.getError();
                    if (responseCode != AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED) {
                        errorMessage = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED.getMessage()
                                       + ". "
                                       + errorMessage;
                    }

                    handleNoAccountSettings(errorMessage);
                } else {
                    handleNoAccountSettings((String) null);
                }
            }

            ++retryCount;

            try {
                Thread.sleep(3000L);
            } catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
            }
        }

        boolean printProperties = false;
        if (LOGGER.isDebugEnabled() && printProperties && propertiesResponse != null) {
            propertiesResponse.printProperties();
        }

        platformProperties = propertiesResponse.getPlatformProperties();
        //AvailableInstrumentProvider.INSTANCE.init(platformProperties);
        return platformProperties;
    }


    public URL getBaseUrl() {

        return this.configurationPool.get();
    }

    public String getLoginUrl() {

        return this.configurationPool.get().toString();
    }

    public String getVersion() {

        return this.version;
    }

    public boolean isSnapshotVersion() {

        return this.version == null || this.version.isEmpty() || this.version.equals("99.99.99");
    }

    private String getNewSessionId() throws Exception {

        String sessionId = null;

        try {
            //            ISession session = PlatformServiceProvider.getInstance().getSession();
            //            if (session != null) {
            //                session.recreateSessionId();
            //                sessionId = session.getSessionId();
            //            }
        } catch (Throwable e) {
            LOGGER.error("Error occurred...", e);
        }

        if (sessionId == null) {
            throw new Exception("The new sessionId is null");
        } else {
            return sessionId;
        }
    }

    private void retrieveDataForALS(ServerResponseObject<Step3ServerResponse> response) {

        try {
            String clientIpAddress = "n/a";
            String clientCountry = "n/a";
            Map<String, List<String>> responseHeaderFields = response.getResponseHeaderFields();
            List<String> ipHttpHeaderList = (List) responseHeaderFields.get(IP_HTTP_HEADER);
            if (ipHttpHeaderList != null && ipHttpHeaderList.size() > 0) {
                clientIpAddress = (String) ipHttpHeaderList.get(0);
                if (clientIpAddress == null || clientIpAddress.trim().isEmpty()) {
                    clientIpAddress = "n/a";
                }
            }

            List<String> clientCountryList = (List) responseHeaderFields.get(CLIENT_COUNTRY);
            if (clientCountryList != null && clientCountryList.size() > 0) {
                clientCountry = (String) clientCountryList.get(0);
                if (clientCountry == null || clientCountry.trim().isEmpty()) {
                    clientCountry = "n/a";
                }
            }

            try {
              /*  PlatformServiceProvider platformServiceProvider = PlatformServiceProvider.getInstance();
                ISessionBean sessionBean = platformServiceProvider.getSessionBean();
                sessionBean.setClientCountry(clientCountry);
                sessionBean.setClientIpAddress(clientIpAddress);*/
            } catch (Exception e) {
                LOGGER.error("Error occurred...", e);
            }

            // authClient.ipInfoAlsMessageBean = AlsMessageFactory.createIPInfoAlsMessageBean(clientCountry,
            // clientIpAddress);

            authClient.ipInfoAlsMessageBean = new AlsMessageBean();

        } catch (Exception var10) {
            LOGGER.error("Cannot get the Client-IP and Client-country.");
            LOGGER.error(var10.getMessage(), var10);
        }

    }

    private byte[] unzip(byte[] compressed) throws IllegalStateException {

        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream bytesCollector = new ByteArrayOutputStream();) {

            byte[] buff = new byte[4096];

            int i;
            while ((i = gzipIn.read(buff)) != -1) {
                bytesCollector.write(buff, 0, i);
            }

            return bytesCollector.toByteArray();


        } catch (IOException e) {
            LOGGER.error("Error occurred...", e);
        }

        throw new IllegalStateException("Error while unzip compressed byte array");
    }

    public void setPlatformSessionKey(String sessionKey) {

        this.srp6PlatformSessionKey.set(sessionKey);
    }


    private static class DummyHostNameVerifier implements HostnameVerifier {
        private DummyHostNameVerifier() {

        }

        public boolean verify(String hostname, SSLSession session) {

            LOGGER.debug("Verify : {} @ {}", hostname, session);
            return true;
        }
    }

    private static class SameAuthUrlAttemptsBean {
        private int maxSameAuthUrlAttempts = 1;
        private int currentUuthUrlAttempts = 0;

        public SameAuthUrlAttemptsBean(int maxSameAuthUrlAttempts) {

            this.maxSameAuthUrlAttempts = maxSameAuthUrlAttempts;
        }

        public boolean isNextAttemptAvailable() {

            return ++this.currentUuthUrlAttempts <= this.maxSameAuthUrlAttempts;
        }

        public void resetCurrentAttempts() {

            this.currentUuthUrlAttempts = 0;
        }
    }
}
