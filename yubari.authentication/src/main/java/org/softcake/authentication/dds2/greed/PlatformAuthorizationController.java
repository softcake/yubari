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

import org.softcake.authentication.AuthorizationClient;
import org.softcake.authentication.CaptchaParametersBean;
import org.softcake.authentication.CommonContext;

import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.ILoginDialogController.LoginDialogState;
import com.dukascopy.login.controller.IPlatformAuthorizationController;
import com.dukascopy.login.controller.Language;
import com.dukascopy.login.controller.LoginDialogBean;
import com.dukascopy.login.controller.LoginDialogComponents;
import com.dukascopy.login.controller.PlatformEnvironment;
import com.dukascopy.login.controller.PlatformMode;
import com.dukascopy.login.controller.PlatformThemeValue;
import com.dukascopy.login.controller.PlatformZoomValue;
import com.dukascopy.login.controller.WikiParametersBean;
import com.dukascopy.login.service.account.DeviceIDBean;
import com.dukascopy.login.service.account.UserBean;
import com.dukascopy.login.service.account.storage.api.IAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;

public class PlatformAuthorizationController implements IPlatformAuthorizationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformAuthorizationController.class);
    private volatile boolean canceled = false;
    private volatile boolean canceling = false;
    private volatile ILoginDialogController loginDialogController;

    public PlatformAuthorizationController() {
    }

    public void cancel() {
        this.canceling = true;
        this.loginDialogController.addInfoMessage("authorization.cancel.progress");
        this.loginDialogController.setFocus(LoginDialogComponents.LOGIN_FIELD);
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    public boolean isCanceling() {
        return this.canceling;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public void setCanceling(boolean canceling) {
        this.canceling = canceling;
    }




    public void connect() {
        boolean dialogWithTwoViews = CompatibilityUtil.isDialogWithTwoViews(ILoginDialogController.class);
       // PlatformInitUtils.initStaticValues();
        LoginDialogBean loginDialogBean = this.loginDialogController.getLoginDialogBean();
        this.applyUISettings(loginDialogBean);
        this.setPlatformLanguage(true);
        this.loginDialogController.saveSettings(loginDialogBean);
        String loginName = loginDialogBean.getLoginName();
        String password = loginDialogBean.getPassword();
        boolean pinSelected = loginDialogBean.isPinSelected();
        String secureCode = loginDialogBean.getSecureCode();
        String captchaId = loginDialogBean.getCaptchaId();
        PlatformMode platformMode = loginDialogBean.getPlatformMode();
        Pair<Boolean, String> result = this.isRememberMeAuthorization(loginDialogBean);
        boolean authByRememberMeToken = (Boolean)result.getFirst();
        String rememberMeToken = (String)result.getSecond();
        if (!authByRememberMeToken) {
            if (loginName == null || password == null || loginName.isEmpty() || password.isEmpty()) {
                this.loginDialogController.setLoginDialogState(LoginDialogState.AUTHORIZATION_READY);
                return;
            }
        } else if (loginName == null || loginName.isEmpty()) {
            this.loginDialogController.setLoginDialogState(LoginDialogState.AUTHORIZATION_READY);
            return;
        }

      /*  if (!Boolean.getBoolean("development.mode")) {
            AccountInfoMessageWaitingHelper accountInfoMessageWaitingHelper = AccountInfoMessageWaitingHelper.getInstance();
            accountInfoMessageWaitingHelper.setAccAbsenceProcessingMode(AccountInfoMessageAbsenceProcessingMode.THROW_TO_LOGIN_DIALOG);
        }*/

        CommonContext.setConfig("account_name", loginName);
        CommonContext.setConfig(" ", password);
        if (!dialogWithTwoViews && pinSelected && secureCode != null && !secureCode.isEmpty()) {
            //GreedContext.setPinEntered(true);
        }

        Boolean rememberMe = null;
        DeviceIDBean deviceIDBean = null;

        try {
            String platformEnvironment =System.getProperty("jnlp.client.mode");
            boolean rememberMeSupported = true;// GreedContext.isRememberMeSupported(platformEnvironment);
            if (rememberMeSupported) {

                IAccountService accountService = loginDialogController.getAccountService();
                rememberMe = loginDialogBean.isRememberMe();
                deviceIDBean = accountService.getDeviceIDBean();
                if (!rememberMe) {
                    accountService.deleteRememberMeToken(platformEnvironment, loginName);
                }
            }
        } catch (Throwable var18) {
            LOGGER.error(var18.getMessage(), var18);
        }
        loginDialogBean.setPinSelected(true);
        this.loginDialogController.setLoginDialogState(LoginDialogState.AUTHORIZATION_CANCEL_DISABLE);
       // this.loginDialogController.closeLoginDialog();
      ConnectAction connectAction = new ConnectAction(this.loginDialogController, captchaId, secureCode, rememberMe, deviceIDBean, rememberMeToken);
      connectAction.doAction();
       /*  GreedContext.publishEvent(connectAction);*/
    }

    public Map<String, BufferedImage> getCaptcha() {
        return this.getCaptcha(false);
    }

    public String getStandalonePlatformJNLPUrl() {
        return ""; //GreedContext.getStandalonePlatformJNLPUrl();
    }

    public void setStandalonePlatformJNLPUrl(String platformJnlpURL) {
       // GreedContext.setStandalonePlatformJNLPUrl(platformJnlpURL);
    }

    public Map<String, BufferedImage> getCaptcha(boolean uiParameters) {
        Map<String, BufferedImage> captcha = null;
        CaptchaParametersBean captchaParametersBean = null;
        if (uiParameters) {
            try {
                LoginDialogBean loginDialogBean = this.loginDialogController.getLoginDialogBean();
                PlatformZoomValue platformZoomValue = loginDialogBean.getPlatformZoomValue();
                PlatformThemeValue platformThemeValue = loginDialogBean.getPlatformThemeValue();
                double multiplierValue = platformZoomValue.getMultiplierValue();
                int width = (int)(228.0D * multiplierValue);
                switch(platformThemeValue) {
                case DARK_THEME:
                    captchaParametersBean = CaptchaParametersBean.createForDarkTheme(width);
                    break;
                case DEFAULT_THEME:
                    captchaParametersBean = CaptchaParametersBean.createForLightTheme(width);
                    break;
                default:
                    captchaParametersBean = CaptchaParametersBean.createForLightTheme(width);
                }
            } catch (Throwable var11) {
                LOGGER.error(var11.getMessage(), var11);
            }
        }

        try {
            AuthorizationClient authorizationClient = AuthorizationClient.getInstance();
            captcha = authorizationClient.getImageCaptcha(captchaParametersBean);
        } catch (Throwable var10) {
            LOGGER.error(var10.getMessage(), var10);
        }

        return captcha;
    }

    public void setLoginDialogController(ILoginDialogController loginDialogController) {
        this.loginDialogController = loginDialogController;
       // GreedContext.setLoginDialogController(this.loginDialogController);
        this.logPlatformLaunchType();
        this.logStandalonePlatformVersion();
    }

    public PlatformEnvironment getPlatformEnvironment() {
        return PlatformEnvironment.fromValue("jnlp.client.mode");
    }

    public void initialize() {
        this.setPlatformUISettings();
        this.setPlatformLanguage(true);
    }

    private void setPlatformUISettings() {
        try {
            LoginDialogBean loginDialogBean = this.loginDialogController.getLoginDialogBean();
            this.applyUISettings(loginDialogBean);
        } catch (Throwable var2) {
            LOGGER.error(var2.getMessage(), var2);
        }

    }

    private void setPlatformLanguage(boolean saveSettings) {
        try {
            LoginDialogBean loginDialogBean = this.loginDialogController.getLoginDialogBean();
            Language newPlatformLanguage = loginDialogBean.getPlatformLanguage();
            Locale newLocale = newPlatformLanguage.getLocale();
//            Locale selectedlocale = LocalizationManager.getLanguage().locale;
          /*  if (!ObjectUtils.isEqual(selectedlocale, newLocale)) {
                com.dukascopy.ui.l10n.LocalizationManager.Language[] var6 = com.dukascopy.ui.l10n.LocalizationManager.Language.values();
                int var7 = var6.length;

                for(int var8 = 0; var8 < var7; ++var8) {
                    com.dukascopy.ui.l10n.LocalizationManager.Language language = var6[var8];
                    if (language.locale.equals(newLocale)) {
                        LocalizationManager.changeLanguage(language);
                        if (saveSettings) {
                            SwingUtilities.invokeLater(new Runnable() {
                                public void run() {
                                    ClientSettingsStorageImpl.saveSystemSettings();
                                }
                            });
                        }
                        break;
                    }
                }
            }*/
        } catch (Throwable var10) {
            LOGGER.error("Cannot set the platform language.");
            LOGGER.error(var10.getMessage(), var10);
        }

    }
/*

    private boolean isJForexModeOnStart() {
        String platformMode = System.getProperty("jnlp.platform.mode");
        return GreedContext.IS_AFT_FX_LABEL || platformMode != null && !"jclient".equals(platformMode);
    }
*/

    private void applyUISettings(LoginDialogBean loginDialogBean) {
        try {
          /*  PlatformZoomValue platformZoomValue = loginDialogBean.getPlatformZoomValue();
            PlatformThemeValue platformThemeValue = loginDialogBean.getPlatformThemeValue();
            IAppearanceThemeManager appearanceThemeManager = UICoreServiceProvider.getAppearanceThemeManager();
            AppearanceTheme currentTheme = appearanceThemeManager.getCurrentTheme();
            ZoomMode currentZoomMode = appearanceThemeManager.getCurrentZoomMode();
            AppearanceTheme newTheme = UISettingsConverter.convert(platformThemeValue);
            if (newTheme != currentTheme) {
                appearanceThemeManager.changeAppearanceTheme(newTheme, (String)null);
            }

            ZoomMode newZoomMode = UISettingsConverter.convert(platformZoomValue);
            if (newZoomMode != currentZoomMode) {
                appearanceThemeManager.changeZoomMode(newZoomMode);
            }*/
        } catch (Throwable var9) {
            LOGGER.error(var9.getMessage(), var9);
        }

    }

    private Pair<Boolean, String> isRememberMeAuthorization(LoginDialogBean loginDialogBean) {
        Boolean authByRememberMeToken = false;
        String rememberMeToken = null;

        try {
            String platformEnvironmentAsString = System.getProperty("jnlp.client.mode");
            boolean rememberMeSupported = true; //GreedContext.isRememberMeSupported(platformEnvironmentAsString);
            if (rememberMeSupported) {

                IAccountService accountService = loginDialogController.getAccountService();
                String loginName = loginDialogBean.getLoginName();
                if (loginName != null && !loginName.isEmpty()) {
                    PlatformEnvironment platformEnvironment = loginDialogBean.getPlatformEnvironment();
                    UserBean userBean = accountService.getUserBean(loginName, platformEnvironment);
                    if (userBean != null && userBean.isAutoLoginAsRememberMePossible()) {
                        authByRememberMeToken = true;
                        rememberMeToken = userBean.getRememberMeToken();
                    }
                }
            }
        } catch (Throwable var11) {
            LOGGER.error(var11.getMessage(), var11);
        }

        Pair<Boolean, String> pair = new Pair(authByRememberMeToken, rememberMeToken);
        return pair;
    }

    private void logPlatformLaunchType() {
        try {
          /*  PlatformLaunchType platformLaunchType = PlatformLaunchTypeDetector.getPlatformLaunchType(this.loginDialogController);
            String launchTypeAsString = platformLaunchType.getName();
            CommonContext.setConfig("PLATFORM_LAUNCH_TYPE", launchTypeAsString);
            AlsMessageBean alsInstallMessageBean = AlsMessageFactory.createPlatformInstallationTypeAlsMessageBean(launchTypeAsString);
            LOGGER.info(alsInstallMessageBean.getMessage());
            PlatformServiceProvider.getInstance().setAlsPlatformInstallTypeMessageBean(alsInstallMessageBean);*/
        } catch (Exception var4) {
            LOGGER.error("Cannot log the platform launch type", var4);
            LOGGER.error(var4.getMessage(), var4);
        }

    }

    private void logStandalonePlatformVersion() {
        try {
           /* if (!GreedContext.isStandaloneInstallation()) {
                return;
            }

            String standAloneVersion = this.loginDialogController.getStandAloneVersion();
            PlatformServiceProvider platformServiceProvider = PlatformServiceProvider.getInstance();
            platformServiceProvider.setStandAloneVersion(standAloneVersion);*/
        } catch (Exception var3) {
            LOGGER.error("Cannot log the standalone platform version", var3);
            LOGGER.error(var3.getMessage(), var3);
        }

    }

    private void logStandalonePlatformVersionAsSeparateKey() {
        try {
          /*  if (!GreedContext.isStandaloneInstallation()) {
                return;
            }

            String standAloneVersion = this.loginDialogController.getStandAloneVersion();
            AlsMessageBean standaloneVersionMessageBean = AlsMessageFactory.createStandalonePlatformVersionAlsMessageBean(standAloneVersion);
            LOGGER.info(standaloneVersionMessageBean.getMessage());
            PlatformServiceProvider.getInstance().setAlsStandaloneVersionMessageBean(standaloneVersionMessageBean);*/
        } catch (Exception var3) {
            LOGGER.error("Cannot log the standalone platform version", var3);
            LOGGER.error(var3.getMessage(), var3);
        }

    }

    public void showWiki(WikiParametersBean wikiParametersBean) {
        try {
            Locale locale = wikiParametersBean.getLocale();
          /*  if (locale != null) {
                com.dukascopy.ui.l10n.LocalizationManager.Language[] var3 = com.dukascopy.ui.l10n.LocalizationManager.Language.values();
                int var4 = var3.length;

                for(int var5 = 0; var5 < var4; ++var5) {
                    com.dukascopy.ui.l10n.LocalizationManager.Language language = var3[var5];
                    if (language.locale.equals(locale)) {
                        LocalizationManager.changeLanguage(language);
                        break;
                    }
                }
            }*/
        } catch (Exception var12) {
            LOGGER.error(var12.getMessage(), var12);
        }

        try {
          /*  float ratio = wikiParametersBean.getRatio();
            ZoomMode zoomMode = ZoomMode.fromValue(ratio);
            UICoreServiceProvider.getAppearanceThemeManager().changeZoomMode(zoomMode);*/
        } catch (Exception var11) {
            LOGGER.error(var11.getMessage(), var11);
        }

        try {
            String baseUrl = wikiParametersBean.getBaseUrl();
            String partOfUrl = wikiParametersBean.getPartOfUrl();
            Window window = wikiParametersBean.getWindow();
            Locale locale = wikiParametersBean.getLocale();
            float ratio = wikiParametersBean.getRatio();
          /*  PlatformServiceProvider platformServiceProvider = PlatformServiceProvider.getInstance();
            IWikiDocumentsService wikiDocumentsService = platformServiceProvider.getWikiDocumentsService();
            WikiDialogParameters wikiDialogParameters = new WikiDialogParameters();
            wikiDialogParameters.withModal(true).withBaseUrl(baseUrl).withUrl(partOfUrl).withLanguage(locale).withOwner(window).withRelativeTo(window).withRatio(ratio);
            wikiDocumentsService.showWikiDialog(wikiDialogParameters);*/
        } catch (Exception var10) {
            LOGGER.error(var10.getMessage(), var10);
        }

    }
}
