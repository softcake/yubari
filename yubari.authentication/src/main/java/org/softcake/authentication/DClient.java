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


//import static com.dukascopy.api.impl.connect.ActivityLogger.getInstance;

import org.softcake.yubari.netty.TransportClient;

import com.dukascopy.api.ConsoleAdapter;
import com.dukascopy.api.IChart;
import com.dukascopy.api.IClientGUI;
import com.dukascopy.api.IClientGUIListener;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IJFRunnable;
import com.dukascopy.api.INewsFilter;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.IFeedInfo;
import com.dukascopy.api.instrument.IFinancialInstrument;
import com.dukascopy.api.strategy.remote.IRemoteStrategyManager;
import com.dukascopy.api.system.IPreferences;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.dds3.transport.msg.acc.AccountInfoMessage;
import com.dukascopy.dds3.transport.msg.acc.AccountInfoMessageInit;
import com.dukascopy.dds3.transport.msg.acc.AccountInfoMessagePack;
import com.dukascopy.dds3.transport.msg.acc.PackedAccountInfoMessage;
import com.dukascopy.dds3.transport.msg.ddsApi.QuitRequestMessage;
import com.dukascopy.dds4.transport.common.mina.ClientListener;
import com.dukascopy.dds4.transport.common.mina.DisconnectedEvent;
import com.dukascopy.dds4.transport.common.mina.ITransportClient;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

//import com.dukascopy.api.impl.StrategyEventsCallback;
//import com.dukascopy.api.impl.connect.ActivityLogger;
//import com.dukascopy.api.impl.connect.ClientChartsController;
//import com.dukascopy.api.impl.connect.DDSChartsControllerFactory;
//import com.dukascopy.api.impl.connect.DefaultLotAmountProvider;
//import com.dukascopy.api.impl.connect.ILotAmountProvider;
//import com.dukascopy.api.impl.connect.ISystemListenerExtended;
//import com.dukascopy.api.impl.connect.IndicatorsSettingsStorage;
//import com.dukascopy.api.impl.connect.JForexTaskManager;
//import com.dukascopy.api.impl.connect.PlatformAccountImpl;
//import com.dukascopy.api.impl.connect.PlatformSessionClientBean;
//import com.dukascopy.api.impl.connect.PlatformType;
//import com.dukascopy.api.impl.connect.PrintStreamNotificationUtils;
//import com.dukascopy.api.impl.connect.SRP6AuthorizationMigrationHelper;
//import com.dukascopy.api.impl.connect.StrategyBroadcastMessageImpl;
//import com.dukascopy.api.impl.connect.StrategyTaskManager;
//import com.dukascopy.api.impl.connect.plugin.PluginTaskManager;
//import com.dukascopy.api.impl.connect.strategy.remote.RemoteStrategyManager;
//import com.dukascopy.charts.data.datacache.CacheManager;
//import com.dukascopy.charts.data.datacache.DataCacheException;
//import com.dukascopy.charts.data.datacache.FeedDataProvider;
//import com.dukascopy.charts.data.datacache.LocalCacheManager;
//import com.dukascopy.charts.data.datacache.TickListener;
//import com.dukascopy.charts.data.datacache.change.CacheChangeTracker;
//import com.dukascopy.charts.data.datacache.change.ICacheChangeTracker;
//import com.dukascopy.charts.data.datacache.splits.SplitsMessageException;
//import com.dukascopy.charts.data.orders.OrdersProvider;
//import com.dukascopy.charts.main.interfaces.DDSChartsController;
//import com.dukascopy.charts.math.indicators.IndicatorsProvider;
//import com.dukascopy.dds2.greed.CommonContext;
//import com.dukascopy.dds2.greed.agent.compiler.JFXCompiler;
//import com.dukascopy.dds2.greed.agent.compiler.JFXPack;
//import com.dukascopy.dds2.greed.agent.indicator.AccountProvider;
//import com.dukascopy.dds2.greed.agent.statistics.OrderExecutionStatisticsRegistry;
//import com.dukascopy.dds2.greed.agent.strategy.bean.IJFRunnablesListener;
//import com.dukascopy.dds2.greed.agent.strategy.bean.JFRunnableStatus;
//import com.dukascopy.dds2.greed.agent.strategy.bean.StrategyNewBean;
//import com.dukascopy.dds2.greed.gui.JForexPreferencesFactory;
//import com.dukascopy.dds2.greed.gui.storage.utils.JStoreSettings;
//import com.dukascopy.dds2.greed.market.ICurrencyMarketManager;
//import com.dukascopy.dds2.greed.market.LiveCurrencyMarketManager;
//import com.dukascopy.dds2.greed.model.InstrumentTradability;
//import com.dukascopy.dds2.greed.service.ISession;
//import com.dukascopy.dds2.greed.service.PlatformServiceProvider;
//import com.dukascopy.dds2.greed.strategy.TransportHelper;
//import com.dukascopy.dds2.greed.util.AbstractCurrencyConverter;
//import com.dukascopy.dds2.greed.util.EnumConverter;
//import com.dukascopy.dds2.greed.util.FilePathManager;
//import com.dukascopy.dds2.greed.util.IFilePathManager;
//import com.dukascopy.dds2.greed.util.IOrderUtils;
//import com.dukascopy.dds2.greed.util.InstrumentUtils;
//import com.dukascopy.dds2.greed.util.NotificationUtilsProvider;
//import com.dukascopy.dds2.greed.util.ObjectUtils;
//import com.dukascopy.dds2.greed.util.ProtocolUtils;
//import com.dukascopy.dds3.transport.common.protocol.binary.StaticSessionDictionary;
//import com.dukascopy.ui.l10n.LocalizationManager;
//import com.dukascopy.util.OperatingSystemType;
//public class DClient implements IClient, ClientListener, InstrumentListener {
public class DClient implements ClientListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DClient.class);
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
    private DClient.ClientGUIListener clientGUIListener;
    //private volatile ISystemListenerExtended systemListener;
    private TransportClient transportClient;
    private GreedClientAuthorizationProvider authProvider;
    private boolean initialized = false;
    private boolean live;
    private String accountName;
    private String version;
    private Set<Instrument> instruments = new HashSet();
    private PrintStream out;
    private PrintStream err;
    private Properties serverProperties;
    private String internalIP;
    private volatile String sessionID;
    private String captchaId;
    private long temporaryKeys = -1L;
    private Map<Long, IJFRunnable<?>> runningJfRunnables = new HashMap();
   // private final Map<Long, JForexTaskManager<?, ?, ?>> taskManagers = new ConcurrentHashMap();
    private Map<INewsFilter.NewsSource, INewsFilter> newsFilters = new HashMap();
    private final Map<UUID, Long> pluginIdMap = new HashMap();
    private AccountInfoMessage lastAccountInfoMessage;
    private AccountInfoMessageInit lastAccountInfoMessageInit;
//    private ILotAmountProvider lotAmountProvider;
//    private DDSChartsController ddsChartsController;
//    private ClientChartsController clientChartsController;
//    private final ICurrencyMarketManager currencyMarketManager;
//    private IRemoteStrategyManager remoteManager;
    //private final JssEventsListener jssBroadcastListener;
    private IConsole console = new ConsoleAdapter() {
        public PrintStream getOut() {
            return DClient.this.out;
        }

        public PrintStream getErr() {
            return DClient.this.err;
        }
    };
//    private ISession session = new ISession() {
//        public void setSessionId(String sessionId) {
//            DClient.this.sessionID = sessionId;
//        }
//
//        public void recreateSessionId() {
//            DClient.this.sessionID = UUID.randomUUID().toString();
//        }
//
//        public String getSessionId() {
//            return DClient.this.sessionID;
//        }
//    };
//    private final Set<IJFRunnablesListener> runnablesListeners = new HashSet();

    public DClient() {
       // System.setProperty(PreferencesFactory.class.getName(), JForexPreferencesFactory.class.getName());
//        this.session.setSessionId(UUID.randomUUID().toString());
      //  PlatformServiceProvider.getInstance().setSession(this.session);
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
       // NotificationUtilsProvider.setNotificationUtils(new PrintStreamNotificationUtils(this.out, this.err));
        this.version = this.getClass().getPackage().getImplementationVersion();
        if (this.version == null || this.version.endsWith("SNAPSHOT")) {
            this.version = "99.99.99";
        }

        this.serverProperties = new Properties();
        this.clientGUIListener = new DClient.ClientGUIListener();
//        this.currencyMarketManager = new LiveCurrencyMarketManager();
//        this.jssBroadcastListener = new JssEventsAdapter() {
//            public void onStrategyBroadcast(RemoteStrategyBroadcastBean bean) {
//                Iterator var2 = DClient.this.taskManagers.values().iterator();
//
////                while(var2.hasNext()) {
////                    JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)var2.next();
////                    taskManager.onBroadcastMessage(bean.getTransactionId(), new StrategyBroadcastMessageImpl(bean.getTopic(), bean.getMessage(), System.currentTimeMillis()));
////                }
//
//            }
//        };
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
           // this.session.recreateSessionId();
            urlList = authServersCsv.split(",");
            authServerUrls = Arrays.asList(urlList);
            ticket = this.authenticate(authServerUrls, username, password, true, pin);
            this.accountName = username;
//          IFilePathManager fmanager = FilePathManager.getInstance();
//            fmanager.setStrategiesFolderPath(fmanager.getDefaultStrategiesFolderPath());
//            fmanager.setPluginsFolderPath(fmanager.getDefaultPluginsFolderPath());
//            OrdersProvider.createInstance(null);
            String servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");
//            ActivityLogger.init(servicesUrl, username);
//            List<String[]> feedCommissions = (List)this.serverProperties.get("feed.commission.history");
//            Set<String> supportedInstruments = (Set)this.serverProperties.get("instruments");
//            String downloadDataInBackgroundString = this.serverProperties.getProperty("DataDownloadInBackground");
//            FeedDataProvider.createFeedDataProvider("SINGLEJAR", feedCommissions, supportedInstruments, new JStoreSettings() {
//                public boolean isJStoreEnabled() {
//                    Object property = DClient.this.serverProperties.get("wlabel.jsEnable");
//                    return property == null ? false : Boolean.valueOf(property.toString());
//                }
//
//                public String getWlEnvironmentKey() {
//                    Object property = DClient.this.serverProperties.get("wlabel.uniEnvironmentKey");
//                    return property == null ? null : property.toString();
//                }
//
//                public String getLinkToCommunityRegistrationHomePage() {
//                    return "";
//                }
//            }, Boolean.parseBoolean(downloadDataInBackgroundString));
//            FeedDataProvider.setPlatformTicket(ticket);
            this.transportClient.connect();
           // this.connectToHistoryServer(username, true);
//            FeedDataProvider.getDefaultInstance().setInstrumentsSubscribed(FeedDataProvider.createInstrumentMapMappedWithSaveInCacheOption(this.instruments, true));
//            IndicatorsProvider.createInstance(new IndicatorsSettingsStorage(username));
//            PlatformSessionClientBean platformSessionClientBean = PlatformSessionClientBean.getInstance();
//            platformSessionClientBean.setTicket(ticket);
//            platformSessionClientBean.setUserName(username);
//            platformSessionClientBean.setPlatformType(PlatformType.STANDALONE);
//            platformSessionClientBean.setScheme(this.live ? "LIVE" : "DEMO");
//            InstrumentTradability.getInstance();
        } else if (!this.transportClient.isOnline()) {
            this.lastAccountInfoMessage = null;
            this.initialized = false;
            authServersCsv = this.getAuthServers(jnlpUrl);
           // this.session.recreateSessionId();
            urlList = authServersCsv.split(",");
            authServerUrls = Arrays.asList(urlList);
            ticket = this.authenticate(authServerUrls, username, password, true, pin);
            String servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");
//            ActivityLogger.init(servicesUrl, username);
//            FeedDataProvider.setPlatformTicket(ticket);
//            FeedDataProvider.getDefaultInstance().getFeedCommissionManager().clear();
            this.transportClient.connect();
           // this.connectToHistoryServer(username, true);
//            InstrumentTradability.clean();
//            InstrumentTradability.getInstance();
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
          //  this.session.recreateSessionId();
            ticket = this.authenticate(authServerUrls, username, password, encodePassword, pin);
            this.accountName = username;
//            OrdersProvider.createInstance((IOrderUtils)null);
//            servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");
//            ActivityLogger.init(servicesUrl, username);
//            List<String[]> feedCommissions = (List)this.serverProperties.get("feed.commission.history");
//            Set<String> supportedInstruments = (Set)this.serverProperties.get("instruments");
//            FeedDataProvider.createFeedDataProvider("SINGLEJAR", feedCommissions, supportedInstruments, Boolean.parseBoolean(this.serverProperties.getProperty("DataDownloadInBackground")));
//            FeedDataProvider.setPlatformTicket(ticket);
//            this.transportClient.connect();
//            this.connectToHistoryServer(username, Boolean.parseBoolean(this.serverProperties.getProperty("DataDownloadInBackground")));
//            FeedDataProvider.getDefaultInstance().setInstrumentsSubscribed(FeedDataProvider.createInstrumentMapMappedWithSaveInCacheOption(this.instruments, true));
//            IndicatorsProvider.createInstance(new IndicatorsSettingsStorage(username));
//            InstrumentTradability.getInstance();
        } else if (!this.transportClient.isOnline()) {
//            this.lastAccountInfoMessage = null;
//            this.initialized = false;
//            this.session.recreateSessionId();
//            ticket = this.authenticate(authServerUrls, username, password, encodePassword, pin);
//            servicesUrl = this.serverProperties.getProperty("services1.url", "") + this.serverProperties.getProperty("tradelog_sfx.url", "");
//            ActivityLogger.init(servicesUrl, username);
//            FeedDataProvider.getDefaultInstance().getFeedCommissionManager().clear();
//            FeedDataProvider.setPlatformTicket(ticket);
//            this.transportClient.connect();
//            this.connectToHistoryServer(username, true);
//            InstrumentTradability.clean();
//            InstrumentTradability.getInstance();
        }

    }

    private String getAuthServers(String jnlp) throws Exception {
        URL jnlpUrl = new URL(jnlp);
        InputStream jnlpIs = jnlpUrl.openConnection().getInputStream();
        Throwable var5 = null;

        Document doc;
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            doc = builder.parse(jnlpIs);
        } catch (Throwable var20) {
            var5 = var20;
            throw var20;
        } finally {
            if (jnlpIs != null) {
                if (var5 != null) {
                    try {
                        jnlpIs.close();
                    } catch (Throwable var19) {
                        var5.addSuppressed(var19);
                    }
                } else {
                    jnlpIs.close();
                }
            }

        }

        String authServersCsv_no_srp6 = null;
        String authServersCsv_srp6 = null;
        NodeList jnlpNodes = doc.getElementsByTagName("jnlp");
        if (jnlpNodes.getLength() < 1) {
            throw new Exception("Can't find jnlp element");
        } else {
            Element jnlpNode = (Element)jnlpNodes.item(0);
            NodeList resourcesNodes = jnlpNode.getElementsByTagName("resources");
            if (resourcesNodes.getLength() < 1) {
                throw new Exception("Can't find resources element");
            } else {
                Element resourcesNode = (Element)resourcesNodes.item(0);
                NodeList propertyNodes = resourcesNode.getElementsByTagName("property");

                for(int i = 0; i < propertyNodes.getLength(); ++i) {
                    Element propertyElement = (Element)propertyNodes.item(i);
                    String nameAttribute = propertyElement.getAttribute("name");
                    if (nameAttribute != null && nameAttribute.trim().equals("jnlp.client.mode")) {
                        String clientMode = propertyElement.getAttribute("value").trim();
                      //  this.live = !ObjectUtils.isNullOrEmpty(clientMode) && clientMode.equals("LIVE");
                    } else if (nameAttribute != null && nameAttribute.trim().equals("jnlp.login.url")) {
                        authServersCsv_no_srp6 = propertyElement.getAttribute("value").trim();
                    } else if (nameAttribute != null && nameAttribute.trim().equals("jnlp.srp6.login.url")) {
                        authServersCsv_srp6 = propertyElement.getAttribute("value").trim();
                    }
                }

                if (Strings.isNullOrEmpty(authServersCsv_no_srp6) && Strings.isNullOrEmpty(authServersCsv_srp6)) {
                    throw new Exception("Can't find property with name attribute equals to jnlp.login.url or jnlp.srp6.login.url");
                } else {
                    return authServersCsv_srp6;
                }
            }
        }
    }

    public BufferedImage getCaptchaImage(String jnlp) throws Exception {
        String authServersCsv = this.getAuthServers(jnlp);
        String[] urlList = authServersCsv.split(",");
        List<String> authServers = Arrays.asList(urlList);
        AuthorizationClient
            authorizationClient = AuthorizationClient.getInstance(authServers, this.version);
        Map<String, BufferedImage> imageCaptchaMap = authorizationClient.getImageCaptcha();
        if (!imageCaptchaMap.isEmpty()) {
            Map.Entry<String, BufferedImage> imageCaptchaEntry = (Map.Entry)imageCaptchaMap.entrySet().iterator().next();
            this.captchaId = (String)imageCaptchaEntry.getKey();
            return (BufferedImage)imageCaptchaEntry.getValue();
        } else {
            return null;
        }
    }

    private String authenticate(Collection<String> authServerUrls, String username, String password, boolean encodePassword, String pin) throws Exception {
        AuthorizationClient
            authorizationClient = AuthorizationClient.getInstance(authServerUrls, this.version);
        if (!encodePassword && pin != null) {
            throw new Exception("(EncodePassword == false && pin != null) == NOT SUPPORTED");
        } else {
            AuthorizationServerResponse authorizationServerResponse = null;

                authorizationServerResponse = authorizationClient.getAPIsAndTicketUsingLogin_SRP6(username, password, this.captchaId, pin, this.sessionID, "DDS3_JFOREXSDK");

//
//            if (authorizationServerResponse != null) {
//                String authResponse = authorizationServerResponse.getFastestAPIAndTicket();
//                LOGGER.debug(authResponse);
//                String host;
//                if (!authorizationServerResponse.isOK()) {
//                    AuthorizationServerResponseCode
//                        authorizationServerResponseCode = authorizationServerResponse.getResponseCode();
//                    if (authorizationServerResponse.isEmptyResponse()) {
//                        throw new IOException("Authentication failed");
//                    } else if (AuthorizationServerResponseCode.MINUS_ONE_OLD_ERROR != authorizationServerResponseCode && AuthorizationServerResponseCode.AUTHENTICATION_AUTHORIZATION_ERROR != authorizationServerResponseCode) {
//                        if (authorizationServerResponse.isWrongVersion()) {
//                            throw new JFVersionException("Incorrect version");
//                        } else if (authorizationServerResponse.isNoAPIServers()) {
//                            throw new JFVersionException("System offline");
//                        } else if (authorizationServerResponse.isSystemError()) {
//                            throw new JFVersionException("System error");
//                        } else if (authorizationServerResponse.isSrp6requestWithProperties() && authorizationServerResponseCode == AuthorizationServerResponseCode.NO_PROPERTIES_RECEIVED) {
//                            throw new JFException(JFException.Error.NO_ACCOUNT_SETTINGS_RECEIVED, authorizationServerResponseCode.getError());
//                        } else {
//                            host = authorizationServerResponseCode.getError();
//                            throw new JFException(host);
//                        }
//                    } else {
//                        throw new JFAuthenticationException("Incorrect username or password");
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
//                        this.lotAmountProvider = new DClient.LotAmountProvider();
//
//                        try {
//                            InetAddress localhost = InetAddress.getLocalHost();
//                            this.internalIP = localhost != null ? localhost.getHostAddress() : null;
//                        } catch (UnknownHostException var18) {
//                            LOGGER.error("Can't detect local IP : " + var18.getMessage());
//                            this.internalIP = "";
//                        }
//
//                        String userAgent = CommonContext.getUserAgent("DDS3_JFOREXSDK");
//                        LOGGER.debug("UserAgent: " + userAgent);
//                        if (this.authProvider == null) {
//                            this.authProvider = new GreedClientAuthorizationProvider(username, ticket, this.sessionID);
//                        } else {
//                            this.authProvider.setSessionId(this.sessionID);
//                            this.authProvider.setTicket(ticket);
//                            this.authProvider.setLogin(username);
//                        }
//
//                        this.authProvider.setUserAgent(userAgent);
//                        if (this.transportClient == null) {
//                            TransportClientBuilder builder = TransportClient.builder();
//                            builder.withStaticSessionDictionary(new StaticSessionDictionary()).withTransportName("DDS2 Standalone Transport Client").withAddress(new ServerAddress(host, port)).withAuthorizationProvider(this.authProvider).withUserAgent(userAgent).withClientListener(this).withEventPoolSize(2).withUseSSL(true).withSecurityExceptionHandler(new SecurityExceptionHandler() {
//                                public boolean isIgnoreSecurityException(X509Certificate[] chain, String authType, CertificateException ex) {
//                                    DClient.LOGGER.warn("Security exception : " + ex);
//                                    return true;
//                                }
//                            });
//                            if (!OperatingSystemType.LINUX && !OperatingSystemType.MACOSX) {
//                                builder.withSecondaryConnectionPingTimeout(TRANSPORT_PING_TIMEOUT);
//                            } else {
//                                builder.withSecondaryConnectionPingInterval(5000L).withPrimaryConnectionPingTimeout(2000L).withSecondaryConnectionPingTimeout(2000L);
//                            }
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
        }
        return "";
    }

    public synchronized void reconnect() {
        if (this.transportClient != null && !this.transportClient.isOnline()) {
            this.transportClient.connect();
        }

    }

    public synchronized void disconnect() {
//        Iterator var1 = this.getStartedStrategies().keySet().iterator();
//
//        while(var1.hasNext()) {
//            Long processId = (Long)var1.next();
//            this.stopStrategy(processId);
//        }

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
            ListenableFuture<Object> listenableFuture = this.transportClient.sendMessageAsync(new QuitRequestMessage());
            listenableFuture.get();
        } catch (Throwable var2) {
            LOGGER.error(var2.getMessage(), var2);
        }

    }

    private void terminateTransportClient() {
        if (this.transportClient != null) {
            this.transportClient.disconnect();
            this.transportClient.terminate();
            this.transportClient = null;
            this.authProvider = null;
        }

    }
//
//    private void nullifySession(LocalCacheManager localCAcheManager) {
//        if (localCAcheManager != null) {
//            localCAcheManager.shutdown();
//        }
//
//        this.newsFilters.clear();
//        this.instruments.clear();
//        this.captchaId = null;
//        this.lastAccountInfoMessage = null;
//        this.accountName = null;
//        this.internalIP = null;
//        this.sessionID = null;
//        this.live = false;
//        this.serverProperties = new Properties();
//        this.initialized = false;
//    }

    public boolean isConnected() {
        return this.transportClient != null && this.transportClient.isOnline() && this.initialized;
    }

    private void connectedInit() {
//        InitRequestMessage initRequestMessage = new InitRequestMessage();
//        initRequestMessage.setSendGroups(true);
//        initRequestMessage.setSendPacked(true);
//        this.transportClient.sendMessageNaive(initRequestMessage);
//        this.setSubscribedInstruments(this.instruments);
//        Iterator var2 = this.newsFilters.values().iterator();
//
//        while(var2.hasNext()) {
//            INewsFilter newsFilter = (INewsFilter)var2.next();
//            NewsSubscribeRequest newsSubscribeRequest = new NewsSubscribeRequest();
//            Set<com.dukascopy.dds3.transport.msg.news.NewsSource> sources = TransportHelper.toTransportSources(newsFilter.getNewsSources());
//            newsSubscribeRequest.setSources(sources);
//            newsSubscribeRequest.setHot(newsFilter.isOnlyHot());
//            newsSubscribeRequest.setGeoRegions(EnumConverter.convert(newsFilter.getCountries(), GeoRegion.class));
//            newsSubscribeRequest.setMarketSectors(EnumConverter.convert(newsFilter.getMarketSectors(), MarketSector.class));
//            newsSubscribeRequest.setIndicies(EnumConverter.convert(newsFilter.getStockIndicies(), StockIndex.class));
//            newsSubscribeRequest.setCurrencies(EnumConverter.convertByName(newsFilter.getCurrencies()));
//            newsSubscribeRequest.setEventCategories(EnumConverter.convert(newsFilter.getEventCategories(), EventCategory.class));
//            newsSubscribeRequest.setKeywords(newsFilter.getKeywords());
//            newsSubscribeRequest.setFrom(newsFilter.getFrom() == null ? -9223372036854775808L : newsFilter.getFrom().getTime());
//            newsSubscribeRequest.setTo(newsFilter.getTo() == null ? -9223372036854775808L : newsFilter.getTo().getTime());
//            newsSubscribeRequest.setCalendarType((CalendarType)EnumConverter.convert(newsFilter.getType(), CalendarType.class));
//            LOGGER.debug("Subscribing : " + newsSubscribeRequest);
//            this.transportClient.sendMessageNaive(newsSubscribeRequest);
//        }

    }
//
//    public synchronized void setSystemListener(final ISystemListener userSystemListener) {
//        this.systemListener = new ISystemListenerExtended() {
//            public void onStart(long processId) {
//                userSystemListener.onStart(processId);
//            }
//
//            public void onStop(long processId) {
//                DClient var3 = DClient.this;
//                synchronized(DClient.this) {
//                    DClient.this.taskManagers.remove(processId);
//                    DClient.this.runningJfRunnables.remove(processId);
//                }
//
//                userSystemListener.onStop(processId);
//                getInstance().flush();
//            }
//
//            public void onConnect() {
//                userSystemListener.onConnect();
//            }
//
//            public void onDisconnect() {
//                userSystemListener.onDisconnect();
//            }
//
//            public void subscribeToInstruments(Set<Instrument> instruments, boolean lock) {
//                Set<Instrument> combinedInstruments = new HashSet(DClient.this.instruments);
//                combinedInstruments.addAll(instruments);
//                DClient.this.setSubscribedInstruments(combinedInstruments);
//            }
//
//            public Set<Instrument> getSubscribedInstruments() {
//                DClient var1 = DClient.this;
//                synchronized(DClient.this) {
//                    return new HashSet(DClient.this.instruments);
//                }
//            }
//
//            public void unsubscribeInstruments(Set<Instrument> instruments) {
//                DClient.this.unsubscribeInstruments(instruments);
//            }
//        };
//        Iterator var2 = this.taskManagers.values().iterator();
//
//        while(var2.hasNext()) {
//            JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)var2.next();
//            taskManager.setSystemListener(this.systemListener);
//        }
//
//    }


//    public void addClientGUIListener(IClientGUIListener listener) {
//        this.clientGUIListener.addListener(listener);
//    }
//    public void removeClientGUIListener(IClientGUIListener listener) {
//        this.clientGUIListener.removeListener(listener);
//    }
//
//    public synchronized void setStrategyEventsListener(StrategyEventsCallback strategyEventsCallback) {
//        Iterator var2 = this.taskManagers.values().iterator();
//
//        while(var2.hasNext()) {
//            JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)var2.next();
//            taskManager.setStrategyEventsCallback(strategyEventsCallback);
//        }
//
//    }

    public void feedbackMessageReceived(ITransportClient client, ProtocolMessage message) {
        TransportClient providedClient = (TransportClient)TransportClient.class.cast(client);
//        if (providedClient == null || providedClient.isOnline() && !providedClient.isTerminated() && !ObjectUtils.isNullOrEmpty(this.transportClient) && ObjectUtils.isEqual(providedClient.getTransportSessionId(), this.transportClient.getTransportSessionId())) {
//            if (message instanceof AccountInfoMessage) {
//                this.onAccountInfoMessage((AccountInfoMessage)message);
//            } else if (message instanceof PackedAccountInfoMessage) {
//                this.onPackedAccountInfoMessage((PackedAccountInfoMessage)message);
//            } else if (message instanceof OrderGroupMessage) {
//                this.onOrderGroupMessage(message);
//            } else if (message instanceof OrderMessage) {
//                this.onOrderMessage(message);
//            } else if (message instanceof MergePositionsMessage) {
//                OrdersProvider.getInstance().groupsMerged((MergePositionsMessage)message);
//            } else if (message instanceof CurrencyMarket) {
//                if (this.lastAccountInfoMessage != null) {
//                    CurrencyMarket currencyMarket = (CurrencyMarket)message;
//                    if (this.instruments.contains(Instrument.fromString(currencyMarket.getInstrument()))) {
//                        this.currencyMarketManager.currencyUpdateReceived(currencyMarket, new TickListener() {
//                            public boolean tickReceived(CurrencyMarket hTick, boolean realTime) {
//                                FeedDataProvider feedDataProvider = FeedDataProvider.getDefaultInstance();
//                                if (feedDataProvider != null && !feedDataProvider.isStopped()) {
//                                    feedDataProvider.tickReceived(hTick, realTime);
//                                    Iterator var4 = DClient.this.taskManagers.values().iterator();
//
//                                    while(var4.hasNext()) {
//                                        JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)var4.next();
//                                        if (taskManager instanceof StrategyTaskManager
//                                            && DClient.this.lastAccountInfoMessage != null && DClient.this.instruments.contains(Instrument.fromString(hTick.getInstrument()))) {
//                                            ((StrategyTaskManager)taskManager).onMarketState(hTick);
//                                        }
//                                    }
//
//                                    return true;
//                                } else {
//                                    DClient.LOGGER.warn("Process tick skipped. FeedDataProvider is stopped.");
//
//                                    return false;
//                                }
//                            }
//                        });
//                    }
//                }
//            } else if (!(message instanceof ErrorResponseMessage) && !(message instanceof AbstractDFSMessage)) {
//                if (message instanceof AccountInfoMessagePack) {
//                    this.onAccountInfoMessagePack((AccountInfoMessagePack)AccountInfoMessagePack.class.cast(message));
//                } else if (message instanceof QuoteSubscriptionResponseMessage) {
//                    try {
//                        FeedDataProvider.getDefaultInstance().getSplitsManager().addSplits((QuoteSubscriptionResponseMessage)message);
//                    } catch (SplitsMessageException var14) {
//                        LOGGER.error(var14.getMessage(), var14);
//                    }
//                } else if (message instanceof ApiSplitGroupMessage) {
//                    try {
//                        FeedDataProvider.getDefaultInstance().getSplitsManager().addSplits((ApiSplitGroupMessage)message);
//                    } catch (SplitsMessageException var13) {
//                        LOGGER.error(var13.getMessage(), var13);
//                    }
//                } else if (message instanceof ApiSplitMessage) {
//                    try {
//                        FeedDataProvider.getDefaultInstance().getSplitsManager().addSplit((ApiSplitMessage)message);
//                    } catch (SplitsMessageException var12) {
//                        LOGGER.error(var12.getMessage(), var12);
//                    }
//                }
//            } else {
//                FeedDataProvider fdp = FeedDataProvider.getDefaultInstance();
//                if (message instanceof DFHistoryChangedMessage) {
//                    ICacheChangeTracker tracker = fdp == null ? null : fdp.getCacheChangeTracker();
//                    if (tracker != null) {
//                        ((CacheChangeTracker)tracker).handleDFHistoryChangedMessage((DFHistoryChangedMessage)message);
//                    }
//                } else if (!(message instanceof DFHistoryChangeSubscribeResponseMessage) && fdp != null && !fdp.isStopped()) {
//                    fdp.processMessage(message);
//                }
//            }
//
//            Iterator var16 = this.taskManagers.values().iterator();
//
//            while(true) {
//                while(var16.hasNext()) {
//                    JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)var16.next();
//                    if (message instanceof OrderGroupMessage) {
//                        taskManager.onOrderGroupReceived((OrderGroupMessage)message);
//                    } else if (message instanceof OrderMessage) {
//                        this.processOrderMessage((OrderMessage)message, taskManager);
//                    } else if (!(message instanceof NotificationMessage)) {
//                        if (message instanceof AccountInfoMessage) {
//                            taskManager.updateAccountInfo((AccountInfoMessage)message);
//                        } else if (message instanceof PackedAccountInfoMessage) {
//                            this.processPackedAccountInfoMessage((PackedAccountInfoMessage)message, taskManager);
//                        } else if (message instanceof MergePositionsMessage) {
//                            taskManager.onOrdersMergedMessage((MergePositionsMessage)message);
//                        } else if (message instanceof OrderSyncMessage) {
//                            LOGGER.trace("Skip handling OrderSyncMessage : {}", message);
//                        } else if (message instanceof InstrumentStatusUpdateMessage) {
//                            InstrumentStatusUpdateMessage instrumentStatusUpdateMessage = (InstrumentStatusUpdateMessage)InstrumentStatusUpdateMessage.class.cast(message);
//                            Instrument instrument = Instrument.fromString(instrumentStatusUpdateMessage.getInstrument());
//                            TradabilityState state = instrumentStatusUpdateMessage.getState();
//                            boolean tradable = ObjectUtils.isEqual(TradabilityState.TRADING_ALLOWED, state);
//                            Long timestamp = message.getTimestamp();
//                            if (timestamp == null) {
//                                FeedDataProvider feedDataProvider = FeedDataProvider.getDefaultInstance();
//                                if (feedDataProvider != null && !feedDataProvider.isStopped()) {
//                                    timestamp = feedDataProvider.getCurrentTime();
//                                }
//                            }
//
//                            taskManager.onIntrumentUpdate(instrument, tradable, timestamp);
//                            InstrumentTradability.getInstance().setInstrumentState(instrumentStatusUpdateMessage);
//                        } else if (message instanceof NewsStoryMessage) {
//                            taskManager.onNewsMessage((NewsStoryMessage)message);
//                        } else if (message instanceof NewsCommandMessage) {
//                            taskManager.onNewsCommandMessage((NewsCommandMessage)message);
//                        } else if (message instanceof AccountInfoMessagePack) {
//                            AccountInfoMessagePack pack = (AccountInfoMessagePack)AccountInfoMessagePack.class.cast(message);
//                            ProtocolMessage accountInfo = pack.getAccountInfo();
//                            if (accountInfo != null) {
//                                if (accountInfo instanceof PackedAccountInfoMessage) {
//                                    this.processPackedAccountInfoMessage((PackedAccountInfoMessage)accountInfo, taskManager);
//                                } else if (accountInfo instanceof AccountInfoMessage) {
//                                    taskManager.updateAccountInfo((AccountInfoMessage)message);
//                                }
//                            }
//                        } else if (!(message instanceof OkResponseMessage) && !(message instanceof CandleHistoryGroupMessage) && !(message instanceof StrategyBroadcastMessage) && !(message instanceof CurrencyMarket) && !(message instanceof QuoteSubscriptionResponseMessage) && !(message instanceof QuoteUnsubscriptionResponseMessage) && !(message instanceof ApiSplitGroupMessage) && !(message instanceof ApiSplitMessage)) {
//                            LOGGER.debug("Unrecognized protocol message : " + message.getClass() + " / " + message);
//                            return;
//                        }
//                    } else {
//                        NotificationMessage notificationMessage = (NotificationMessage)message;
//                        if (notificationMessage.getLevel() != null && !notificationMessage.getLevel().equals(
//                            NotificationLevel.INFO)) {
//                            if (notificationMessage.getLevel().equals(NotificationLevel.WARNING)) {
//                                NotificationUtilsProvider.getNotificationUtils().postWarningMessage(notificationMessage.getText());
//                            } else if (notificationMessage.getLevel().equals(NotificationLevel.ERROR)) {
//                                NotificationUtilsProvider.getNotificationUtils().postErrorMessage(notificationMessage.getText());
//                            } else {
//                                NotificationUtilsProvider.getNotificationUtils().postErrorMessage(notificationMessage.getText());
//                            }
//                        } else {
//                            NotificationUtilsProvider.getNotificationUtils().postInfoMessage(notificationMessage.getText());
//                        }
//
//                        taskManager.onNotifyMessage(notificationMessage);
//                    }
//                }
//
//                return;
//            }
//        }
    }

 //   private void processPackedAccountInfoMessage(PackedAccountInfoMessage packedAccountInfoMessage, JForexTaskManager<?, ?, ?> taskManager) {
//        AccountInfoMessageInit accountInfoMessageInit = packedAccountInfoMessage.getAccount();
//        taskManager.updateAccountInfo(accountInfoMessageInit);
//        OrdersProvider.getInstance().orderSynch(packedAccountInfoMessage);
//        taskManager.orderSynch(packedAccountInfoMessage);
//        taskManager.onConnect(true);
//        Iterator var4 = packedAccountInfoMessage.getGroups().iterator();
//
//        while(var4.hasNext()) {
//            OrderGroupMessage orderGroupMessage = (OrderGroupMessage)var4.next();
//            taskManager.onOrderGroupReceived(orderGroupMessage);
//        }
//
//        var4 = packedAccountInfoMessage.getOrders().iterator();
//
//        while(var4.hasNext()) {
//            OrderMessage orderMessage = (OrderMessage)var4.next();
//            this.processOrderMessage(orderMessage, taskManager);
//        }

 //   }

 //   private void processOrderMessage(OrderMessage message, JForexTaskManager<?, ?, ?> taskManager) {
//        OrderMessageExt orderMessage = null;
//        if (message instanceof OrderMessageExt) {
//            orderMessage = (OrderMessageExt)message;
//        } else {
//            orderMessage = ProtocolUtils.getInstance(message);
//        }
//
//        taskManager.onOrderReceived(orderMessage);
//    }

    private void onAccountInfoMessage(AccountInfoMessage message) {
//        FeedDataProvider feedDataProvider = FeedDataProvider.getDefaultInstance();
//        if (feedDataProvider != null && !feedDataProvider.isStopped()) {
//            AccountProvider.updateAccountInfo(message, this.accountName);
//            feedDataProvider.setCurrentTime(message.getTimestamp());
//            OrdersProvider.getInstance().updateAccountInfoData(message);
//            if (message instanceof AccountInfoMessageInit) {
//                this.lastAccountInfoMessageInit = (AccountInfoMessageInit)message;
//                Map<String, FeedCommission> feedCommissions = ((AccountInfoMessageInit)AccountInfoMessageInit.class.cast(message)).getFeedCommissionMap();
//                if (feedCommissions != null) {
//                    feedDataProvider.getFeedCommissionManager().addFeedCommissions(feedCommissions, message.getTimestamp());
//                }
//            } else {
//                this.lastAccountInfoMessage = message;
//                if (!this.initialized) {
//                    this.setSubscribedInstruments(this.instruments);
//                    this.initialized = true;
//                    this.fireConnected();
//                }
//            }
//        }

   }

    private void onPackedAccountInfoMessage(PackedAccountInfoMessage packedAccountInfoMessage) {
//        AccountInfoMessageInit accountInfoMessage = packedAccountInfoMessage.getAccount();
//        if (accountInfoMessage != null) {
//            this.onAccountInfoMessage(accountInfoMessage);
//        }
//
//        Iterator var3 = packedAccountInfoMessage.getOrders().iterator();
//
//        while(var3.hasNext()) {
//            OrderMessage orderMessage = (OrderMessage)var3.next();
//            this.onOrderMessage(orderMessage);
//        }
//
//        var3 = packedAccountInfoMessage.getGroups().iterator();
//
//        while(var3.hasNext()) {
//            OrderGroupMessage orderGroupMessage = (OrderGroupMessage)var3.next();
//            this.onOrderGroupMessage(orderGroupMessage);
//        }

    }

    private void onOrderMessage(ProtocolMessage protocolMessage) {
//        OrderMessage orderMessage = (OrderMessage)protocolMessage;
//        PlatformAccountImpl account = AccountProvider.getAccount();
//        if (account != null) {
//            OrderExecutionStatisticsRegistry.onReceived(orderMessage, account.isGlobal());
//        }
//
//        orderMessage = ProtocolUtils.updateAmounts(orderMessage);
//        OrdersProvider.getInstance().updateOrder(orderMessage);
//        String instrumentStr = orderMessage.getInstrument();
//        if (instrumentStr != null) {
//            Instrument instrument = Instrument.fromString(instrumentStr);
//            if (!this.instruments.contains(instrument)) {
//                LOGGER.debug("Order received for instrument [" + instrument + "], adding instrument to the list of the subscribed instruments");
//                this.setSubscribedInstruments(this.instruments);
//            }
//        }

    }

    private void onOrderGroupMessage(ProtocolMessage protocolMessage) {
//        OrderGroupMessage orderGroupMessage = (OrderGroupMessage)protocolMessage;
//        PlatformAccountImpl account = AccountProvider.getAccount();
//        if (account != null) {
//            OrderExecutionStatisticsRegistry.onReceived(orderGroupMessage, account.isGlobal());
//        }
//
//        orderGroupMessage = ProtocolUtils.updateAmounts(orderGroupMessage);
//        OrdersProvider.getInstance().updateOrderGroup(orderGroupMessage);
//        String instrumentStr = orderGroupMessage.getInstrument();
//        if (instrumentStr != null) {
//            Instrument instrument = Instrument.fromString(instrumentStr);
//            if (!this.instruments.contains(instrument)) {
//                LOGGER.debug("Order group received for instrument [" + instrument + "], adding instrument to the list of the subscribed instruments");
//                this.setSubscribedInstruments(this.instruments);
//            }
//        } else {
//            OrderMessage openingOrder = ProtocolUtils.getOpeningOrder(orderGroupMessage);
//            instrumentStr = openingOrder == null ? null : openingOrder.getInstrument();
//            if (instrumentStr != null) {
//                Instrument instrument = Instrument.fromString(instrumentStr);
//                if (!this.instruments.contains(instrument)) {
//                    LOGGER.debug("Order group received for instrument [" + instrument + "], adding instrument to the list of the subscribed instruments");
//                    this.setSubscribedInstruments(this.instruments);
//                }
//            }
//        }

    }

    private void onAccountInfoMessagePack(AccountInfoMessagePack pack) {
//        ProtocolMessage accountInfo = pack.getAccountInfo();
//        if (accountInfo != null) {
//            if (accountInfo instanceof PackedAccountInfoMessage) {
//                this.onPackedAccountInfoMessage((PackedAccountInfoMessage)PackedAccountInfoMessage.class.cast(accountInfo));
//            } else if (accountInfo instanceof AccountInfoMessage) {
//                this.onAccountInfoMessage((AccountInfoMessage)AccountInfoMessage.class.cast(accountInfo));
//            }
//        }

    }

    public synchronized void authorized(ITransportClient client) {
        this.connectedInit();
        if (this.initialized) {
            this.fireConnected();
        }

       // this.currencyMarketManager.start();
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
//            FeedDataProvider.getDefaultInstance().disconnected();
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

//    public long startStrategy(IStrategy strategy) throws IllegalStateException, NullPointerException {
//        return this.startJFRunnable(strategy, StrategyTaskManager.Builder.newSdkInstance(), (IStrategyListener)null, (IStrategyExceptionHandler)null, JForexTaskManager.Environment.LOCAL_EMBEDDED, true, (String)null);
//    }
//
//    public long startStrategy(IStrategy strategy, IStrategyExceptionHandler exceptionHandler) {
//        return this.startJFRunnable(strategy, StrategyTaskManager.Builder.newSdkInstance(), (IStrategyListener)null, exceptionHandler, JForexTaskManager.Environment.LOCAL_EMBEDDED, true, (String)null);
//    }

//    private <RUNNABLE extends IJFRunnable<?>, UI extends IUserInterface, MANAGER extends JForexTaskManager<?, RUNNABLE, UI>, BUILDER extends JForexTaskManager.Builder<?, RUNNABLE, UI, MANAGER>> long startJFRunnable(final RUNNABLE jfRunnable, BUILDER builder, IStrategyListener strategyListener, IStrategyExceptionHandler exceptionHandler, JForexTaskManager.Environment taskManagerEnvironment, boolean fullAccessGranted, String strategyHash) throws IllegalStateException, NullPointerException {
//        JForexTaskManager taskManager;
//        long temporaryProcessId;
//        synchronized(this) {
//            if (!this.transportClient.isOnline()) {
//                throw new IllegalStateException("Not connected");
//            }
//
//            if (!this.initialized) {
//                throw new IllegalStateException("Not initialized");
//            }
//
//            if (jfRunnable == null) {
//                throw new NullPointerException("Runnable is null");
//            }
//
//            if (exceptionHandler == null) {
//                exceptionHandler = new DClient.DefaultStrategyExceptionHandler();
//            }
//
//            taskManager = builder.build(taskManagerEnvironment, this.live, this.accountName, this.console, this.transportClient, this.ddsChartsController, this.lotAmountProvider, (IStrategyExceptionHandler)exceptionHandler, this.lastAccountInfoMessage, this.lastAccountInfoMessageInit, this.serverProperties.getProperty("external_ip"), this.internalIP, this.serverProperties, this.clientGUIListener);
//            taskManager.setSystemListener((ISystemListenerExtended)ISystemListenerExtended.class.cast(this.getSystemListener()));
//            if (exceptionHandler instanceof DClient.DefaultStrategyExceptionHandler) {
//                ((DClient.DefaultStrategyExceptionHandler)exceptionHandler).setTaskManager(taskManager);
//            }
//
//            temporaryProcessId = (long)(this.temporaryKeys--);
//            this.taskManagers.put(temporaryProcessId, taskManager);
//            this.runningJfRunnables.put(temporaryProcessId, jfRunnable);
//        }
//
//        String strategyKey = jfRunnable.getClass().getSimpleName();
//        String classMD5 = "";
//        if (strategyHash == null) {
//            classMD5 = ObjectUtils.getMD5(jfRunnable.getClass());
//            strategyKey = strategyKey + ".class" + (classMD5 == null ? "" : " " + classMD5);
//        } else {
//            strategyKey = strategyKey + ".jfx " + strategyHash;
//        }
//
//        final long processId = taskManager.startJfRunnable(jfRunnable, strategyListener, strategyKey, fullAccessGranted, classMD5, (List)null, -1);
//        synchronized(this) {
//            if (processId == 0L) {
//                this.taskManagers.remove(temporaryProcessId);
//                this.runningJfRunnables.remove(temporaryProcessId);
//            } else {
//                this.taskManagers.remove(temporaryProcessId);
//                this.runningJfRunnables.remove(temporaryProcessId);
//                this.taskManagers.put(processId, taskManager);
//                this.runningJfRunnables.put(processId, jfRunnable);
//                (new Thread(new Runnable() {
//                    public void run() {
//                        try {
//                            Thread.sleep(100L);
//                        } catch (InterruptedException var6) {
//                            var6.printStackTrace();
//                        }
//
//                        if (jfRunnable instanceof IStrategy) {
//                            synchronized(DClient.this.runnablesListeners) {
//                                Iterator var2 = DClient.this.runnablesListeners.iterator();
//
//                                while(var2.hasNext()) {
//                                    IJFRunnablesListener listener = (IJFRunnablesListener)var2.next();
//                                    StrategyNewBean bean = StrategyNewBean.createFilelessBean((IStrategy)jfRunnable);
//                                    bean.setStatus(JFRunnableStatus.STARTING);
//                                    listener.onStrategyStart(processId, bean);
//                                }
//                            }
//                        }
//
//                    }
//                })).start();
//            }
//
//            return processId;
//        }
//    }
//
//    public IStrategy loadStrategy(File strategyBinaryFile) throws IOException, GeneralSecurityException {
//        JFXPack jfxPack = JFXPack.loadFromPack(strategyBinaryFile);
//        if (jfxPack == null) {
//            return null;
//        } else if (jfxPack.hasConverterFiles()) {
//            String converterNotSupported = LocalizationManager.getText("converter.not.supported");
//            NotificationUtilsProvider.getNotificationUtils().postWarningMessage(converterNotSupported);
//            LOGGER.warn(converterNotSupported);
//            return null;
//        } else {
//            return (IStrategy)jfxPack.getTarget();
//        }
//    }

    public synchronized void stopStrategy(long processId) {
//        if (this.runningJfRunnables.containsKey(processId)) {
//            JForexTaskManager<?, ?, ?> taskManager = (JForexTaskManager)this.taskManagers.remove(processId);
//            taskManager.stopStrategy();
//            IJFRunnable<?> jfRunnable = (IJFRunnable)this.runningJfRunnables.remove(processId);
//            if (jfRunnable != null && jfRunnable instanceof IStrategy) {
//                Set var5 = this.runnablesListeners;
//                synchronized(this.runnablesListeners) {
//                    Iterator var6 = this.runnablesListeners.iterator();
//
//                    while(var6.hasNext()) {
//                        IJFRunnablesListener listener = (IJFRunnablesListener)var6.next();
//                        listener.onStrategyStop(processId, StrategyNewBean.createFilelessBean((IStrategy)jfRunnable));
//                    }
//                }
//            }
//
//        }
    }

//    public synchronized ISystemListener getSystemListener() {
//        if (this.systemListener == null) {
//            this.setSystemListener(new DClient.DefaultSystemListener(null));
//        }
//
//        return this.systemListener;
//    }

    public synchronized Map<Long, IStrategy> getStartedStrategies() {
        Map<Long, IStrategy> map = new HashMap();
        Iterator var2 = this.runningJfRunnables.entrySet().iterator();

        while(var2.hasNext()) {
            Map.Entry<Long, IJFRunnable<?>> entry = (Map.Entry)var2.next();
            if (entry.getValue() instanceof IStrategy) {
                map.put(entry.getKey(), (IStrategy)entry.getValue());
            }
        }

        return map;
    }

//    public synchronized Map<UUID, Plugin> getRunningPlugins() {
//        Map<UUID, Plugin> map = new HashMap();
//        Iterator var2 = this.runningJfRunnables.entrySet().iterator();
//
//        while(true) {
//            while(true) {
//                Map.Entry runnableEntry;
//                do {
//                    if (!var2.hasNext()) {
//                        return map;
//                    }
//
//                    runnableEntry = (Map.Entry)var2.next();
//                } while(!(runnableEntry.getValue() instanceof Plugin));
//
//                long id = (Long)runnableEntry.getKey();
//                Iterator var6 = this.pluginIdMap.entrySet().iterator();
//
//                while(var6.hasNext()) {
//                    Map.Entry<UUID, Long> pluginEntry = (Map.Entry)var6.next();
//                    if (((Long)pluginEntry.getValue()).equals(id)) {
//                        map.put(pluginEntry.getKey(), (Plugin)runnableEntry.getValue());
//                        break;
//                    }
//                }
//            }
//        }
//    }
//
   public synchronized void setSubscribedInstruments(final Set<Instrument> instruments) {
//        try {
//            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
//                public Void run() throws Exception {
//                    Set<Instrument> mergedInstruments = new HashSet(instruments);
//                    synchronized(DClient.this.taskManagers) {
//                        Iterator var3 = DClient.this.taskManagers.values().iterator();
//
//                        while(true) {
//                            if (!var3.hasNext()) {
//                                break;
//                            }
//
//                            JForexTaskManager<?, ?, ?> engine = (JForexTaskManager)var3.next();
//                            mergedInstruments.addAll(engine.getRequiredInstruments());
//                        }
//                    }
//
//                    if (DClient.this.transportClient != null) {
//                        mergedInstruments.addAll(OrdersProvider.getInstance().getOrderInstruments());
//                        mergedInstruments.addAll(OrdersProvider.getInstance().getExposureInstruments());
//                    }
//
//                    Iterator var2 = (new HashSet(mergedInstruments)).iterator();
//
//                    while(var2.hasNext()) {
//                        Instrument instrument = (Instrument)var2.next();
//                        mergedInstruments.addAll(AbstractCurrencyConverter.getConversionDeps(instrument.getSecondaryJFCurrency(), Instrument.EURUSD.getSecondaryJFCurrency()));
//                    }
//
//                    if (DClient.this.transportClient != null && !mergedInstruments.isEmpty()) {
//                        IInstrumentManager instrumentManager = FeedDataProvider.getDefaultInstance().getInstrumentManager();
//                        instrumentManager.addToFullDepthSubscribed(mergedInstruments);
//                    }
//
//                    if (FeedDataProvider.getDefaultInstance() != null) {
//                        FeedDataProvider.getDefaultInstance().setInstrumentsSubscribed(FeedDataProvider.createInstrumentMapMappedWithSaveInCacheOption(mergedInstruments, true));
//                    }
//
//                    DClient.this.instruments = new HashSet(mergedInstruments);
//                    return null;
//                }
//            });
//        } catch (PrivilegedActionException var3) {
//            throw new RuntimeException("Instruments subscription error", var3.getException());
//        }
   }
//
   public synchronized void unsubscribeInstruments(Set<Instrument> instruments) {
//        try {
//            AccessController.doPrivileged(() -> {
//                Set<Instrument> unsubcribeInstruments = new HashSet(instruments);
//                Set<Instrument> instrumentsUsedByOrdersAndPositions = new HashSet();
//                Map var4 = this.taskManagers;
//                synchronized(this.taskManagers) {
//                    Iterator var5 = this.taskManagers.values().iterator();
//
//                    while(true) {
//                        if (!var5.hasNext()) {
//                            break;
//                        }
//
//                        JForexTaskManager<?, ?, ?> engine = (JForexTaskManager)var5.next();
//                        instrumentsUsedByOrdersAndPositions.addAll(engine.getRequiredInstruments());
//                    }
//                }
//
//                if (this.transportClient != null) {
//                    instrumentsUsedByOrdersAndPositions.addAll(OrdersProvider.getInstance().getOrderInstruments());
//                    if (!unsubcribeInstruments.isEmpty()) {
//                        IInstrumentManager instrumentManager = FeedDataProvider.getDefaultInstance().getInstrumentManager();
//                        Set<Instrument> requiredInstruments = instrumentManager.requiredInstruments(instrumentsUsedByOrdersAndPositions, this.ddsChartsController);
//                        unsubcribeInstruments.removeAll(requiredInstruments);
//                        Set<Instrument> remainingInstruments = instrumentManager.getSubscribedInstruments();
//                        remainingInstruments.removeAll(unsubcribeInstruments);
//                        instrumentManager.unsubscribe(unsubcribeInstruments);
//                        if (FeedDataProvider.getDefaultInstance() != null) {
//                            FeedDataProvider.getDefaultInstance().setInstrumentsSubscribed(FeedDataProvider.createInstrumentMapMappedWithSaveInCacheOption(remainingInstruments, true));
//                        }
//
//                        this.instruments = new HashSet(remainingInstruments);
//                    }
//                }
//
//                return null;
//            });
//        } catch (PrivilegedActionException var3) {
//            throw new RuntimeException("Error while unsubscribing from instruments", var3.getException());
//        }
   }
//
    public void setSubscribedFinancialInstruments(Set<IFinancialInstrument> financialInstruments) {
//        Set<Instrument> instruments = AvailableInstrumentProvider.INSTANCE.convertToInstrumentSet(financialInstruments);
//        this.setSubscribedInstruments(instruments);
    }
//
//    public synchronized INewsFilter getNewsFilter(INewsFilter.NewsSource newsSource) {
//        return (INewsFilter)this.newsFilters.get(newsSource);
//    }
//
    public synchronized INewsFilter removeNewsFilter(INewsFilter.NewsSource newsSource) {
        if (this.transportClient != null && this.transportClient.isOnline()) {
//            NewsSubscribeRequest newsUnsubscribeRequest = new NewsSubscribeRequest();
//            newsUnsubscribeRequest.setRequestType(SubscribeRequestType.UNSUBSCRIBE);
//            newsUnsubscribeRequest.setSources(Collections.singleton(com.dukascopy.dds3.transport.msg.news.NewsSource.valueOf(newsSource.name())));
//            LOGGER.debug("Unsubscribing : " + newsUnsubscribeRequest);
//            this.transportClient.sendMessageNaive(newsUnsubscribeRequest);
            return (INewsFilter)this.newsFilters.remove(newsSource);
        } else {
            return null;
        }
    }
//
    public synchronized Set<Instrument> getSubscribedInstruments() {
       return new HashSet(this.instruments);
    }
//
    public synchronized Set<IFinancialInstrument> getSubscribedFinancialInstruments() {
      return null; //AvailableInstrumentProvider.INSTANCE.convertToFinancialInstrumentSet(this.getSubscribedInstruments());
    }
//
   public synchronized void addNewsFilter(INewsFilter newsFilter) {
//        if (this.transportClient != null && this.transportClient.isOnline()) {
//            Iterator var2 = newsFilter.getNewsSources().iterator();
//
//            while(var2.hasNext()) {
//                INewsFilter.NewsSource source = (INewsFilter.NewsSource)var2.next();
//                this.newsFilters.put(source, newsFilter);
//           }
//
//            NewsSubscribeRequest newsSubscribeRequest = new NewsSubscribeRequest();
//            newsSubscribeRequest.setRequestType(SubscribeRequestType.SUBSCRIBE);
//            Set<com.dukascopy.dds3.transport.msg.news.NewsSource> sources = TransportHelper.toTransportSources(newsFilter.getNewsSources());
//            newsSubscribeRequest.setSources(sources);
//            newsSubscribeRequest.setHot(newsFilter.isOnlyHot());
//            newsSubscribeRequest.setGeoRegions(EnumConverter.convert(newsFilter.getCountries(), GeoRegion.class));
//            newsSubscribeRequest.setMarketSectors(EnumConverter.convert(newsFilter.getMarketSectors(), MarketSector.class));
//            newsSubscribeRequest.setIndicies(EnumConverter.convert(newsFilter.getStockIndicies(), StockIndex.class));
//            newsSubscribeRequest.setCurrencies(EnumConverter.convertByName(newsFilter.getCurrencies()));
//            newsSubscribeRequest.setEventCategories(EnumConverter.convert(newsFilter.getEventCategories(), EventCategory.class));
//            newsSubscribeRequest.setKeywords(newsFilter.getKeywords());
//            newsSubscribeRequest.setFrom(newsFilter.getFrom() == null ? -9223372036854775808L : newsFilter.getFrom().getTime());
//            newsSubscribeRequest.setTo(newsFilter.getTo() == null ? -9223372036854775808L : newsFilter.getTo().getTime());
//            newsSubscribeRequest.setCalendarType((CalendarType)EnumConverter.convert(newsFilter.getType(), CalendarType.class));
//            LOGGER.debug("Subscribing : " + newsSubscribeRequest);
//            this.transportClient.sendMessageNaive(newsSubscribeRequest);
//        }
//
    }
//
    public synchronized void setOut(PrintStream out) {
//        this.out = out;
//        NotificationUtilsProvider.setNotificationUtils(new PrintStreamNotificationUtils(out, this.err));
    }
//
    public synchronized void setErr(PrintStream err) {
//        this.err = err;
//        NotificationUtilsProvider.setNotificationUtils(new PrintStreamNotificationUtils(this.out, err));
    }
//
    public void setCacheDirectory(File cacheDirectory) {
//        if (!cacheDirectory.exists()) {
//            LOGGER.warn("Cache directory [" + cacheDirectory + "] doesn't exist, trying to create");
//            if (!cacheDirectory.mkdirs()) {
//                LOGGER.error("Cannot create cache directory [" + cacheDirectory + "], default cache directory will be used");
//                return;
//            }
//
//            try {
//                CacheManager.createVersionFile(cacheDirectory);
//            } catch (DataCacheException var3) {
//                LOGGER.error("Cannot create cache directory [" + cacheDirectory + "], default cache directory will be used. Reason [" + var3.getLocalizedMessage() + "]", var3);
//                return;
//            }
//        }
//
//        FilePathManager.getInstance().setCacheFolderPath(cacheDirectory.getAbsolutePath());
    }
//
    public void compileStrategy(String srcJavaFile, boolean obfuscate) {
//        JFXCompiler.getInstance().compile(new File(srcJavaFile), this.console, obfuscate);
    }
//
    public File packPluginToJfx(File file) {
       return this.packToJfx(file);
    }
//
    public File packToJfx(File file) {
//        try {
//            return JFXCompiler.getInstance().packJarToJfx(file);
//        } catch (Exception var3) {
//            LOGGER.error(var3.getMessage(), var3);
//            return null;
//        }

        return null;
   }
//
    public String getVersion() {
        return this.version;
    }
//
   public IChart openChart(IFeedDescriptor feedDescriptor) {
        return null; //this.clientChartsController.openChart(feedDescriptor);
    }
//
    public IChart openChart(IFeedInfo feedInfo) {
        IFeedDescriptor feedDescriptor = null; //AvailableInstrumentProvider.INSTANCE.convertToFeedDescriptor(feedInfo);
        return this.openChart(feedDescriptor);
   }
//
    public IChart openChart(File chartTemplate) {
//        return this.clientChartsController == null ? null : this.clientChartsController.openChart(chartTemplate);
        return null;
    }
//
    public void closeChart(IChart chart) {
//        this.clientChartsController.closeChart(chart);
    }
//
    public IClientGUI getClientGUI(IChart chart) {
//        return this.clientChartsController.getClientGUI(chart);
        return null;
    }
//
    public Set<Instrument> getAvailableInstruments() {
//        Set<String> setAsString = (Set)this.serverProperties.get("instruments");
       return null;//Instrument.fromStringSet(setAsString);
    }
//
    public Set<IFinancialInstrument> getAvailableFinancialInstruments() {
       return null; //AvailableInstrumentProvider.INSTANCE.convertToFinancialInstrumentSet(this.getAvailableInstruments());
    }
//
//    public UUID runPlugin(Plugin plugin, IStrategyExceptionHandler exceptionHandler) throws IllegalStateException, NullPointerException {
//        return this.runPlugin(plugin, exceptionHandler, (PluginGuiListener)null);
//    }
//
//    public UUID runPlugin(Plugin plugin, IStrategyExceptionHandler exceptionHandler, PluginGuiListener pluginGuiListener) throws IllegalStateException, NullPointerException {
//        long id = this.startJFRunnable(plugin, PluginTaskManager.Builder.newSdkInstance(pluginGuiListener), (IStrategyListener)null, exceptionHandler, JForexTaskManager.Environment.LOCAL_EMBEDDED, true, this.accountName);
//        UUID uid = UUID.randomUUID();
//        this.pluginIdMap.put(uid, id);
//        return uid;
//    }
//
   public void stopPlugin(UUID processId) {
//        Long id = (Long)this.pluginIdMap.get(processId);
//        if (id == null) {
//            LOGGER.warn("No plugin running with id " + processId);
//        } else {
//            this.stopStrategy(id);
//            this.pluginIdMap.remove(processId);
//        }
   }
//
    public IPreferences getPreferences() {
       return null; //this.ddsChartsController.getPreferences();
   }
//
    public void setPreferences(IPreferences preferences) {
//        this.ddsChartsController.setPreferences(preferences);
//        this.currencyMarketManager.setSkipTicks(preferences.platform().platformSettings().isSkipTicks());
   }
//
    public void init(Set<Instrument> subscribedInstruments) {
     // this.updateSubscribedInstruments(subscribedInstruments);
   }
//
//    public void subscriptionChanged(Map<Instrument, InstrumentSubscriptionResult> resultMap, IInstrumentManager instrumentManager, boolean lock) {
//       this.updateSubscribedInstruments(instrumentManager.getSubscribedInstruments());
//   }
//
   private void updateSubscribedInstruments(Set<Instrument> subscribedInstruments) {
        Set<Instrument> result = new TreeSet(Instrument.COMPARATOR);
        result.addAll(subscribedInstruments);
        this.instruments = result;
    }
//
    private void initServerProperties(Properties properties) {
//        if (properties != null) {
//            this.serverProperties.putAll(properties);
//            PlatformSessionClientBean.getInstance().setStorageServerUrl(properties.getProperty("sss.server.url"));
//            String typesList = this.serverProperties.getProperty("userTypes");
//            if (typesList == null) {
//                LOGGER.warn("Types list was not found");
//                return;
//            }
//
//            List<String> accountTypeList = splitUserTypes(typesList);
//            boolean isContest = accountTypeList.contains("ANALITIC_CONTEST");
//            this.serverProperties.put("ANALITIC_CONTEST", isContest);
//            Set<Integer> typesSet = (Set)this.serverProperties.get("additionalUserTypes");
//            if (typesSet == null) {
//                LOGGER.warn("Layout Types list was not found");
//                return;
//            }
//
//            boolean isPlaceBidOfferHidden = typesSet.contains(DClient.LayoutType.PLACE_BID_OFFER_HIDDEN.getId());
//            this.serverProperties.put(DClient.LayoutType.PLACE_BID_OFFER_HIDDEN.toString(), isPlaceBidOfferHidden);
//            boolean isDelivirableXau = typesSet.contains(DClient.LayoutType.DELIVERABLE_XAU.getId());
//            this.serverProperties.put(DClient.LayoutType.DELIVERABLE_XAU.toString(), isDelivirableXau);
//        }
//
    }
//
    private static List<String> splitUserTypes(String input) {
        Pattern p = Pattern.compile("[,\\s]+");
        String[] result = p.split(input);
        List<String> accountTypes = new ArrayList();
        Collections.addAll(accountTypes, result);
        return accountTypes;
    }
//
    private boolean getBooleanProperty(String property) {
        boolean value = false;
        if (this.serverProperties.get(property) != null) {
            value = (Boolean)this.serverProperties.get(property);
        }

        return value;
    }
//
    public IRemoteStrategyManager getRemoteStrategyManager() {
        return null; //this.remoteManager;
    }
//
//    public void addJFRunnablesListener(IJFRunnablesListener listener) {
//        Set var2 = this.runnablesListeners;
//        synchronized(this.runnablesListeners) {
//            this.runnablesListeners.add(listener);
//        }
//    }
//
//    public void removeJFRunnablesListener(IJFRunnablesListener listener) {
//        Set var2 = this.runnablesListeners;
//        synchronized(this.runnablesListeners) {
//            this.runnablesListeners.remove(listener);
//        }
//    }
//
    private void connectToHistoryServer(String username, boolean downloadInBackground) {
//        FeedDataProvider.getDefaultInstance().connectToHistoryServer(this.transportClient, username, this.serverProperties.getProperty("history.server.url", (String)null), this.serverProperties.getProperty("encryptionKey", (String)null), downloadInBackground, true);
//        IInstrumentManager instrumentManager = FeedDataProvider.getDefaultInstance().getInstrumentManager();
//        if (!instrumentManager.getInstrumentListeners().contains(this)) {
//            instrumentManager.addInstrumentListener(this);
//        }

    }
//
    static {
        TRANSPORT_PING_TIMEOUT = TimeUnit.SECONDS.toMillis(10L);
    }
//
    private class DefaultSystemListener implements ISystemListener {
        private DefaultSystemListener() {
        }

        public void onStart(long processId) {
            DClient.LOGGER.info("Strategy started: " + processId);
        }

        public void onStop(long processId) {
            DClient.LOGGER.info("Strategy stopped: " + processId);
        }

        public void onConnect() {
            DClient.LOGGER.info("Connected");
        }

        public void onDisconnect() {
            DClient.LOGGER.warn("Disconnected");
        }
    }
//
    private class ClientGUIListener implements IClientGUIListener {
        private volatile List<IClientGUIListener> listeners;

        private ClientGUIListener() {
            this.listeners = Collections.synchronizedList(new ArrayList());
        }

        public void onOpenChart(IClientGUI clientGUI) {
            Iterator var2 = this.listeners.iterator();

            while(var2.hasNext()) {
                IClientGUIListener clientGUIListener = (IClientGUIListener)var2.next();
                clientGUIListener.onOpenChart(clientGUI);
            }

        }

        public void onCloseChart(IChart chart) {
            Iterator var2 = this.listeners.iterator();

            while(var2.hasNext()) {
                IClientGUIListener clientGUIListener = (IClientGUIListener)var2.next();
                clientGUIListener.onCloseChart(chart);
            }

        }

        public void addListener(IClientGUIListener listener) {
            this.listeners.add(listener);
        }

        public void removeListener(IClientGUIListener listener) {
            this.listeners.remove(listener);
        }
    }
//
//    private class LotAmountProvider extends DefaultLotAmountProvider implements ILotAmountProvider {
//        private final BigDecimal MIN_CONTEST_TRADABLE_AMOUNT = BigDecimal.valueOf(0.1D);
//        private final BigDecimal MAX_CONTEST_TRADABLE_AMOUNT = BigDecimal.valueOf(5L);
//        private boolean contest = false;
//
//        public LotAmountProvider() {
//            this.contest = DClient.this.getBooleanProperty("ANALITIC_CONTEST");
//        }
//
//        public BigDecimal getMinTradableAmount(Instrument instrument) {
//            BigDecimal result = super.getMinTradableAmount(instrument);
//            if (instrument != null && (InstrumentUtils.isForex(instrument) || InstrumentUtils.isCrypto(instrument)) && this.contest) {
//                result = this.MIN_CONTEST_TRADABLE_AMOUNT;
//            }
//
//            return result;
//        }
//
//        public BigDecimal getMaxTradableAmount(Instrument instrument) {
//            BigDecimal result = super.getMaxTradableAmount(instrument);
//            if (instrument != null && (InstrumentUtils.isForex(instrument) || InstrumentUtils.isCrypto(instrument)) && this.contest) {
//                result = this.MAX_CONTEST_TRADABLE_AMOUNT;
//            }
//
//            return result;
//        }
//    }
//
//    private static class DefaultStrategyExceptionHandler implements IStrategyExceptionHandler {
//        private static final Logger LOGGER = LoggerFactory.getLogger(DClient.DefaultStrategyExceptionHandler.class);
//        private JForexTaskManager<?, ?, ?> taskManager;
//
//        private DefaultStrategyExceptionHandler() {
//        }
//
//        public void setTaskManager(JForexTaskManager<?, ?, ?> taskManager) {
//            this.taskManager = taskManager;
//        }
//
//        public void onException(long strategyId, Source source, Throwable t) {
//            LOGGER.error("Exception thrown while running " + source + " method: " + t.getMessage(), t);
//            this.taskManager.stopStrategy();
//        }
//    }
//
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
