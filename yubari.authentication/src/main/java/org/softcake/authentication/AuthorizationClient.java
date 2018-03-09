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




import static org.softcake.authentication.AuthorizationServerResponseCode.*;

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
import com.google.common.base.Preconditions;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
        BufferedReader reader = null;
        AuthorizationServerResponse authorizationServerResponse = null;

        try {
            int authorizationServerResponseCode = 0;
            URLConnection connection = getConnection(url);
            if (connection instanceof HttpURLConnection) {
                authorizationServerResponseCode = ((HttpURLConnection) connection).getResponseCode();
            }

            try {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                response = reader.readLine();
            } catch (IOException var10) {
                ;
            }

            authorizationServerResponse = new AuthorizationServerResponse(response, authorizationServerResponseCode);
        } finally {
            if (reader != null) {
                reader.close();
            }

        }

        LOGGER.debug("<< [{}]", response);
        return authorizationServerResponse;
    }

    public static String selectFastestAPIServer(String apiServersAndTicket)
        throws ApiUrlServerFormatException, InterruptedException, IOException {

        LOGGER.info("Selecting the best server...");
        Matcher matcher = null;
        boolean matches = false;

        try {
            matcher = RESULT_PATTERN.matcher(apiServersAndTicket);
            matches = matcher.matches();
        } catch (Throwable var22) {
            String message = var22.getMessage();
            if (message == null || message.isEmpty()) {
                message = "";
            }

            throw new ApiUrlServerFormatException("1. Wrong format of api url." + message, var22);
        }

        if (!matches) {
            throw new ApiUrlServerFormatException("2. Wrong format of api url:" + apiServersAndTicket);
        } else {
            long pingStartTime = 0L;
            long pingEndTime = 0L;

            String apiURL;
            try {
                String apiUrls = matcher.group(1);
                Map<String, InetSocketAddress> addresses = new LinkedHashMap();
                StringTokenizer tokenizer = new StringTokenizer(apiUrls, "@");
                if (tokenizer.countTokens() == 0) {
                    throw new ApiUrlServerFormatException("3. Wrong format of api url:" + apiServersAndTicket);
                } else if (tokenizer.countTokens() == 1) {
                    return apiServersAndTicket;
                } else {
                    String host;
                    int port;
                    for (; tokenizer.hasMoreTokens(); addresses.put(apiURL, new InetSocketAddress(host, port))) {
                        apiURL = tokenizer.nextToken();
                        int semicolonIndex = apiURL.indexOf(58);
                        if (semicolonIndex != -1) {
                            host = apiURL.substring(0, semicolonIndex);
                            if (semicolonIndex + 1 >= apiURL.length()) {
                                LOGGER.warn("Port is not set, using default 443");
                                port = 443;
                            } else {
                                port = Integer.parseInt(apiURL.substring(semicolonIndex + 1));
                            }
                        } else {
                            LOGGER.warn("Port is not set, using default 443");
                            host = apiURL;
                            port = 443;
                        }
                    }

                    SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                    pingStartTime = System.currentTimeMillis();
                    LOGGER.info("The ping start time=" + DATE_FORMAT.format(pingStartTime));
                    //                    SLogger.add(ISSUE.JFOREX_9102, (Object)null, LogName.AUTHORIZATION_CLIENT,
                    // ((StringBuffer)SLogger.STRING_BUFFER.get()).append
                    // ("AuthorizationClient#selectFastestAPIServer, before ping API servers"), true);
                    Map<String, Long[]> pingResult = Ping.ping(addresses);
                    pingEndTime = System.currentTimeMillis();
                    LOGGER.info("The ping end time="
                                + DATE_FORMAT.format(pingEndTime)
                                + ", the ping processing time=["
                                + (pingEndTime - pingStartTime)
                                + "]ms");
                    Map<String, Long> pingAverages = new LinkedHashMap();
                    Iterator var29 = pingResult.entrySet().iterator();

                    while (var29.hasNext()) {
                        Entry<String, Long[]> entry = (Entry) var29.next();
                        Long[] results = (Long[]) entry.getValue();
                        long averageTime = 0L;
                        Long[] var18 = results;
                        int var19 = results.length;

                        for (int var20 = 0; var20 < var19; ++var20) {
                            Long time = var18[var20];
                            if (time == Long.MIN_VALUE) {
                                if (averageTime > 0L) {
                                    averageTime = -averageTime;
                                }

                                averageTime += -2147483648L;
                            } else if (time == 9223372036854775807L) {
                                if (averageTime < 0L) {
                                    averageTime += -2147483648L;
                                } else {
                                    averageTime += 2147483647L;
                                }
                            } else if (averageTime < 0L) {
                                averageTime -= time;
                            } else {
                                averageTime += time;
                            }
                        }

                        averageTime /= (long) results.length;
                        pingAverages.put(entry.getKey(), averageTime);
                    }

                    String mainURL = null;
                    Long mainTime = null;
                    String bestURL = null;
                    Long bestTime = null;
                    Iterator var17 = pingAverages.entrySet().iterator();

                    while (var17.hasNext()) {
                        Entry<String, Long> entry = (Entry) var17.next();
                        if (mainURL == null) {
                            bestURL = mainURL = (String) entry.getKey();
                            bestTime = mainTime = (Long) entry.getValue();
                        } else {
                            Long time = (Long) entry.getValue();
                            if (mainTime >= 0L) {
                                if (time < 0L) {
                                    break;
                                }

                                if (mainTime > time
                                    && (mainTime - time) * 100L / mainTime > 10L
                                    && mainTime - time > 50L
                                    && bestTime > time) {
                                    bestURL = (String) entry.getKey();
                                    bestTime = time;
                                }
                            } else if (time >= 0L) {
                                if (bestTime < 0L || bestTime > time) {
                                    bestURL = (String) entry.getKey();
                                    bestTime = (Long) entry.getValue();
                                }
                            } else if (Math.abs(mainTime) > Math.abs(time)
                                       && (Math.abs(mainTime) - Math.abs(time)) * 100L / Math.abs(mainTime) > 10L
                                       && Math.abs(mainTime) - Math.abs(time) > 50L
                                       && bestTime < 0L
                                       && Math.abs(bestTime) > Math.abs(time)) {
                                bestURL = (String) entry.getKey();
                                bestTime = time;
                            }
                        }
                    }

                    LOGGER.debug("Best api url [" + bestURL + "] with time [" + bestTime + "]");
                    addIPAndBestPing(bestTime, bestURL);
                    String address = bestURL + "@" + matcher.group(7);
                    if (validAPIServerAddressFormat(address)) {
                        return address;
                    } else {
                        throw new ApiUrlServerFormatException("4. Wrong format of api url:" + address);
                    }
                }
            } catch (InterruptedException var23) {
                //                SLogger.add(ISSUE.JFOREX_9102, (Object)null, LogName.AUTHORIZATION_CLIENT, (
                // (StringBuffer)SLogger.STRING_BUFFER.get()).append("AuthorizationClient#selectFastestAPIServer, the
                // API servers ping is interrupted"), true);
                //                SLogger.sendToALS(ISSUE.JFOREX_9102);
                SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                pingEndTime = System.currentTimeMillis();
                String errorMessage = "";
                if (pingEndTime > 0L && pingStartTime > 0L) {
                    errorMessage = "The ping interrupted, end time="
                                   + DATE_FORMAT.format(pingEndTime)
                                   + ", the ping processing time=["
                                   + (pingEndTime - pingStartTime)
                                   + "]ms";
                } else {
                    errorMessage = "The ping interrupted.";
                }

                apiURL = var23.getMessage();
                if (apiURL != null && !apiURL.isEmpty()) {
                    apiURL = ". " + apiURL;
                } else {
                    apiURL = "";
                }

                LOGGER.error(errorMessage + apiURL, var23);
                throw var23;
            }
        }
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

    private static AuthorizationServerPropertiesResponse requestPlatformProperties(URL url) throws IOException {

        String propertiesAsString = null;
        BufferedReader reader = null;
        int responseCode = 0;
        AuthorizationServerPropertiesResponse authorizationServerPropertiesResponse = null;

        try {
            URLConnection connection = getConnection(url);
            if (connection instanceof HttpURLConnection) {
                responseCode = ((HttpURLConnection) connection).getResponseCode();
            }

            String clientCountry;
            try {
                String clientIpAddress = connection.getHeaderField(IP_HTTP_HEADER);
                if (clientIpAddress == null || clientIpAddress.trim().isEmpty()) {
                    clientIpAddress = "n/a";
                }

                clientCountry = connection.getHeaderField(CLIENT_COUNTRY);
                if (clientCountry == null || clientCountry.trim().isEmpty()) {
                    clientCountry = "n/a";
                }

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
            } catch (Throwable var13) {
                LOGGER.error("Cannot get the Client-IP header field.");
                LOGGER.error(var13.getMessage(), var13);
            }

            try {
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuffer sb = new StringBuffer(8192);

                do {
                    clientCountry = reader.readLine();
                    sb.append(clientCountry);
                } while (clientCountry != null);

                propertiesAsString = sb.toString();
                propertiesAsString = propertiesAsString.trim();
            } catch (Throwable var12) {
                LOGGER.error(var12.getMessage(), var12);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }

        }

        authorizationServerPropertiesResponse = new AuthorizationServerPropertiesResponse(responseCode,
                                                                                          propertiesAsString);
        return authorizationServerPropertiesResponse;
    }

    private static void handleNoAccountSettings(String errorMessage) throws Exception {

        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED.getMessage();
        }

        LOGGER.error(errorMessage);
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

    private static boolean connectWithoutLoginDialog() {

        String username = System.getProperty("jnlp.client.username");
        String password = System.getProperty("jnlp.client.password");
        String ticket = System.getProperty("jnlp.auth.ticket");
        String sessionId = System.getProperty("jnlp.api.sid");
        String authToken = System.getProperty("jnlp.auth.token");
        String apiUrl = System.getProperty("jnlp.api.url");
        //        if (PlatformServiceProvider.getInstance().isSdkMode()) {
        //            return true;
        //        } else {
        //            if (username != null) {
        //                username = username.trim();
        //            }
        //
        //            if (password != null) {
        //                password = password.trim();
        //            }
        //
        //            if (ticket != null) {
        //                ticket = ticket.trim();
        //            }
        //
        //            if (sessionId != null) {
        //                sessionId = sessionId.trim();
        //            }
        //
        //            if (authToken != null) {
        //                authToken = authToken.trim();
        //            }
        //
        //            if (apiUrl != null) {
        //                apiUrl = apiUrl.trim();
        //            }
        //
        //            if (!ObjectUtils.isNullOrEmpty(authToken)) {
        //                return true;
        //            } else if (!ObjectUtils.isNullOrEmpty(password) && !ObjectUtils.isNullOrEmpty(username.isEmpty())) {
        //                return true;
        //            } else {
        //                return !ObjectUtils.isNullOrEmpty(username) && !ObjectUtils.isNullOrEmpty(sessionId) && !ObjectUtils.isNullOrEmpty(apiUrl) && !ObjectUtils.isNullOrEmpty(ticket);
        //            }
        //        }
        //TODO added
        return true;
    }

    private static URLConnection getConnection(URL url) throws IOException {

        URLConnection connection = url.openConnection();
        if (connection instanceof HttpsURLConnection && Boolean.getBoolean(SSL_IGNORE_ERRORS)) {
            LOGGER.debug("Ignoring SSL errors");
            ((HttpsURLConnection) connection).setHostnameVerifier(new AuthorizationClient.DummyHostNameVerifier());
        }

        if (connection instanceof URLConnection) {
            connection.setRequestProperty(USER_AGENT_KEY, USER_AGENT_VALUE);
        }

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        return connection;
    }

    private static String convertToHex(byte[] data) {

        StringBuffer buf = new StringBuffer();
        byte[] var2 = data;
        int var3 = data.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            byte aData = var2[var4];
            int halfbyte = aData >>> 4 & 15;
            int var7 = 0;

            do {
                if (0 <= halfbyte && halfbyte <= 9) {
                    buf.append((char) (48 + halfbyte));
                } else {
                    buf.append((char) (97 + (halfbyte - 10)));
                }

                halfbyte = aData & 15;
            } while (var7++ < 1);
        }

        return buf.toString();
    }

    private static String encodeAll(String password, String capthaId, String login)
        throws NoSuchAlgorithmException, UnsupportedEncodingException {

        String toCode;
        if (capthaId != null) {
            toCode = encodeString(password) + capthaId + login;
        } else {
            toCode = encodeString(password) + login;
        }

        return encodeString(toCode);
    }

    public static String encodeString(String string) throws NoSuchAlgorithmException, UnsupportedEncodingException {

        MessageDigest md = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM);
        md.update(string.getBytes(MESSAGE_DIGEST_ENCODING), 0, string.length());
        byte[] sha1hash = md.digest();
        return convertToHex(sha1hash).toUpperCase();
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
            String info = String.format("%s %s%-10s %s%-65s %s%-65s %s%-5s %s%s %s%-5s %s%-15s %s%-15s %s%-20s %s%-15s %s%-40s %s%s", currentDate, "login=", login, "currentToken=", currentToken, "nextToken=", nextToken, "authResult=", String.valueOf(authResult), "deviceID=", deviceID, "rememberMe=", rememberMe, "platform=", platform, "deviceName=", deviceName, "deviceOS=", deviceOS, "platformVersion=", platformVersion, "sessionId=", sessionId, "authorizationUrl=", authorizationUrl);
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

    public void updateConfigurationPool(Collection<String> authServerUrls) throws MalformedURLException {

        this.configurationPool.clear();
        Iterator var2 = authServerUrls.iterator();

        while (var2.hasNext()) {
            String authServerUrl = (String) var2.next();
            this.configurationPool.add(authServerUrl);
        }

    }


    /**
     * The report URL.
     *
     * @param reportKey the key
     * @param baseUrl the url
     * @param stsToken the requiered token
     * @return the report url as String
     */
    public String getReportURL(String reportKey, String baseUrl, String stsToken) {
        PreCheck.parameterNotNullOrEmpty(reportKey, "reportKey");
        PreCheck.parameterNotNullOrEmpty(baseUrl, "baseUrl");
        PreCheck.parameterNotNullOrEmpty(stsToken, "stsToken");
        reportKey = reportKey.trim();
        String TYPE =            "secure";
        StringBuilder reportUrl =            new StringBuilder(256);
        reportUrl
            .append(baseUrl)
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
     *
     *
     * @param baseUrl
     * @param stsToken
     * @param mode
     * @return
     */
    public String getChatURL(String baseUrl,
                             String stsToken,
                             String mode) {

        PreCheck.parameterNotNullOrEmpty(stsToken, "stsToken");
        PreCheck.parameterNotNullOrEmpty(baseUrl,"baseUrl");
        PreCheck.parameterNotNullOrEmpty(mode,"mode");
        StringBuilder chatUrl = new StringBuilder();
        char separator = baseUrl.contains("#") ?  '&' : '?';
        chatUrl
            .append(baseUrl)
            .append(separator)
            .append("token")
            .append("=")
            .append(mode)
            .append(stsToken);
        return chatUrl.toString();
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
                BufferedReader reader = null;
                int authorizationServerResponseCode = 0;
                URLConnection connection = getConnection(stsTokenURL);
                if (connection instanceof HttpURLConnection) {
                    authorizationServerResponseCode = ((HttpURLConnection) connection).getResponseCode();
                }

                try {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    response = reader.readLine();
                } catch (Throwable var28) {
                    LOGGER.error(var28.getMessage(), var28);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (Exception var27) {
                            LOGGER.error(var27.getMessage(), var27);
                        }
                    }

                }

                authorizationServerStsTokenResponse = new AuthorizationServerStsTokenResponse(response,
                                                                                              authorizationServerResponseCode);
                if (authorizationServerStsTokenResponse.isOK()) {
                    return authorizationServerStsTokenResponse;
                }

                LOGGER.error("Cannot get STS token:" + stsTokenURLBuf.toString());
                LOGGER.error(authorizationServerStsTokenResponse.toString());
            } catch (MalformedURLException var30) {
                LOGGER.error(var30.getMessage(), var30);
                authorizationServerStsTokenResponse
                    = new AuthorizationServerStsTokenResponse(AuthorizationServerResponseCode
                                                                  .BAD_URL);
                return authorizationServerStsTokenResponse;
            } catch (IOException var31) {
                LOGGER.error(var31.getMessage(), var31);
                authorizationServerStsTokenResponse
                    = new AuthorizationServerStsTokenResponse(ERROR_IO);
            } catch (Throwable var32) {
                LOGGER.error(var32.getMessage(), var32);
                authorizationServerStsTokenResponse
                    = new AuthorizationServerStsTokenResponse(AuthorizationServerResponseCode
                    .INTERNAL_ERROR);
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
     * @param authorizationServerResponse
     * @return
     */
    private boolean continueObtainingAPIServers(int retryCount,
                                                AuthorizationServerResponse authorizationServerResponse) {

            return authorizationServerResponse == null || (
                retryCount < this.configurationPool.size() && authorizationServerResponse.isEmptyResponse()
                || retryCount < this.configurationPool.size() && (
                    authorizationServerResponse.getResponseCode() == TOO_MANY_REQUESTS
                    || authorizationServerResponse.getResponseCode() == ERROR_IO
                    || authorizationServerResponse.getResponseCode() == EMPTY_RESPONSE
                    || authorizationServerResponse.getResponseCode() == NOT_FOUND
                    || authorizationServerResponse.getResponseCode() == WRONG_AUTH_RESPONSE
                    || authorizationServerResponse.getResponseCode() == AUTHORIZATION_REQUEST_TIMEOUT
                    || authorizationServerResponse.getResponseCode() == AUTHORIZATION_TIMEOUT
                    || authorizationServerResponse.getResponseCode() == NO_PROPERTIES_RECEIVED
                    || authorizationServerResponse.getResponseCode() == SERVICE_UNAVAILABLE
                    || authorizationServerResponse.getResponseCode() == UNKNOWN_RESPONSE
                    || authorizationServerResponse.isSystemError()));
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
                                                                ==AuthorizationServerResponseCode.SERVICE_UNAVAILABLE
                                                             || authorizationServerResponse.getResponseCode()
                                                                == AuthorizationServerResponseCode.INTERRUPTED);
        }
    }

    /**
     * @param attempts
     */
    private void resetSameAuthUrlAttempts(Map<AuthorizationServerResponseCode,
        SameAuthUrlAttemptsBean> attempts) {


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
    private boolean useTheSameAuthorizationURL(AuthorizationServerResponseCode
    authorizationServerResponseCode,
                                               Map<AuthorizationServerResponseCode,
                                               AuthorizationClient.SameAuthUrlAttemptsBean> attempts) {

        AuthorizationClient.SameAuthUrlAttemptsBean
            authUrlAttemptsBean
            =  attempts.get(authorizationServerResponseCode);
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
                    login)).setPassword(password)).setPlatformVersion(clientVersion)).setAccountType(AccountType
                    .TRADER))
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
        sameAuthUrlAttempts.put(AUTHORIZATION_REQUEST_TIMEOUT,
                                authUrlAttemptsBean);
        authUrlAttemptsBean = new AuthorizationClient.SameAuthUrlAttemptsBean(3);
        sameAuthUrlAttempts.put(AuthorizationServerResponseCode.AUTHORIZATION_TIMEOUT,
                                authUrlAttemptsBean);
        authUrlAttemptsBean = new AuthorizationClient.SameAuthUrlAttemptsBean(2);
        sameAuthUrlAttempts.put(AuthorizationServerResponseCode.SERVICE_UNAVAILABLE,
                                authUrlAttemptsBean);

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
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode
                            .INTERNAL_ERROR);
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
                                                                                      AuthorizationServerResponseCode.SUCCESS_OK,
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

                    authorizationServerResponse
                        = new AuthorizationServerResponse(EMPTY_RESPONSE);
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
                        } catch (Exception var37) {
                            LOGGER.error(var37.getMessage(), var37);
                        }
                    } else {
                        authorizationServerResponse
                            = new AuthorizationServerResponse(AuthorizationServerResponseCode
                            .UNKNOWN_RESPONSE);
                    }
                    break;
                } catch (SRP6Exception var44) {
                    LOGGER.error(var44.getMessage(), var44);
                    authProtocol = var44.getCauseType();
                    switch (authProtocol) {
                        case BAD_CREDENTIALS:
                            authorizationServerResponse
                                = new AuthorizationServerResponse(AuthorizationServerResponseCode
                                .BAD_CREDENTIALS);
                            break label191;
                        case BAD_PUBLIC_VALUE:
                            authorizationServerResponse
                                = new AuthorizationServerResponse(AuthorizationServerResponseCode
                                .BAD_PUBLIC_VALUE);
                            break label191;
                        case TIMEOUT:
                            authorizationServerResponse
                                = new AuthorizationServerResponse(AuthorizationServerResponseCode
                                .AUTHORIZATION_TIMEOUT);
                            break label191;
                        default:
                            authorizationServerResponse
                                = new AuthorizationServerResponse(AuthorizationServerResponseCode
                                .UNKNOWN_RESPONSE);
                            break label191;
                    }
                } catch (IOException var45) {
                    LOGGER.error(var45.getMessage(), var45);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(ERROR_IO);
                    break;
                } catch (InterruptedException var46) {
                    LOGGER.error(var46.getMessage(), var46);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .INTERRUPTED);
                    break;
                } catch (Throwable var47) {
                    LOGGER.error(var47.getMessage(), var47);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .INTERNAL_ERROR);
                    return authorizationServerResponse;
                }
            }

            if (!this.useTheSameAuthorizationURL(authorizationServerResponse.getResponseCode(), sameAuthUrlAttempts)) {
                this.resetSameAuthUrlAttempts(sameAuthUrlAttempts);
                ++retryCount;
                if (this.continueObtainingAPIServers(retryCount,  authorizationServerResponse)) {
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
                } catch (Throwable var12) {
                    LOGGER.error(var12.getMessage(), var12);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .INTERNAL_ERROR);
                    return authorizationServerResponse;
                }
            }

            try {
                String serverRegion = System.getProperty("server.region");
                String formedUrl = this.getBaseUrl()
                                   + AUTH_CONTEXT
                                   + PLATFORM_PARAM
                                   + "="
                                   + platform
                                   + "&"
                                   + LOGIN_PARAM
                                   + "="
                                   + URLEncoder.encode(login, "UTF-8")
                                   + "&"
                                   + PASSWORD_PARAM
                                   + "="
                                   + URLEncoder.encode(encodePassword ? encodeAll(password, "", login) : password,
                                                       "UTF-8")
                                   + "&"
                                   + VERSION_PARAM
                                   + "="
                                   + URLEncoder.encode(this.version, "UTF-8")
                                   + "&"
                                   + SESSION_ID_PARAM
                                   + "="
                                   + sessionId
                                   + "&"
                                   + WILL_PING_PARAM
                                   + "=true"
                                   + (serverRegion == null ? "" : "&region=" + serverRegion);
                URL url = new URL(formedUrl);
                authorizationServerResponse = getAuthorizationServerResponse(url);
                if (authorizationServerResponse.isOK()) {
                    String
                        fastestAPIAndTicket
                        = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                    authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                }
            } catch (MalformedURLException | UnsupportedEncodingException | NoSuchAlgorithmException var13) {
                LOGGER.error(var13.getMessage(), var13);
                if (var13 instanceof MalformedURLException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_URL);
                }

                if (var13 instanceof UnsupportedEncodingException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .ERROR_INIT);
                }

                if (var13 instanceof NoSuchAlgorithmException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .ERROR_INIT);
                }

                return authorizationServerResponse;
            } catch (IOException var14) {
                LOGGER.error(var14.getMessage(), var14);
                authorizationServerResponse
                    = new AuthorizationServerResponse(ERROR_IO);
            } catch (InterruptedException var15) {
                LOGGER.error(var15.getMessage(), var15);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (Throwable var16) {
                LOGGER.error(var16.getMessage(), var16);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode
                    .INTERNAL_ERROR);
                return authorizationServerResponse;
            }

            ++retryCount;
            if (this.continueObtainingAPIServers(retryCount,  authorizationServerResponse)) {
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

        while (this.continueObtainingAPIServers(retryCount,  authorizationServerResponse)) {
            if (this.firstApiRequest) {
                this.firstApiRequest = false;
            } else {
                try {
                    sessionId = this.getNewSessionId();
                } catch (Throwable var13) {
                    LOGGER.error(var13.getMessage(), var13);
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .INTERNAL_ERROR);
                    return authorizationServerResponse;
                }
            }

            try {
                String serverRegion = System.getProperty("server.region");
                String urlString = this.getBaseUrl()
                                   + AUTH_CONTEXT
                                   + PLATFORM_PARAM
                                   + "="
                                   + platform
                                   + "&"
                                   + LOGIN_PARAM
                                   + "="
                                   + URLEncoder.encode(login, "UTF-8")
                                   + "&"
                                   + PASSWORD_PARAM
                                   + "="
                                   + URLEncoder.encode(encodeAll(password, captchaId, login), "UTF-8")
                                   + "&"
                                   + VERSION_PARAM
                                   + "="
                                   + URLEncoder.encode(this.version, "UTF-8")
                                   + "&"
                                   + SESSION_ID_PARAM
                                   + "="
                                   + sessionId
                                   + "&"
                                   + WILL_PING_PARAM
                                   + "=true"
                                   + (serverRegion == null ? "" : "&region=" + serverRegion);
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
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .ERROR_INIT);
                }

                return authorizationServerResponse;
            } catch (IOException var15) {
                LOGGER.error(var15.getMessage(), var15);
                authorizationServerResponse
                    = new AuthorizationServerResponse(ERROR_IO);
            } catch (InterruptedException var16) {
                LOGGER.error(var16.getMessage(), var16);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (Throwable var17) {
                LOGGER.error(var17.getMessage(), var17);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode
                    .INTERNAL_ERROR);
                return authorizationServerResponse;
            }

            ++retryCount;
            if (this.continueObtainingAPIServers(retryCount,  authorizationServerResponse)) {
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

        while (this.continueObtainingAPIServers(retryCount,  authorizationServerResponse)) {
            try {
                String formedUrl = this.getBaseUrl()
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
                                   + URLEncoder.encode(this.version, "UTF-8")
                                   + "&"
                                   + WILL_PING_PARAM
                                   + "=true";
                URL url = new URL(formedUrl);
                authorizationServerResponse = getAuthorizationServerResponse(url);
                if (authorizationServerResponse.isOK()) {
                    String
                        fastestAPIAndTicket
                        = selectFastestAPIServer(authorizationServerResponse.getResponseMessage());
                    authorizationServerResponse.setFastestAPIAndTicket(fastestAPIAndTicket);
                }
            } catch (UnsupportedEncodingException | MalformedURLException var10) {
                LOGGER.error(var10.getMessage(), var10);
                if (var10 instanceof MalformedURLException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode.BAD_URL);
                }

                if (var10 instanceof UnsupportedEncodingException) {
                    authorizationServerResponse
                        = new AuthorizationServerResponse(AuthorizationServerResponseCode
                        .ERROR_INIT);
                }

                return authorizationServerResponse;
            } catch (IOException var11) {
                LOGGER.error(var11.getMessage(), var11);
                authorizationServerResponse
                    = new AuthorizationServerResponse(ERROR_IO);
            } catch (InterruptedException var12) {
                LOGGER.error(var12.getMessage(), var12);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (Throwable var13) {
                LOGGER.error(var13.getMessage(), var13);
                authorizationServerResponse
                    = new AuthorizationServerResponse(AuthorizationServerResponseCode
                    .INTERNAL_ERROR);
                return authorizationServerResponse;
            }

            ++retryCount;
            if (this.continueObtainingAPIServers(retryCount,  authorizationServerResponse)) {
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
                org.json.JSONObject responseAsJson = singleServerResponse.getResponse();
                authorizationServerResponse = new AuthorizationServerPinRequiredResponse(responseAsJson);
            } catch (MalformedURLException var9) {
                LOGGER.error(var9.getMessage(), var9);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode.BAD_URL);
            } catch (IOException var10) {
                LOGGER.error(var10.getMessage(), var10);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(ERROR_IO);
            } catch (InterruptedException var11) {
                LOGGER.error(var11.getMessage(), var11);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode.INTERRUPTED);
            } catch (AuthServerException var12) {
                LOGGER.error(var12.getMessage(), var12);
                AuthServerResponse authServerResponse = var12.getAuthServerResponse();
                if (authServerResponse != null) {
                    int responseCode = authServerResponse.getResponseCode();
                    String responseMessage = authServerResponse.getResponse();
                    authorizationServerResponse = new AuthorizationServerPinRequiredResponse(responseCode,
                                                                                             responseMessage);
                } else {
                    authorizationServerResponse
                        = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                }
            } catch (Throwable var13) {
                LOGGER.error(var13.getMessage(), var13);
                authorizationServerResponse
                    = new AuthorizationServerPinRequiredResponse(AuthorizationServerResponseCode
                    .INTERNAL_ERROR);
            }

            ++retryCount;
            if (this.continueCheckIfPinRequired(retryCount,  authorizationServerResponse)) {
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
                = new AuthorizationServerWLBOResponse(AuthorizationServerResponseCode
                .INTERNAL_ERROR);
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
                    LOGGER.debug("Authorization url=" + authURLBuf.toString());
                    URL stsTokenURL = new URL(authURLBuf.toString());
                    String response = null;
                    BufferedReader reader = null;
                    int authorizationServerResponseCode = 0;
                    URLConnection connection = getConnection(stsTokenURL);
                    if (connection instanceof HttpURLConnection) {
                        authorizationServerResponseCode = ((HttpURLConnection) connection).getResponseCode();
                    }

                    try {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        response = reader.readLine();
                    } catch (Throwable var24) {
                        ;
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (Throwable var23) {
                                LOGGER.error(var23.getMessage(), var23);
                            }
                        }

                    }

                    authorizationServerWLBOResponse = new AuthorizationServerWLBOResponse(response,
                                                                                          authorizationServerResponseCode);
                    if (authorizationServerWLBOResponse.isOK()) {
                        return authorizationServerWLBOResponse;
                    }

                    LOGGER.error("Wrong response, URL="
                                 + authURLBuf.toString()
                                 + ", response="
                                 + authorizationServerWLBOResponse.toString());
                } catch (MalformedURLException var26) {
                    LOGGER.error(var26.getMessage(), var26);
                    authorizationServerWLBOResponse
                        = new AuthorizationServerWLBOResponse(AuthorizationServerResponseCode
                        .BAD_URL);
                    return authorizationServerWLBOResponse;
                } catch (IOException var27) {
                    LOGGER.error(var27.getMessage(), var27);
                    authorizationServerWLBOResponse
                        = new AuthorizationServerWLBOResponse(ERROR_IO);
                } catch (Throwable var28) {
                    LOGGER.error(var28.getMessage(), var28);
                    authorizationServerWLBOResponse
                        = new AuthorizationServerWLBOResponse(AuthorizationServerResponseCode
                        .INTERNAL_ERROR);
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
                formedUrl = this.getBaseUrl()
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
                URL url = new URL(formedUrl);
                LOGGER.info("Account settings request: retry count = " + retryCount + ", time = " + DATE_FORMAT.format(
                    System.currentTimeMillis()));
                LOGGER.debug(">> [{}]", url);
                propertiesResponse = requestPlatformProperties(url);
                if (propertiesResponse.isOK()) {
                    LOGGER.info("Account settings received: retry count = "
                                + retryCount
                                + ", time = "
                                + DATE_FORMAT.format(System.currentTimeMillis()));
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
                    } catch (Throwable var12) {
                        LOGGER.error(var12.getMessage(), var12);
                    }
                    break;
                }

                this.configurationPool.markLastUsedAsBad();
            } catch (IOException var13) {
                LOGGER.info("Cannot get the platform properties, url=" + formedUrl);
                propertiesResponse
                    = new AuthorizationServerPropertiesResponse(ERROR_IO);
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error(var13.getMessage(), var13);
            } catch (Throwable var14) {
                LOGGER.info("Cannot get the platform properties, url=" + formedUrl);
                propertiesResponse
                    = new AuthorizationServerPropertiesResponse(AuthorizationServerResponseCode.INTERNAL_ERROR);
                this.configurationPool.markLastUsedAsBad();
                LOGGER.error(var14.getMessage(), var14);
            }

            if (propertiesResponse.getResponseCode()
                == AuthorizationServerResponseCode.TICKET_EXPIRED) {
                handleNoAccountSettings(propertiesResponse.getResponseCode().getError());
            }

            if (retryCount >= this.configurationPool.size()) {
                AuthorizationServerResponseCode responseCode = propertiesResponse.getResponseCode();
                if (responseCode != null) {
                    String errorMessage = responseCode.getError();
                    if (responseCode != AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED) {
                        errorMessage
                            = AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED.getMessage()
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
            } catch (Throwable var11) {
                ;
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
        } catch (Throwable var3) {
            LOGGER.error(var3.getMessage(), var3);
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
            } catch (Exception var9) {
                LOGGER.error(var9.getMessage(), var9);
            }

            //authClient.ipInfoAlsMessageBean = AlsMessageFactory.createIPInfoAlsMessageBean(clientCountry, clientIpAddress);
        } catch (Exception var10) {
            LOGGER.error("Cannot get the Client-IP and Client-country.");
            LOGGER.error(var10.getMessage(), var10);
        }

    }

    private byte[] unzip(byte[] compressed) throws IOException {

        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        GZIPInputStream gzipIn = new GZIPInputStream(bais);
        Throwable var4 = null;

        try {
            ByteArrayOutputStream bytesCollector = new ByteArrayOutputStream();
            byte[] buff = new byte[4096];

            int i;
            while ((i = gzipIn.read(buff)) != -1) {
                bytesCollector.write(buff, 0, i);
            }

            byte[] var8 = bytesCollector.toByteArray();
            return var8;
        } catch (Throwable var17) {
            var4 = var17;
            throw var17;
        } finally {
            if (gzipIn != null) {
                if (var4 != null) {
                    try {
                        gzipIn.close();
                    } catch (Throwable var16) {
                        var4.addSuppressed(var16);
                    }
                } else {
                    gzipIn.close();
                }
            }

        }
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
