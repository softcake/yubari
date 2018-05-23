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


import org.softcake.yubari.netty.client.TransportClient;

import com.dukascopy.dds3.transport.msg.acc.AccountInfoMessage;
import com.dukascopy.dds3.transport.msg.acc.AccountInfoMessageInit;
import com.dukascopy.dds3.transport.msg.acc.AccountInfoMessagePack;
import com.dukascopy.dds3.transport.msg.acc.PackedAccountInfoMessage;
import com.dukascopy.dds3.transport.msg.api.ApiSplitGroupMessage;
import com.dukascopy.dds3.transport.msg.api.ApiSplitMessage;
import com.dukascopy.dds3.transport.msg.ddsApi.InitRequestMessage;
import com.dukascopy.dds3.transport.msg.ddsApi.QuitRequestMessage;
import com.dukascopy.dds3.transport.msg.dfs.AbstractDFSMessage;
import com.dukascopy.dds3.transport.msg.feeder.QuoteSubscriptionResponseMessage;
import com.dukascopy.dds3.transport.msg.ord.MergePositionsMessage;
import com.dukascopy.dds3.transport.msg.ord.OrderGroupMessage;
import com.dukascopy.dds3.transport.msg.ord.OrderMessage;
import com.dukascopy.dds3.transport.msg.ord.OrderMessageExt;
import com.dukascopy.dds4.transport.common.mina.ClientListener;
import com.dukascopy.dds4.transport.common.mina.DisconnectedEvent;
import com.dukascopy.dds4.transport.common.mina.ITransportClient;
import com.dukascopy.dds4.transport.msg.system.CurrencyMarket;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class DCClientImpl implements ClientListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DCClientImpl.class);
    private static final long TRANSPORT_PING_TIMEOUT;
    private static final String SNAPSHOT = "SNAPSHOT";
    public static final String DEFAULT_VERSION = "99.99.99";
    private static final String FEED_COMMISSION_HISTORY = "feed.commission.history";
    private static final String SUPPORTED_INSTRUMENTS = "instruments";
    private static final String HISTORY_SERVER_URL = "history.server.url";
    private static final String ENCRYPTION_KEY = "encryptionKey";
    private static final String SERVICES1_URL = "services1.url";
    private static final String TRADELOG_SFX = "tradelog_sfx.url";
    private static final String EXTERNAL_IP_ADDRESS = "external_ip";
    private static final String STORAGE_URL_PARAM_NAME = "sss.server.url";
    private static final String USER_TYPES = "userTypes";
    private static final String ANALITIC_CONTEST = "ANALITIC_CONTEST";
    private static final String LAYOUT_TYPES = "additionalUserTypes";
    private static final String WL_ENVIRONMENT_KEY = "wlabel.uniEnvironmentKey";
    private static final String JSTORE_ENABLED = "wlabel.jsEnable";
    private static final String DOWNLOAD_DATA_IN_BACKGROUND = "DataDownloadInBackground";
    private static final String INFO = "INFO";
    private static final String WARNING = "WARNING";
    private static final String ERROR = "ERROR";

    private TransportClient transportClient;

    private boolean initialized = false;
    private boolean live;
    private String accountName;
    private String version;

    private PrintStream out;
    private PrintStream err;
    private Properties serverProperties;
    private String internalIP;
    private volatile String sessionID;
    private String captchaId;
    private long temporaryKeys = -1L;

    private final Map<UUID, Long> pluginIdMap = new HashMap();
    private AccountInfoMessage lastAccountInfoMessage;
    private AccountInfoMessageInit lastAccountInfoMessageInit;

//
//    private ISession session = new ISession() {
//        public void setSessionId(String sessionId) {
//            DCClientImpl.this.sessionID = sessionId;
//        }
//
//        public void recreateSessionId() {
//            DCClientImpl.this.sessionID = UUID.randomUUID().toString();
//        }
//
//        public String getSessionId() {
//            return DCClientImpl.this.sessionID;
//        }
//    };


    public DCClientImpl() {

        ThreadFactory var10000 = new ThreadFactory() {
            final AtomicInteger threadNumber = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "Mina_Thread_" + this.threadNumber.getAndIncrement());
                if (!thread.isDaemon()) {
                    thread.setDaemon(true);
                }

                return thread;
            }
        };
        this.out = System.out;
        this.err = System.err;

        this.version = this.getClass().getPackage().getImplementationVersion();
        if (this.version == null || this.version.endsWith("SNAPSHOT")) {
            this.version = "99.99.99";
        }

        this.serverProperties = new Properties();

    }

    public synchronized void connect(String jnlpUrl, String username, String password) throws Exception {
        this.connect(jnlpUrl, username, password, (String)null);
    }

    public synchronized void connect(String jnlpUrl, String username, String password, String pin) throws Exception {
        String authServersCsv;
        String[] urlList;
        List authServerUrls;
        String ticket;
        if (this.transportClient == null) {
            authServersCsv = this.getAuthServers(jnlpUrl);

            urlList = authServersCsv.split(",");
            authServerUrls = Arrays.asList(urlList);
            ticket = this.authenticate(authServerUrls, username, password, true, pin);
            this.accountName = username;


            String servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");

            List<String[]> feedCommissions = (List)this.serverProperties.get("feed.commission.history");
            Set<String> supportedInstruments = (Set)this.serverProperties.get("instruments");
            String downloadDataInBackgroundString = this.serverProperties.getProperty("DataDownloadInBackground");


            this.transportClient.connect();
            this.connectToHistoryServer(username, true);

        } else if (!this.transportClient.isOnline()) {
            this.lastAccountInfoMessage = null;
            this.initialized = false;
            authServersCsv = this.getAuthServers(jnlpUrl);

            urlList = authServersCsv.split(",");
            authServerUrls = Arrays.asList(urlList);
            ticket = this.authenticate(authServerUrls, username, password, true, pin);
            String servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");

        }

    }

    public synchronized void connect(Collection<String> authServerUrls, boolean live, String username, String password, boolean encodePassword) throws Exception {
        this.connect(authServerUrls, live, username, password, encodePassword, (String)null);
    }

    public synchronized void connect(Collection<String> authServerUrls, boolean live, String username, String password, boolean encodePassword, String pin) throws Exception {
        this.live = live;
        String ticket;
        String servicesUrl;
        if (this.transportClient == null) {

            ticket = this.authenticate(authServerUrls, username, password, encodePassword, pin);
            this.accountName = username;

            servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");

            List<String[]> feedCommissions = (List)this.serverProperties.get("feed.commission.history");
            Set<String> supportedInstruments = (Set)this.serverProperties.get("instruments");

            this.transportClient.connect();
            this.connectToHistoryServer(username, Boolean.parseBoolean(this.serverProperties.getProperty("DataDownloadInBackground")));

        } else if (!this.transportClient.isOnline()) {
            this.lastAccountInfoMessage = null;
            this.initialized = false;

            ticket = this.authenticate(authServerUrls, username, password, encodePassword, pin);
            servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");

            this.transportClient.connect();
            this.connectToHistoryServer(username, true);

        }

    }

    private String getAuthServers(String jnlp) throws Exception {
       return "";
    }

    public BufferedImage getCaptchaImage(String jnlp) throws Exception {
//        String authServersCsv = this.getAuthServers(jnlp);
//        String[] urlList = authServersCsv.split(",");
//        List<String> authServers = Arrays.asList(urlList);
//        AuthorizationClient
//            authorizationClient = AuthorizationClient.getInstance(authServers, this.version);
//        Map<String, BufferedImage> imageCaptchaMap = authorizationClient.getImageCaptcha();
//        if (!imageCaptchaMap.isEmpty()) {
//            Entry<String, BufferedImage> imageCaptchaEntry = (Entry)imageCaptchaMap.entrySet().iterator().next();
//            this.captchaId = (String)imageCaptchaEntry.getKey();
//            return (BufferedImage)imageCaptchaEntry.getValue();
//        } else {
//            return null;
//        }
        return null;
    }

    private String authenticate(Collection<String> authServerUrls, String username, String password, boolean encodePassword, String pin) throws Exception {
//        AuthorizationClient
//            authorizationClient =AuthorizationClient.getInstance(authServerUrls, this.version);
//        if (!encodePassword && pin != null) {
//            throw new Exception("(EncodePassword == false && pin != null) == NOT SUPPORTED");
//        } else {
//            AuthorizationServerResponse authorizationServerResponse = null;
//
//                authorizationServerResponse = authorizationClient.getAPIsAndTicketUsingLogin_SRP6(username, password, this.captchaId, pin, this.sessionID, "DDS3_JFOREXSDK");
//
//
//            if (authorizationServerResponse != null) {
//                String authResponse = authorizationServerResponse.getFastestAPIAndTicket();
//                LOGGER.debug(authResponse);
//                String host;
//                if (!authorizationServerResponse.isOK()) {
//                    AuthorizationServerResponseCode authorizationServerResponseCode = authorizationServerResponse.getResponseCode();
//                    if (authorizationServerResponse.isEmptyResponse()) {
//                        throw new IOException("Authentication failed");
//                    } else if (AuthorizationServerResponseCode.MINUS_ONE_OLD_ERROR != authorizationServerResponseCode && AuthorizationServerResponseCode.AUTHENTICATION_AUTHORIZATION_ERROR != authorizationServerResponseCode) {
//                        if (authorizationServerResponse.isWrongVersion()) {
//                           // throw new JFVersionException("Incorrect version");
//                        } else if (authorizationServerResponse.isNoAPIServers()) {
//                          //  throw new JFVersionException("System offline");
//                        } else if (authorizationServerResponse.isSystemError()) {
//                           // throw new JFVersionException("System error");
//                        } else if (authorizationServerResponse.isSrp6requestWithProperties() && authorizationServerResponseCode == AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED) {
//                          //  throw new JFException(Error.NO_ACCOUNT_SETTINGS_RECEIVED, authorizationServerResponseCode.getError());
//                        } else {
//                            host = authorizationServerResponseCode.getError();
//                            // throw new JFException(host);
//                        }
//                    } else {
//                      //  throw new JFAuthenticationException("Incorrect username or password");
//                    }
//                } else {
//                    Matcher matcher = AuthorizationClient.RESULT_PATTERN.matcher(authResponse);
//                    if (!matcher.matches()) {
//                        throw new IOException("Authentication procedure returned unexpected result [" + authResponse + "]");
//                    } else {
//                        String url = matcher.group(1);
//                        int semicolonIndex = url.indexOf(58);
//                        int port;
//                        if (semicolonIndex != -1) {
//                            host = url.substring(0, semicolonIndex);
//                            if (semicolonIndex + 1 >= url.length()) {
//                                LOGGER.warn("port not set, using default 443");
//                                port = 443;
//                            } else {
//                                port = Integer.parseInt(url.substring(semicolonIndex + 1));
//                            }
//                        } else {
//                            host = url;
//                            port = 443;
//                        }
//
//                        String ticket = matcher.group(7);
//                        Properties properties;
//
//                            properties = authorizationServerResponse.getPlatformProperties();
//
//
//                        this.initServerProperties(properties);
//
//
//                        try {
//                            InetAddress localhost = InetAddress.getLocalHost();
//                            this.internalIP = localhost != null ? localhost.getHostAddress() : null;
//                        } catch (UnknownHostException var18) {
//                            LOGGER.error("Can't detect local IP : " + var18.getMessage());
//                            this.internalIP = "";
//                        }
//
//
//                        LOGGER.debug("UserAgent: ");
//
//
//
//                        if (this.transportClient == null) {
//                            TransportClientBuilder builder = TransportClient.builder();
//                            builder.withStaticSessionDictionary(new StaticSessionDictionary()).withTransportName("DDS2 Standalone Transport Client").withAddress(new ServerAddress(host, port)).withAuthorizationProvider(
//
//                                new ClientAuthorizationProvider() {
//                                    @Override
//                                    public void setUserAgent(final String s) {
//
//                                    }
//
//                                    @Override
//                                    public void setChildConnectionDisabled(final boolean b) {
//
//                                    }
//
//                                    @Override
//                                    public void setDroppableMessageServerTTL(final long l) {
//
//                                    }
//
//                                    @Override
//                                    public void setSessionName(final String s) {
//
//                                    }
//
//                                    @Override
//                                    public void setListener(final AuthorizationProviderListener authorizationProviderListener) {
//
//                                    }
//
//                                    @Override
//                                    public InetSocketAddress getAddress() {
//
//                                        return null;
//                                    }
//
//                                    @Override
//                                    public void authorize(final IoSessionWrapper ioSessionWrapper) {
//
//                                    }
//
//                                    @Override
//                                    public void onMessageReceived(final IoSessionWrapper ioSessionWrapper,
//                                                                final ProtocolMessage protocolMessage) {
//
//                                    }
//
//                                    @Override
//                                    public void cleanUp() {
//
//                                    }
//                                }).withUserAgent("").withClientListener(this).withEventPoolSize(2).withUseSSL(true).withSecurityExceptionHandler(new SecurityExceptionHandler() {
//                                public boolean isIgnoreSecurityException(X509Certificate[] chain, String authType, CertificateException ex) {
//                                    DCClientImpl.LOGGER.warn("Security exception : " + ex);
//                                    return true;
//                                }
//                            });
////                            if (!OperatingSystemType.LINUX && !OperatingSystemType.MACOSX) {
////                                builder.withSecondaryConnectionPingTimeout(TRANSPORT_PING_TIMEOUT);
////                            } else {
////                                builder.withSecondaryConnectionPingInterval(5000L).withPrimaryConnectionPingTimeout(2000L).withSecondaryConnectionPingTimeout(2000L);
////                            }
//
//                            this.transportClient = builder.build();
//                        } else {
//                            this.transportClient.setAddress(new ServerAddress(host, port));
//                        }
//
//                        return ticket;
//                    }
//                }
//            } else {
//                throw new IOException("Authentication failed, no server response.");
//            }
//        }
        return "";
    }

    public synchronized void reconnect() {
        if (this.transportClient != null && !this.transportClient.isOnline()) {
            this.transportClient.connect();
        }

    }

    public synchronized void disconnect() {


//        LocalCacheManager localCacheManager = null;
//        if (FeedDataProvider.getDefaultInstance() != null) {
//            localCacheManager = FeedDataProvider.getDefaultInstance().getLocalCacheManager();
//            FeedDataProvider.getDefaultInstance().close();
//        }

        try {
            TimeUnit.SECONDS.sleep(1L);
        } catch (InterruptedException var4) {
            ;
        }

        if (this.transportClient != null) {
            if (this.transportClient.isOnline()) {
                this.sendSynchronizedQuitMessage();
            }

            this.terminateTransportClient();
        }

       // this.nullifySession(localCacheManager);
    }

    private void sendSynchronizedQuitMessage() {
        try {
            Completable listenableFuture = this.transportClient.sendMessageAsync(new QuitRequestMessage());
            listenableFuture.subscribe();
        } catch (Throwable var2) {
            LOGGER.error(var2.getMessage(), var2);
        }

    }

    private void terminateTransportClient() {
        if (this.transportClient != null) {
            this.transportClient.disconnect();
            this.transportClient.terminate();
            this.transportClient = null;
          //  this.authProvider = null;
        }

    }



    public boolean isConnected() {
        return this.transportClient != null && this.transportClient.isOnline() && this.initialized;
    }

    private void connectedInit() {
        InitRequestMessage initRequestMessage = new InitRequestMessage();
        initRequestMessage.setSendGroups(true);
        initRequestMessage.setSendPacked(true);
        this.transportClient.sendMessageNaive(initRequestMessage);


    }




    public void feedbackMessageReceived(ITransportClient client, ProtocolMessage message) {
        TransportClient providedClient = (TransportClient)TransportClient.class.cast(client);
        if (providedClient == null || providedClient.isOnline() && !providedClient.isTerminated() &&  providedClient.getTransportSessionId().equals(this.transportClient.getTransportSessionId())) {
            if (message instanceof AccountInfoMessage) {
                this.onAccountInfoMessage((AccountInfoMessage)message);
            } else if (message instanceof PackedAccountInfoMessage) {
                this.onPackedAccountInfoMessage((PackedAccountInfoMessage)message);
            } else if (message instanceof OrderGroupMessage) {
                this.onOrderGroupMessage(message);
            } else if (message instanceof OrderMessage) {
                this.onOrderMessage(message);
            } else if (message instanceof MergePositionsMessage) {
               // OrdersProvider.getInstance().groupsMerged((MergePositionsMessage)message);
            } else if (message instanceof CurrencyMarket) {
                if (this.lastAccountInfoMessage != null) {
                    CurrencyMarket currencyMarket = (CurrencyMarket)message;

                }
            } else if (!(message instanceof ErrorResponseMessage) && !(message instanceof AbstractDFSMessage)) {
                if (message instanceof AccountInfoMessagePack) {
                    this.onAccountInfoMessagePack((AccountInfoMessagePack)AccountInfoMessagePack.class.cast(message));
                } else if (message instanceof QuoteSubscriptionResponseMessage) {

                } else if (message instanceof ApiSplitGroupMessage) {

                } else if (message instanceof ApiSplitMessage) {

                }
            } else {





                return;
            }
        }
    }

    private void processPackedAccountInfoMessage(PackedAccountInfoMessage packedAccountInfoMessage) {
        AccountInfoMessageInit accountInfoMessageInit = packedAccountInfoMessage.getAccount();


    }

    private void processOrderMessage(OrderMessage message) {
        OrderMessageExt orderMessage = null;
        if (message instanceof OrderMessageExt) {
            orderMessage = (OrderMessageExt)message;
        } else {

        }


    }

    private void onAccountInfoMessage(AccountInfoMessage message) {

    }

    private void onPackedAccountInfoMessage(PackedAccountInfoMessage packedAccountInfoMessage) {


    }

    private void onOrderMessage(ProtocolMessage protocolMessage) {


    }

    private void onOrderGroupMessage(ProtocolMessage protocolMessage) {

    }

    private void onAccountInfoMessagePack(AccountInfoMessagePack pack) {
        ProtocolMessage accountInfo = pack.getAccountInfo();
        if (accountInfo != null) {
            if (accountInfo instanceof PackedAccountInfoMessage) {
                this.onPackedAccountInfoMessage((PackedAccountInfoMessage)PackedAccountInfoMessage.class.cast(accountInfo));
            } else if (accountInfo instanceof AccountInfoMessage) {
                this.onAccountInfoMessage((AccountInfoMessage)AccountInfoMessage.class.cast(accountInfo));
            }
        }

    }

    public synchronized void authorized(ITransportClient client) {
        this.connectedInit();
        if (this.initialized) {
            this.fireConnected();
        }


    }

    private void fireConnected() {
//        ISystemListener systemListener = this.getSystemListener();
//        systemListener.onConnect();
//        FeedDataProvider.getDefaultInstance().connected();
//        this.remoteManager = RemoteStrategyManager.getResetInstance();
//        this.ddsChartsController = DDSChartsControllerFactory.instance().getDefaultInstance();
//        this.clientChartsController = new ClientChartsController(this.ddsChartsController, this.getClass().getName());
//        this.ddsChartsController.changeChartTrading(false);
//        FeedDataProvider.getDefaultInstance().getJssManager().addJssListener(this.jssBroadcastListener);
    }

    public synchronized void disconnected(ITransportClient client, DisconnectedEvent event) {
//        if (FeedDataProvider.getDefaultInstance() != null) {
//            FeedDataProvider.getDefaultInstance().onDisconnected();
//            FeedDataProvider.getDefaultInstance().getJssManager().removeJssListener(this.jssBroadcastListener);
//        }
//
//        ISystemListener systemListener = this.getSystemListener();
//        systemListener.onDisconnect();
//        Iterator var4 = this.taskManagers.values().iterator();
//
//        while(var4.hasNext()) {
//            JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)var4.next();
//            taskManager.onConnect(false);
//        }
//
//        if (this.ddsChartsController != null) {
//            this.ddsChartsController.dispose();
//        }
//
//        this.currencyMarketManager.stop();
    }




    public synchronized void setSubscribedInstruments(String instruments) {

    }

    public synchronized void unsubscribeInstruments(String instruments) {

    }


    public synchronized void setOut(PrintStream out) {
        this.out = out;

    }

    public synchronized void setErr(PrintStream err) {
        this.err = err;
      //  NotificationUtilsProvider.setNotificationUtils(new PrintStreamNotificationUtils(this.out, err));
    }

    public void setCacheDirectory(File cacheDirectory) {
        if (!cacheDirectory.exists()) {
            LOGGER.warn("Cache directory [" + cacheDirectory + "] doesn't exist, trying to create");
            if (!cacheDirectory.mkdirs()) {
                LOGGER.error("Cannot create cache directory [" + cacheDirectory + "], default cache directory will be used");
                return;
            }

//            try {
//                CacheManager.createVersionFile(cacheDirectory);
//            } catch (DataCacheException var3) {
//                LOGGER.error("Cannot create cache directory [" + cacheDirectory + "], default cache directory will be used. Reason [" + var3.getLocalizedMessage() + "]", var3);
//                return;
//            }
        }

       // FilePathManager.getInstance().setCacheFolderPath(cacheDirectory.getAbsolutePath());
    }


    public String getVersion() {
        return this.version;
    }

    private void initServerProperties(Properties properties) {
        if (properties != null) {
            this.serverProperties.putAll(properties);
            //PlatformSessionClientBean.getInstance().setStorageServerUrl(properties.getProperty("sss.server.url"));
            String typesList = this.serverProperties.getProperty("userTypes");
            if (typesList == null) {
                LOGGER.warn("Types list was not found");
                return;
            }

            List<String> accountTypeList = splitUserTypes(typesList);
            boolean isContest = accountTypeList.contains("ANALITIC_CONTEST");
            this.serverProperties.put("ANALITIC_CONTEST", isContest);
            Set<Integer> typesSet = (Set)this.serverProperties.get("additionalUserTypes");
            if (typesSet == null) {
                LOGGER.warn("Layout Types list was not found");
                return;
            }

            boolean isPlaceBidOfferHidden = typesSet.contains(DCClientImpl.LayoutType
                                                                  .PLACE_BID_OFFER_HIDDEN.getId());
            this.serverProperties.put(DCClientImpl.LayoutType.PLACE_BID_OFFER_HIDDEN.toString(), isPlaceBidOfferHidden);
            boolean isDelivirableXau = typesSet.contains(DCClientImpl.LayoutType.DELIVERABLE_XAU.getId());
            this.serverProperties.put(DCClientImpl.LayoutType.DELIVERABLE_XAU.toString(), isDelivirableXau);
        }

    }

    private static List<String> splitUserTypes(String input) {
        Pattern p = Pattern.compile("[,\\s]+");
        String[] result = p.split(input);
        List<String> accountTypes = new ArrayList();
        Collections.addAll(accountTypes, result);
        return accountTypes;
    }

    private boolean getBooleanProperty(String property) {
        boolean value = false;
        if (this.serverProperties.get(property) != null) {
            value = (Boolean)this.serverProperties.get(property);
        }

        return value;
    }

    private void connectToHistoryServer(String username, boolean downloadInBackground) {
//        FeedDataProvider.getDefaultInstance().connectToHistoryServer(this.transportClient, username, this.serverProperties.getProperty("history.server.url", (String)null), this.serverProperties.getProperty("encryptionKey", (String)null), downloadInBackground, true);
//        IInstrumentManager instrumentManager = FeedDataProvider.getDefaultInstance().getInstrumentManager();
//        if (!instrumentManager.getInstrumentListeners().contains(this)) {
//            instrumentManager.addInstrumentListener(this);
//        }

    }

    static {
        TRANSPORT_PING_TIMEOUT = TimeUnit.SECONDS.toMillis(10L);
    }

    private class DefaultSystemListener  {
        private DefaultSystemListener() {
        }

        public void onStart(long processId) {
            DCClientImpl.LOGGER.info("Strategy started: " + processId);
        }

        public void onStop(long processId) {
            DCClientImpl.LOGGER.info("Strategy stopped: " + processId);
        }

        public void onConnect() {
            DCClientImpl.LOGGER.info("Connected");
        }

        public void onDisconnect() {
            DCClientImpl.LOGGER.warn("Disconnected");
        }
    }

    private class LotAmountProvider  {
        private final BigDecimal MIN_CONTEST_TRADABLE_AMOUNT = BigDecimal.valueOf(0.1D);
        private final BigDecimal MAX_CONTEST_TRADABLE_AMOUNT = BigDecimal.valueOf(5L);
        private boolean contest = false;

        public LotAmountProvider() {
            this.contest = DCClientImpl.this.getBooleanProperty("ANALITIC_CONTEST");
        }

        public BigDecimal getMinTradableAmount(String instrument) {


            return null;
        }

        public BigDecimal getMaxTradableAmount(String instrument) {

            return null;
        }
    }


    private static enum LayoutType {
        GLOBAL_EXTENDED(13),
        PLACE_BID_OFFER_HIDDEN(16),
        DELIVERABLE_XAU(22);

        private int layoutId;

        private LayoutType(int layoutId) {
            this.layoutId = layoutId;
        }

        public int getId() {
            return this.layoutId;
        }
    }
}
