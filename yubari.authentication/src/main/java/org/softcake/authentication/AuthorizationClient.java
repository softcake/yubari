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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.UUID;
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
    public static final int SSL_PORT = 443;
    public static final String AUTH_API_URLS = "authApiURLs";
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
    private static AuthorizationClient instance;
    private static Object mutex = new Object();

    private final AuthorizationConfigurationPool configurationPool = new AuthorizationConfigurationPool();
    private final String version;
    private volatile AlsMessageBean ipInfoAlsMessageBean = null;
    private volatile boolean firstApiRequest = true;
    private AtomicReference<String> srp6PlatformSessionKey = new AtomicReference();

    private AuthorizationClient() {
        this.version = "99.99.99";
    }

    private AuthorizationClient(Collection<String> authServerUrls, String version) throws MalformedURLException {


        for (final String url : authServerUrls) {
            this.configurationPool.add(url);
        }

        this.version = version;
    }

    public static synchronized AuthorizationClient getInstance(Collection<String> authServerUrls, String version) {

        AuthorizationClient result = instance;
        if (result == null) {
            synchronized (mutex) {
                result = instance;
                if (result == null) {
                    try {
                        instance = result = new AuthorizationClient(authServerUrls, version);

                    } catch (MalformedURLException var4) {
                        LOGGER.error(var4.getMessage(), var4);
                    }


                }else{
                    try {
                        result.updateConfigurationPool(authServerUrls);
                    } catch (MalformedURLException var3) {
                        LOGGER.error(var3.getMessage(), var3);
                    }
                }
            }
        } else {
            try {
                result.updateConfigurationPool(authServerUrls);
            } catch (MalformedURLException var3) {
                LOGGER.error(var3.getMessage(), var3);
            }
        }
        return result;
    }

    public static synchronized AuthorizationClient getInstance() {

        AuthorizationClient result = instance;
        if (result == null) {
            synchronized (mutex) {
                result = instance;
                if (result == null) { instance = result = new AuthorizationClient(); }
            }
        }
        return result;

    }


/*
    public static synchronized AuthorizationClient getInstance() {

        if (instance == null) {
            throw new IllegalStateException("The AuthorizationClient is not initialized.");
        } else {
            return instance;
        }
    }*/

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
        int semicolonIndex = url.indexOf(':');
        if (semicolonIndex != -1) {
            host = url.substring(0, semicolonIndex);
            if (semicolonIndex + 1 >= url.length()) {
                LOGGER.warn("Port is not set, using default {}", SSL_PORT);
                port = SSL_PORT;
            } else {
                port = Integer.parseInt(url.substring(semicolonIndex + 1));
            }
        } else {
            LOGGER.warn("Port is not set, using default {}", SSL_PORT);
            host = url;
            port = SSL_PORT;
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

        if (instance.ipInfoAlsMessageBean != null) {
            instance.ipInfoAlsMessageBean.addParameter("PING_TIME", bestTime).addParameter("API_IP_ADDRESS", bestURL);
            //  PlatformServiceProvider.getInstance().setIpInfoAlsMessageBean(instance.ipInfoAlsMessageBean);
            instance.ipInfoAlsMessageBean = null;
        } else {
            LOGGER.error("The ipInfoAlsMessageBean is null.");
        }

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
            StringBuilder buf = new StringBuilder(128);
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
            } catch (NumberFormatException var23) {
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error(var23.getMessage(), var23);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {

                }

            }

            ++retryCount;
        }

        return output;
    }


    /**
     * @param retryCount
     * @param response
     * @return
     */
    private boolean continueObtainingAPIServers(int retryCount, AuthorizationServerResponse response) {

        return response == null || (retryCount < this.configurationPool.size() && response.isEmptyResponse()
                                    || retryCount < this.configurationPool.size() && (response.getResponseCode()
                                                                                      == TOO_MANY_REQUESTS
                                                                                      || response.getResponseCode()
                                                                                         == ERROR_IO
                                                                                      || response.getResponseCode()
                                                                                         == EMPTY_RESPONSE
                                                                                      || response.getResponseCode()
                                                                                         == NOT_FOUND
                                                                                      || response.getResponseCode()
                                                                                         == WRONG_AUTH_RESPONSE
                                                                                      || response.getResponseCode()
                                                                                         ==
                                                                                         AUTHORIZATION_REQUEST_TIMEOUT
                                                                                      || response.getResponseCode()
                                                                                         == AUTHORIZATION_TIMEOUT
                                                                                      || response.getResponseCode()
                                                                                         == NO_PROPERTIES_RECEIVED
                                                                                      || response.getResponseCode()
                                                                                         == SERVICE_UNAVAILABLE
                                                                                      || response.getResponseCode()
                                                                                         == UNKNOWN_RESPONSE
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
                                               Map<AuthorizationServerResponseCode, SameAuthUrlAttemptsBean> attempts) {

        SameAuthUrlAttemptsBean authUrlAttemptsBean = attempts.get(authorizationServerResponseCode);
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
                    if (instance.isSnapshotVersion()) {
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
        SameAuthUrlAttemptsBean authUrlAttemptsBean = null;
        Map<AuthorizationServerResponseCode, SameAuthUrlAttemptsBean> sameAuthUrlAttempts = new HashMap();
        authUrlAttemptsBean = new SameAuthUrlAttemptsBean(3);
        sameAuthUrlAttempts.put(AUTHORIZATION_REQUEST_TIMEOUT, authUrlAttemptsBean);
        authUrlAttemptsBean = new SameAuthUrlAttemptsBean(3);
        sameAuthUrlAttempts.put(AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT, authUrlAttemptsBean);
        authUrlAttemptsBean = new SameAuthUrlAttemptsBean(2);
        sameAuthUrlAttempts.put(AuthorizationServerResponseCode.SERVICE_UNAVAILABLE, authUrlAttemptsBean);


        do {

            if (this.firstApiRequest) {
                this.firstApiRequest = false;
            } else {
                try {
                    sessionId = this.getNewSessionId();
                } catch (Throwable var41) {
                    LOGGER.error(var41.getMessage(), var41);
                    return new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);

                }
            }


            String authorizationUrl = this.getBaseUrl().toString();

            AbstractBusinessSRPAuthClientParamsBuilder<?, ?> builder = null;
            if (isRememberMeTokenAuthorization) {
                builder = new RememberMeClientParamsBuilder();
            } else {
                builder = new ApiClientParamsBuilder();
            }
            builder.setAuthTransport(new HttpAuthTransport())
                   .setHttpBase(authorizationUrl)
                   .setLogin(login)
                   .setPassword(password)
                   .setCaptchaId(captchaId)
                   .setPin(pin)
                   .setAccountType(AccountType.TRADER)
                   .setSessionId(sessionId)
                   .setPlatform(platform)
                   .setPlatformVersion(this.version)
                   .setWillPing(true)
                   .setNeedSettings(true)
                   .setUserAgent(USER_AGENT_VALUE)
                   .setLogger(LOGGER);

            if (isRememberMeTokenRemove) {
                ((ApiClientParamsBuilder) builder).setRememberMe(false)
                                                  .setDeviceId(deviceId)
                                                  .setDeviceName(deviceName)
                                                  .setDeviceOS(deviceOS)
                                                  .setAccountType(AccountType.TRADER);
            }

            if (isRememberMeTokenRequest) {
                ((ApiClientParamsBuilder) builder).setRememberMe(rememberMe).setDeviceId(deviceId).setDeviceName(
                    deviceName).setDeviceOS(deviceOS).setAccountType(AccountType.TRADER);
            }

            if (isRememberMeTokenAuthorization) {
                ((RememberMeClientParamsBuilder) builder).setDeviceId(deviceId).setDeviceName(deviceName).setDeviceOS(
                    deviceOS).setAccountType(AccountType.TRADER).setPassword(previousRememberMeToken);
            }


            try {


                Object authProtocol2;
                if (isRememberMeTokenAuthorization) {
                    authProtocol2
                        = new RememberMeClientProtocol((RememberMeClientParamsBuilder.RememberMeClientParams) builder
                        .build());
                } else {
                    authProtocol2 = new ApiClientProtocol(((ApiClientParamsBuilder) builder).build());
                }

                SRPAuthClient client = new SRPAuthClient((ISRPClientProtocol) authProtocol2, LOGGER);
                ServerResponseObject<Step3ServerResponse> result = client.authenticate();

                if (result != null) {
                    String newRememberMeToken = result.getPasswordServerResponse().getSessionKey();
                    this.srp6PlatformSessionKey.set(newRememberMeToken);
                    this.retrieveDataForALS(result);
                    Step3ServerResponse passwordServerResponse = result.getPasswordServerResponse();
                    String serverResponse = passwordServerResponse.getServerResponse();
                    String sessionKey = passwordServerResponse.getSessionKey();
                    // TODO  JSONObject jsonObject = new JSONObject(serverResponse, true);
                    JSONObject jsonObject = new JSONObject(serverResponse);
                    JSONArray jsonArray = jsonObject.getJSONArray(AUTH_API_URLS);
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
                        ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(decodedBytes));
                        properties = (Properties) inputStream.readObject();
                        if (instance.isSnapshotVersion()) {
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

                        fastestAPIAndTicket = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                        authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                    } else {
                        authorizationServerResponse = new AuthorizationServerResponse(EMPTY_RESPONSE);
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

                } else {
                    authorizationServerResponse = new AuthorizationServerResponse(EMPTY_RESPONSE);
                }


            } catch (AuthServerException e) {
                LOGGER.error("Error occurred...", e);

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

                AuthServerResponse authServerResponse = e.getAuthServerResponse();
                if (authServerResponse != null) {

                    AuthorizationServerResponseCode
                        authorizationServerResponseCode
                        = AuthorizationServerResponseCode.fromValue(authServerResponse.getResponseCode());
                    authorizationServerResponse = new AuthorizationServerResponse(authorizationServerResponseCode);


                    Map<String, List<String>> responseHeaderFields = authServerResponse.getHeaderFields();
                    List<String> detailedStatusCodeInfo = responseHeaderFields.get("DetailedStatusCode");
                    if (detailedStatusCodeInfo != null) {
                        if (detailedStatusCodeInfo.size() == 1) {

                            int detailedStatusCode = Integer.parseInt(detailedStatusCodeInfo.get(0));
                            authorizationServerResponse.setDetailedStatusCode(detailedStatusCode);
                        } else if (detailedStatusCodeInfo.size() > 1) {
                            LOGGER.error("Cannot get the DetailedStatusCode, size={}", detailedStatusCodeInfo.size());
                        }
                    }

                } else {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.UNKNOWN_RESPONSE);
                }

            } catch (SRP6Exception e) {
                LOGGER.error("Error occurred...", e);

                switch (e.getCauseType()) {
                    case BAD_CREDENTIALS:
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_CREDENTIALS);
                        break;
                    case BAD_PUBLIC_VALUE:
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_PUBLIC_VALUE);
                        break;
                    case TIMEOUT:
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT);
                        break;
                    default:
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode.UNKNOWN_RESPONSE);

                }
            } catch (IOException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse = new AuthorizationServerResponse(ERROR_IO);

            } catch (InterruptedException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);

            } catch (NoSuchAlgorithmException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.ERROR_INIT);

            } catch (ApiUrlServerFormatException e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.WRONG_AUTH_RESPONSE);

            } catch (Exception e) {
                LOGGER.error("Error occurred...", e);
                //TODO Exception from SPRClient Autehtication; must be spezific
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode
                                                          .AUTHENTICATION_AUTHORIZATION_ERROR);

            }
           /* catch (Throwable e) {
                LOGGER.error("Error occurred...", e);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                return authorizationServerResponse;
            }*/


            if (!this.useTheSameAuthorizationURL(authorizationServerResponse.getResponseCode(), sameAuthUrlAttempts)) {
                this.resetSameAuthUrlAttempts(sameAuthUrlAttempts);
                ++retryCount;
                if (this.continueObtainingAPIServers(retryCount, authorizationServerResponse)) {
                    this.configurationPool.markLastUsedAsBad();
                } else {
                    return authorizationServerResponse;
                }
            }
        } while (true);
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

    public String getFeedUrlAndTicket(String login, String platformTicket, String sessionId)
        throws IOException, NoSuchAlgorithmException {

        String result = null;
        return (String) result;
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
            //         ISession session = PlatformServiceProvider.getInstance().getSession();
            //            if (session != null) {
            //             session.recreateSessionId();
            //                sessionId = session.getSessionId();
            //            }
        } catch (Throwable e) {
            LOGGER.error("Error occurred...", e);
        }

        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            return sessionId;
            // throw new Exception("The new sessionId is null");
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

            // instance.ipInfoAlsMessageBean = AlsMessageFactory.createIPInfoAlsMessageBean(clientCountry,
            // clientIpAddress);

            instance.ipInfoAlsMessageBean = new AlsMessageBean();

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

            ++this.currentUuthUrlAttempts;
            return this.currentUuthUrlAttempts <= this.maxSameAuthUrlAttempts;
        }

        public void resetCurrentAttempts() {

            this.currentUuthUrlAttempts = 0;
        }
    }
}
