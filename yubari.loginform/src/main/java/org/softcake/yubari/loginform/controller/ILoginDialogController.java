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

package org.softcake.yubari.loginform.controller;

import org.softcake.yubari.loginform.resources.IResourcesProvider;
import org.softcake.yubari.loginform.service.account.storage.api.IAccountService;
import org.softcake.yubari.loginform.utils.JNLPObject;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.List;

public interface ILoginDialogController {
    String USE_NEW_LOGIN_DIALOG = "jnlp.useNewLoginDialog";
    String DEMO_3 = "DEMO_3";
    String LIVE_3 = "LIVE_3";
    String version = "new_captcha_version";
    String JFOREX_9822 = "JFOREX_9822";
    String JFOREX_9868 = "JFOREX_9868";

    void setLoginDialogMode(LoginDialogMode var1);

    LoginDialogMode getLoginDialogMode();

    boolean isStandAloneDialogMode();

    void setTitle(String var1);

    PopupMessageResponse showModalPopupMessage(String var1, ILoginDialogController.ModalMessageMode var2);

    PopupMessageResponse showModalPopupMessage(String var1, ILoginDialogController.ModalMessageMode var2, Insets var3);

    void setCancelMode(boolean var1);

    boolean captchaHaveReloadAndCancelButtons();

    void setCaptchaHaveReloadAndCancelButtons(boolean var1);

    LoginDialogBean loadAndGetSettings();

    void saveSettings();

    void saveSettings(LoginDialogBean var1);

    boolean isFirstPlatformUpdateOccured();

    void setFirstPlatformUpdateOccured(boolean var1);

    void setLoginDialogState(ILoginDialogController.LoginDialogState var1);

    void setVisible(boolean var1);

    void closeLoginDialog();

    void setEnabled(boolean var1, LoginDialogComponents... var2);

    void setOkButtonCaption(String var1);

    void setCancelButtonCaption(String var1);

    void setExitButtonCaption(String var1);

    void setFocus(LoginDialogComponents var1);

    void setFocus(LoginDialogComponents var1, boolean var2);

    void setFocusOnFirstEmptyField(boolean var1);

    void setCursor(Cursor var1);

    void setDefaultCursor();

    void setVisibleRememberMeRelatedComponents(boolean var1);

    void showAdvancedSettinsDialog();

    void setEnabledAdvancedSettings(boolean var1);

    boolean isProxySettingsExist();

    void saveProxySettings(Integer var1, String var2, boolean var3);

    void deleteProxySettings();

    boolean isSystemProxyDetecting();

    void activateSystemProxyDetecting(boolean var1);

    void setOkButtonActionListener(ActionListener var1);

    void setCancelButtonActionListener(ActionListener var1);

    void setExitButtonActionListener(ActionListener var1);

    void setPlatformUpdateActionListener(ActionListener var1);

    void setDialogWindowListener(WindowAdapter var1);

    void setCaptchaLifeCycleListener(ICaptchaLifeCycleListener var1);

    void setOpenWikiDialogListener(IOpenWikiDialogListener var1);

    IOpenWikiDialogListener getOpenWikiDialogListener();

    void setPlatformAuthorizationActionListener(ActionListener var1);

    void setPlatformUpdateCancelActionListener(ActionListener var1);

    void setPlatformAuthorizationCancelListener(ActionListener var1);

    void setPlatformAuthorizationController(IPlatformAuthorizationController var1);

    IPlatformAuthorizationController getPlatformAuthorizationController();

    void setLoginName(String var1);

    void setPassword(String var1);

    void setPlatformVersion(String var1);

    void setPlatformMode(PlatformMode var1);

    void setRememberMe(boolean var1);

    void setLoginDialogBean(LoginDialogBean var1);

    void setLoginDialogBean(LoginDialogBean var1, boolean var2);

    void setPlatformEnvironment(PlatformEnvironment var1);

    void setPlatformEnvironment(PlatformEnvironment var1, PlatformEnvironmentParametersBean var2);

    LoginDialogBean getLoginDialogBean();

    LoginDialogBean getLoginDialogBean(boolean var1);

    void setPlatformEnvironments(List<PlatformEnvironment> var1);

    void setPlatformLanguages(List<Language> var1);

    List<Language> getPlatformLanguages();

    void setPlatformLanguage(Language var1);

    boolean isPinSelected();

    void setPlatformVersions(Map<PlatformEnvironment, String> var1);

    void setPlatformExecuteCommandBean(PlatformExecuteCommandBean var1);

    PlatformExecuteCommandBean getPlatformExecuteCommandBean();

    Window getWindow();

    void setResourcesProvider(IResourcesProvider var1);

    IResourcesProvider getResourcesProvider();

    void setPlatformName(String var1);

    String getPlatformName();

    String getPlatformName(PlatformEnvironment var1);

    String getProperty(PlatformEnvironment var1, String var2);

    void setJnlpObjects(Map<PlatformEnvironment, JNLPObject> var1);

    void saveAutologinIgnoreFlag(boolean var1);

    String getStandAloneVersion();

    void setStandAloneVersion(String var1);

    void addMessage(String var1);

    void addInfoMessage(String var1);

    void addErrorMessage(String var1);

    void addInfoMessage(String var1, Object[] var2);

    void addErrorMessage(String var1, Object[] var2);

    void clearMessages();

    void captchaObtainingBefore();

    void captchaObtainingAfter();

    void captchaObtainingFailed();

    void obtainCaptcha();

    void obtainCaptcha(ICaptchaListener var1);

    void setCaptcha(Map<String, BufferedImage> var1);

    void hideCaptcha();

    void setPinCheckBoxSelected(boolean var1);

    void restoreCaptchaProgressBar();

    void setProgressBarCaption(String var1);

    void setProgressBarValue(int var1);

    int getProgressBarValue();

    void setProgressBarMaximumValue(int var1);

    int getProgressBarMaximumValue();

    void setProgressBarIndeterminate(boolean var1);

    boolean isProgressBarStringPainted();

    void setProgressBarStringPainted(boolean var1);

    void setProgressBarVisible(boolean var1);

    void addPlatformEnvironmentListener(IPlatformEnvironmentListener var1);

    void removePlatformEnvironmentListener(IPlatformEnvironmentListener var1);

    void addPlatformLanguageListener(IPlatformLanguageListener var1);

    void firePlatformLanguageSelected(Language var1);

    boolean isRememberMeSupported(String var1);

    IAccountService getAccountService();

    String getlocalLogDirectory();

    void setLocalLogDirectory(String var1);

    boolean isFictivePasswordInPasswordField();

    void showJF2WarningDialog();

    void handlePlatformBetaVersionChangedEvent();

    void setView(ILoginDialogController.LoginDialogView var1);

    ILoginDialogController.AuthorizationPhase getAuthorizationPhase();

    void openForgottenPinPasswordPage(ForgottenCredentialsBean var1);

    public static enum ModalMessageMode {
        OK_BUTTON,
        OK_CANCEL_BUTTON;

        private ModalMessageMode() {
        }
    }

    public static enum LoginDialogView {
        USER_PASSWORD_VIEW,
        PIN_VIEW;

        private LoginDialogView() {
        }
    }

    public static enum AuthorizationPhase {
        USER_NAME_PASSWORD,
        PIN;

        private AuthorizationPhase() {
        }
    }

    public static enum LoginDialogState {
        AUTHORIZATION_READY,
        RETRY_UPDATE_READY,
        AUTHORIZATION_CANCEL_ENABLE,
        AUTHORIZATION_CANCEL_DISABLE,
        PLATFORM_UPDATE_CANCEL_ENABLE,
        PLATFORM_UPDATE_CANCEL_DISABLE;

        private LoginDialogState() {
        }
    }
}
