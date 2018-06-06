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

package org.softcake.authentication.dds2.greed;

import org.softcake.authentication.CommonContext;


import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.IPlatformEnvironmentListener;
import com.dukascopy.login.controller.Language;
import com.dukascopy.login.controller.LoginDialogBean;
import com.dukascopy.login.controller.PlatformEnvironment;
import com.dukascopy.login.controller.PlatformEnvironmentParametersBean;
import com.dukascopy.login.resources.IResourcesProvider;
import com.dukascopy.login.resources.ResourcesProviderFactory;
import com.dukascopy.login.service.AppearanceThemeManager;
import com.dukascopy.login.service.AppearanceThemesSettingsStorage;
import com.dukascopy.login.service.UICoreServiceProvider;
import com.dukascopy.login.service.account.UserBean;
import com.dukascopy.login.service.account.storage.api.IAccountService;
import com.dukascopy.login.settings.CurrentLoginFormSettins;
import com.dukascopy.login.settings.DefaultLoginFormSettins;
import com.dukascopy.login.utils.JNLPObject;
import com.dukascopy.login.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;

public class LoginDialogFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginDialogFactory.class);

    public LoginDialogFactory() {
    }

    public static ILoginDialogController create(Properties jforexProperties) {
        updateSystemPropertiesWithJNLPProperies();
        IResourcesProvider webStartResourcesProvider = null;
        LoginDialogBean loginDialogBean = createAndGetLoginDialogBean();

        try {
            webStartResourcesProvider = ResourcesProviderFactory.createWebStartResourcesProvider();
        } catch (Throwable var19) {
            LOGGER.error(var19.getMessage(), var19);
        }

        try {
            UICoreServiceProvider.initAppearanceThemeManager(new AppearanceThemeManager());
            AppearanceThemesSettingsStorage appearanceSettingsStorage = new AppearanceThemesSettingsStorage();
            appearanceSettingsStorage.setLoginDialogBean(loginDialogBean);
            UICoreServiceProvider.getAppearanceThemeManager().loadInitialLookAndFeel(appearanceSettingsStorage);
        } catch (Exception var18) {
            LOGGER.error(var18.getMessage(), var18);
        }

       // ILoginDialogController loginDialogController = com.dukascopy.login.ui.LoginDialogFactory.createWebStartVersion(webStartResourcesProvider, loginDialogBean);
        ILoginDialogController loginDialogController = com.dukascopy.login.ui.LoginDialogFactory.createStandAloneInstallationVersion(webStartResourcesProvider, loginDialogBean);


        loginDialogController.setOkButtonActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                LOGGER.info("Action");
            }
        });
      //  ILoginDialogController loginDialogController = com.dukascopy.login.ui.LoginDialogFactory.createStandAloneInstallationVersion();
        try {
           /* PlatformEnvironment clientModeAsEnum = PlatformParameters.getClientModeAsEnum();
            List<PlatformEnvironment> platformEnvironments = new ArrayList();
            clientModeAsEnum.setWebStartMode(true);
            platformEnvironments.add(clientModeAsEnum);
            loginDialogController.setPlatformEnvironments(platformEnvironments);*/
            if (CompatibilityUtil.isPlatformCompatibleWithLoginDialog("JFOREX_9822")) {
               /* PlatformEnvironmentParametersBean platformEnvironmentParametersBean = new PlatformEnvironmentParametersBean(false);
                loginDialogController.setPlatformEnvironment(clientModeAsEnum, platformEnvironmentParametersBean);*/

                final PlatformEnvironment demo3 = PlatformEnvironment.DEMO;
                final PlatformEnvironment live3 = PlatformEnvironment.LIVE;

                List<PlatformEnvironment> environments = new ArrayList<>();
                environments.add(demo3);
                environments.add(live3);
                loginDialogController.setPlatformEnvironments(environments);

            } else {
               // loginDialogController.setPlatformEnvironment(clientModeAsEnum);
            }
        } catch (Throwable var17) {
            LOGGER.error(var17.getMessage(), var17);
        }

        loginDialogController.setPlatformVersion(CommonContext.CLIENT_VERSION);
        String platformName = PlatformParameters.getPlatformName();
        if (platformName == null || platformName.isEmpty()) {
            platformName = "FX Marketplace";
        }

        loginDialogController.setTitle(platformName);
        LoginActionListener loginActionListener = new LoginActionListener(loginDialogController);
        LoginFormCloseListener loginFormCloseListener = new LoginFormCloseListener(loginDialogController);
        CancelAuthorizationListener cancelAuthorizationListener = new CancelAuthorizationListener(loginDialogController);
        loginDialogController.setPlatformAuthorizationActionListener(loginActionListener);
        loginDialogController.setDialogWindowListener(loginFormCloseListener);
        loginDialogController.setPlatformAuthorizationCancelListener(cancelAuthorizationListener);

        loginDialogController.addPlatformEnvironmentListener(new IPlatformEnvironmentListener() {
            @Override
            public void newEnvironmentSelected(final PlatformEnvironment platformEnvironment,
                                               final PlatformEnvironment platformEnvironment1) {
                LOGGER.info("Listener {} {}", platformEnvironment, platformEnvironment1);
            }
        });
        List<Language> platformLanguages = loginDialogBean.getPlatformLanguages();
        loginDialogController.setPlatformLanguages(platformLanguages);
        boolean rememberMeSupported = false;
        PlatformEnvironment platformEnvironment = loginDialogBean.getPlatformEnvironment();

        try {
            rememberMeSupported = loginDialogController.isRememberMeSupported(platformEnvironment.getEnvironment());
        } catch (Throwable var16) {
            LOGGER.error(var16.getMessage(), var16);
        }

        try {
            loginDialogController.setVisibleRememberMeRelatedComponents(rememberMeSupported);
            if (rememberMeSupported) {
                boolean autoLoginByUsingPlatformParameters = PlatformParameters.isAutoLoginPossibleByPasswordAndUserName(loginDialogBean);
                if (!autoLoginByUsingPlatformParameters) {
                    IAccountService accountService = loginDialogController.getAccountService();
                    UserBean userBean = accountService.getLastLoggedInUserAsRememberMe(platformEnvironment);
                    if (userBean != null) {
                        String userName = userBean.getUserName();
                        loginDialogBean.setLoginName(userName);
                        loginDialogBean.setFictivePassword(userBean.getFictivePassword());
                        loginDialogBean.setRememberMe(true);
                        loginDialogController.setLoginDialogBean(loginDialogBean);
                    }
                }
            }
        } catch (Throwable var15) {
            LOGGER.error(var15.getMessage(), var15);
        }

        return loginDialogController;
    }

    private static LoginDialogBean createAndGetLoginDialogBean() {
        LoginDialogBean loginDialogBean = null;

        try {
            CurrentLoginFormSettins currentLoginFormSettins = new CurrentLoginFormSettins();
            currentLoginFormSettins.loadProperties();
            loginDialogBean = currentLoginFormSettins.getLoginDialogBean();
        } catch (Throwable var9) {
            LOGGER.error("Cannot fill the loginDialogBean by by the local settings values.");
            LOGGER.error(var9.getMessage(), var9);
        }

        boolean dialogWithTwoViews = CompatibilityUtil.isDialogWithTwoViews(ILoginDialogController.class);
        boolean pinValueShouldBeChanged = false;
        if (loginDialogBean != null && !dialogWithTwoViews) {
            try {
                PlatformEnvironment platformEnvironment = loginDialogBean.getPlatformEnvironment();
                if (platformEnvironment == PlatformEnvironment.DEMO_3) {
                    platformEnvironment = PlatformEnvironment.DEMO;
                } else if (platformEnvironment == PlatformEnvironment.LIVE_3) {
                    platformEnvironment = PlatformEnvironment.LIVE;
                }

                if (platformEnvironment != PlatformParameters.getClientModeAsEnum()) {
                    if (PlatformParameters.getClientModeAsEnum() == PlatformEnvironment.LIVE) {
                        if (!loginDialogBean.isPinSelected()) {
                            pinValueShouldBeChanged = true;
                        }
                    } else if (loginDialogBean.isPinSelected()) {
                        pinValueShouldBeChanged = true;
                    }
                }
            } catch (Throwable var8) {
                LOGGER.error(var8.getMessage(), var8);
            }
        }

        try {
            PlatformParameters.fill(loginDialogBean);
        } catch (Throwable var7) {
            LOGGER.error("Cannot fill the loginDialogBean by the platform parameters.");
            LOGGER.error(var7.getMessage(), var7);
        }

        try {
            if (loginDialogBean == null) {
                DefaultLoginFormSettins defaultLoginFormSettins = new DefaultLoginFormSettins();
                loginDialogBean = defaultLoginFormSettins.getLoginDialogBean();
            }
        } catch (Throwable var6) {
            LOGGER.error(var6.getMessage(), var6);
        }

        List<Language> platformLanguages = new ArrayList();
        platformLanguages.addAll(Arrays.asList(Language.values()));
        loginDialogBean.setPlatformLanguages(platformLanguages);
        loginDialogBean.setPlatformVersion(CommonContext.CLIENT_VERSION);
        PlatformEnvironment platformEnvironment = loginDialogBean.getPlatformEnvironment();
        if (platformEnvironment != null) {
            platformEnvironment.setPlatformVersion(CommonContext.CLIENT_VERSION);
        }

        if (!dialogWithTwoViews) {
            try {
                if (PlatformEnvironment.LIVE != loginDialogBean.getPlatformEnvironment() && loginDialogBean.isPinSelected() && loginDialogBean.isPinSelectedHasDefaultValue()) {
                    loginDialogBean.setPinSelected(false);
                } else if (pinValueShouldBeChanged) {
                    loginDialogBean.setPinSelected(!loginDialogBean.isPinSelected());
                }
            } catch (Throwable var10) {
                LOGGER.error(var10.getMessage(), var10);
            }
        }

        return loginDialogBean;
    }

    private static String extractJNLPFileName(String jnlpHrefProperty) {
        String jnlpName = jnlpHrefProperty;
        if (jnlpHrefProperty != null) {
            jnlpName = jnlpHrefProperty.trim();
        }

        if (jnlpName != null && !jnlpName.isEmpty()) {
            int lastIndex = jnlpName.lastIndexOf("/");
            if (lastIndex != -1) {
                jnlpName = jnlpName.substring(lastIndex + 1, jnlpName.length());
            }

            return jnlpName;
        } else {
            throw new IllegalArgumentException("The jnlp.href is null or empty.");
        }
    }

    private static boolean isWebstart() {
        boolean webstart = false;

        try {
            BasicService basicService = (BasicService)ServiceManager.lookup("javax.jnlp.BasicService");
            webstart = true;
        } catch (Throwable var2) {
            ;
        }

        return webstart;
    }

    private static void updateSystemPropertiesWithJNLPProperies() {
        try {
            if (isWebstart()) {
                BasicService basicService = (BasicService)ServiceManager.lookup("javax.jnlp.BasicService");
                URL codeBaseUrl = basicService.getCodeBase();
                String codeBase = codeBaseUrl.toString();
                String jnlpHrefProperty = System.getProperty("jnlp.href");
                String jnlpFileName = extractJNLPFileName(jnlpHrefProperty);
                if (!codeBase.endsWith("/")) {
                    codeBase = codeBase + "/";
                }

                String jnlpUrl = codeBase + jnlpFileName;
                JNLPObject jnlpObject = new JNLPObject(jnlpUrl);
                boolean fail = jnlpObject.downloadAndParse(3);
                if (fail) {
                    LOGGER.error("Cannot parse JNLP");
                } else {
                    jnlpObject.setSystemProperty("jnlp.company.logo.url");
                    jnlpObject.setSystemProperty("jnlp.platform.logo.url");

                    try {
                        Properties properties = System.getProperties();
                        String property1 = properties.getProperty("jnlp.srp6.login.url");
                        String property2 = properties.getProperty("jnlp.login.url");
                        if (ObjectUtils.isNullOrEmpty(property1) && ObjectUtils.isNullOrEmpty(property2)) {
                            boolean updated1 = jnlpObject.setSystemProperty("jnlp.srp6.login.url");
                            boolean updated2 = jnlpObject.setSystemProperty("jnlp.login.url");
                          /*  if (updated1 || updated2) {
                                GreedContext.LOGIN_URLS.clear();
                                List<String> authorizationUrls = SRP6AuthorizationMigrationHelper.getAuthorizationUrls(GreedContext.CLIENT_MODE);
                                GreedContext.LOGIN_URLS.addAll(authorizationUrls);
                                AuthorizationClient.getInstance().updateConfigurationPool(GreedContext.LOGIN_URLS);
                            }*/
                        }
                    } catch (Exception var14) {
                        LOGGER.error(var14.getMessage(), var14);
                    }
                }
            }
        } catch (Exception var15) {
            LOGGER.error(var15.getMessage(), var15);
        }

    }
}
