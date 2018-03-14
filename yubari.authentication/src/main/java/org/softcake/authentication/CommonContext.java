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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CommonContext {
    public static final String IS_DUKASCOPY_PLATFORM = "dukascopy.jforex";
    public static final String WL_NAME = "wl.name";
    public static final String ACCOUNT_NAME = "account_name";
    public static final String LOCAL_IP_ADDRESS = "local_ip_address";
    public static final String JFOREX_PLATFORM = "jforex";
    public static final String JCLIENT_PLATFORM = "java";
    public static final String SNAPSHOT_VERSION = "99.99.99";
    public static final String CLIENT_VERSION;
    public static final String PLATFORM_INSTANCE_ID = "PLATFORM_INSTANCE_ID";
    private static final String IS_JAPAN_WL = "IS_JAPAN_WL";
    private static final String IS_AFT_WL = "IS_AFT_WL";
    private static final String PLATFORM_WEEKEND_MODE = "PLATFORM_WEEKEND_MODE";
    public static final String USER_PROPERTIES_PREFIX = "user.property.";
    private static final Map<String, Object> configurationData = Collections.synchronizedMap(new HashMap());

    public CommonContext() {
    }

    public static boolean isDukascopyPlatform() {
        return System.getProperty("dukascopy.jforex", "true").equals("true");
    }

    public static String getWLName() {
        return System.getProperty("wl.name");
    }

    public static boolean isStrategyAllowed() {
        return true;
    }

    public static void setUserProperty(String propertyName, String propertyValue) {
        configurationData.put("user.property." + propertyName, propertyValue);
    }

    public static String getUserProperty(String propertyName) {
        return (String)configurationData.get("user.property." + propertyName);
    }

    public static void setConfig(String name, Object value) {
        Map var2 = configurationData;
        synchronized(configurationData) {
            configurationData.put(name, value);
        }
    }

    public static Object getConfig(String name) {
        Map var1 = configurationData;
        synchronized(configurationData) {
            return configurationData.get(name);
        }
    }

    public static void setIsJapanWL(boolean isJapanWL) {
        setConfig("IS_JAPAN_WL", isJapanWL);
    }

    public static boolean isJapanWL() {
        Object conf = getConfig("IS_JAPAN_WL");
        return conf != null && conf instanceof Boolean ? (Boolean)Boolean.class.cast(conf) : false;
    }

    public static void setIsAftWL(boolean isAftWL) {
        setConfig("IS_AFT_WL", isAftWL);
    }

    public static boolean isAftWL() {
        Object conf = getConfig("IS_AFT_WL");
        return conf != null && conf instanceof Boolean ? (Boolean)Boolean.class.cast(conf) : false;
    }

    public static void setPlatformWeekendMode(PlatformWeekendMode weekendMode) {
        setConfig("PLATFORM_WEEKEND_MODE", weekendMode);
    }

    public static PlatformWeekendMode getPlatformWeekendMode() {
        Object conf = getConfig("PLATFORM_WEEKEND_MODE");
        return conf != null && conf instanceof PlatformWeekendMode ? (PlatformWeekendMode)conf : null;
    }

    public static Map<String, Object> getConfiguration() {
        return Collections.unmodifiableMap(configurationData);
    }

    public static String getUserAgent() {
        return getUserAgent(isStrategyAllowed() ? "jforex" : "java");
    }

    public static String getUserAgent(String clientType) {
        TransportUserAgent userAgent = new TransportUserAgent();
        userAgent.setClientVersion(CLIENT_VERSION);
        userAgent.setJavaVersion(System.getProperty("java.version"));
        userAgent.setJvmVersion(System.getProperty("java.vm.version"));
        userAgent.setOsName(System.getProperty("os.name"));
        userAgent.setUserName(String.valueOf(getConfig("account_name")));
        userAgent.setIp(String.valueOf(getConfig("local_ip_address")));
        userAgent.setClientType(clientType);
        return userAgent.toJsonString();
    }

    static {
        String version = null;

        try {
            Class dcClientImpl = Thread.currentThread().getContextClassLoader().loadClass("com.dukascopy.dds2.greed.GreedContext");
            version = dcClientImpl.getPackage().getImplementationVersion();
        } catch (ClassNotFoundException var2) {
            ;
        }

        if (version != null && !version.equals("") && !version.endsWith("SNAPSHOT")) {
            CLIENT_VERSION = version;
        } else {
            CLIENT_VERSION = "99.99.99";
        }

    }
}
