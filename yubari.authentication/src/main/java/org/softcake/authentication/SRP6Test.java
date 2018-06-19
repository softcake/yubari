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


import org.softcake.authentication.dds2.greed.CompatibilityUtil;
import org.softcake.authentication.dds2.greed.LoginDialogFactory;
import org.softcake.authentication.dds2.greed.PlatformAuthUtil;
import org.softcake.authentication.dds2.greed.PlatformAuthorizationController;
import org.softcake.authentication.dds2.greed.PlatformRememberMeAuthBean;
import org.softcake.yubari.connect.authorization.AuthorizationProperties;
import org.softcake.yubari.connect.authorization.AuthorizationPropertiesFactory;

import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.IPlatformAuthorizationController;
import com.dukascopy.login.controller.IPlatformEnvironmentListener;
import com.dukascopy.login.controller.LoginDialogBean;
import com.dukascopy.login.controller.PlatformEnvironment;
import com.dukascopy.login.service.account.DeviceIDBean;
import com.dukascopy.login.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;


/**
 * @author Rene Neubert
 */

public class SRP6Test {
    public static final String JFOREXSDK_PLATFORM = "DDS3_JFOREXSDK";
    public static final String DEFAULT_VERSION = "99.99.99";
    private static final Logger LOGGER = LoggerFactory.getLogger(SRP6Test.class);
    static String url;
    static String urlDemo;
    static String username;
    static String password;
    static String usernameD;
    static String passwordD;
    static String captchaId;
    static AuthorizationClient authClient;
    public static void main(String[] args) throws Exception {

       /* System.setProperty("jnlp.login.url",
                           "https://www-cdn-1.dukascopy.com/authorization-1/demo,https://www-cdn-3.dukascopy"
                           + ".com/authorization-2/demo");
        System.setProperty("jnlp.srp6.login.url",
                           "https://login.dukascopy.com/authorization-1/demo,https://login.dukascopy"
                           + ".com/authorization-2/demo");*/
        /*System.setProperty("jnlp.platform.mode", "jforex");
        System.setProperty("jnlp.client.version", "2.45.47");
      System.setProperty("jnlp.client.mode", "LIVE");
        System.setProperty("sun.java2d.d3d", "false");*/
        System.setProperty("jnlp.platform.logo.url", "images/logo/dukascopy-sw_32x32.png");
        System.setProperty("jnlp.company.logo.url", "images/dukascopy-sw.png");
       /* System.setProperty("jnlp.localize.reg.form.url", "true");
        System.setProperty("jnlp.register.new.demo.url", "https://demo-login.dukascopy.com/fo/register/demo_new");
        System.setProperty("java.net.preferIPv4Stack", "true");*/
        System.setProperty("jnlp.client.username", "");
        System.setProperty("jnlp.client.password", "");
//        System.setProperty("sendThreadDumpsToALS", "true");
        urlDemo =   "http://platform.dukascopy.com/demo/jforex.jnlp";
      //  urlDemo = "https://platform.dukascopy.com/demo_3/jforex_3.jnlp";
        url = "https://platform.dukascopy.com/live_3/jforex_3.jnlp";
        username = System.getenv("DUKA_LIVE_USER");
        password = System.getenv("DUKA_LIVE_PW");
        usernameD = System.getenv("DUKA_DEMO_USER");
        passwordD = System.getenv("DUKA_DEMO_PW");

        boolean useDemo = false;

        AuthorizationProperties properties = AuthorizationPropertiesFactory.getAuthorizationProperties(useDemo?urlDemo:url);


        List<URL> authServerUrls = properties.getLoginSrpSixUrls();

        final IPlatformAuthorizationController platformAuthorizationController = new PlatformAuthorizationController();
        authClient = AuthorizationClient.getInstance(authServerUrls, DEFAULT_VERSION);
        AuthorizationServerResponse serverResponse;

        String sessionID = UUID.randomUUID().toString();

        System.setProperty("SESSION_ID",sessionID);
      //  final Properties platformPropertiesFromAuthServer = authClient.getPlatformPropertiesFromAuthServer(username,
//                                                                                                           System.getProperty(
//                                                                                                               "jnlp.client.version"),
//                                                                                                           sessionID);
      //  LOGGER.info("PROPERTIES: {}" ,platformPropertiesFromAuthServer);
          /*String username = System.getProperty("DUKA_LIVE_USER");
          String password = System.getProperty("DUKA_LIVE_PW");*/

                String username = System.getProperty("DUKA_DEMO_USER");
                String password = System.getProperty("DUKA_DEMO_PW");
          /*String username = System.getProperty("jnlp.client.username");
          String password = System.getProperty("jnlp.client.password");*/
                String instanceId = System.getProperty("jnlp.api.sid");
                String apiURL = System.getProperty("jnlp.api.url");
                String ticket = System.getProperty("jnlp.auth.ticket");
                String authToken = System.getProperty("jnlp.auth.token");
                String autoLoginUserName = null;
                DeviceIDBean deviceIDBean = null;
                String rememberMeToken = null;
        try {

            final AtomicBoolean autologin = new AtomicBoolean(false);
            final AtomicBoolean autoAuthByRememberMeToken = new AtomicBoolean(false);
            final AtomicBoolean authByRememberMeTokenIsReady = new AtomicBoolean(false);
            final ILoginDialogController loginDialogController = LoginDialogFactory.create(null);
            final PlatformEnvironment demo3 = PlatformEnvironment.DEMO;
            final PlatformEnvironment live3 = PlatformEnvironment.LIVE;

            List<PlatformEnvironment> environments = new ArrayList<>();
            environments.add(demo3);
            environments.add(live3);
            loginDialogController.setPlatformEnvironments(environments);
            loginDialogController.addPlatformEnvironmentListener(new IPlatformEnvironmentListener() {
                @Override
                public void newEnvironmentSelected(final PlatformEnvironment platformEnvironment,
                                                   final PlatformEnvironment platformEnvironment1) {
                    LOGGER.info(platformEnvironment.getEnvironment());
                }
            });


            SwingUtilities.invokeAndWait(new Runnable() {
                public void run() {






                   // loginDialogController.setLoginDialogMode(LoginDialogMode.STAND_ALONE);

                    platformAuthorizationController.setLoginDialogController(loginDialogController);

                    loginDialogController.setPlatformAuthorizationController(platformAuthorizationController);
                    platformAuthorizationController.initialize();
                  /*  PlatformInitUtils.initLookAndFeel();
                    PlatformInitUtils.initStaticValues();*/
                  /*  if (!GreedContext.isStandaloneInstallation()) {
                        platformAuthorizationController = GreedContext.getPlatformAuthorizationController();
                        platformAuthorizationController.initialize();
                    }*/
                    String username = System.getenv("DUKA_DEMO_USER");
                    String password = System.getenv("DUKA_DEMO_PW");
                   /* String username = System.getProperty("jnlp.client.username");
                    String password = System.getProperty("jnlp.client.password");*/
                   LOGGER.info("LOGIN: {} PASSWORD: {}",username,password);
                    String instanceId = System.getProperty("jnlp.api.sid");
                    String apiURL = System.getProperty("jnlp.api.url");
                    String ticket = System.getProperty("jnlp.auth.ticket");
                    String authToken = System.getProperty("jnlp.auth.token");
                    String autoLoginUserName = null;
                    DeviceIDBean deviceIDBean = null;
                    String rememberMeToken = null;
                    if (true) {
                        try {
                          //  ILoginDialogController loginDialogControllerx = GreedContext.getLoginDialogController();
                            String platformEnvironment = System.getProperty("jnlp.client.mode");
                            boolean rememberMeSupported = true; //GreedContext.isRememberMeSupported(platformEnvironment);
                            if (rememberMeSupported) {
                                String platformEnvironmentAsString =  System.getProperty("jnlp.client.mode");


                                PlatformRememberMeAuthBean
                                    authByRememberMeTokenBean = PlatformAuthUtil.getAuthByRememberMeTokenBean(platformEnvironmentAsString,
                                                                                                              loginDialogController);
                                if (authByRememberMeTokenBean.isAutoLoginIgnore()) {
                                    loginDialogController.saveAutologinIgnoreFlag(false);
                                } else if (authByRememberMeTokenBean.autoAuthByRememberMeTokenIsPossible()) {
                                    autoLoginUserName = authByRememberMeTokenBean.getAutoLoginUserName();
                                    rememberMeToken = authByRememberMeTokenBean.getRememberMeToken();
                                    deviceIDBean = authByRememberMeTokenBean.getDeviceIDBean();
                                    autoAuthByRememberMeToken.set(true);
                                }

                                if (authByRememberMeTokenBean.authByRememberMeTokenIsPossible()) {
                                    authByRememberMeTokenIsReady.set(true);
                                }
                            }
                        } catch (Throwable var15) {

                        }
                    }

                    if (!ObjectUtils.isNullOrEmpty(username) && !ObjectUtils.isNullOrEmpty(password) && false) {
                        autologin.set(true);
                      //  GreedClient.this.doAutoLogin();
                    } else if (!ObjectUtils.isNullOrEmpty(username) && !ObjectUtils.isNullOrEmpty(instanceId) && !ObjectUtils.isNullOrEmpty(apiURL) && !ObjectUtils.isNullOrEmpty(ticket)) {
                        autologin.set(true);
                     //   GreedClient.this.doAutoLoginFromBackOffice();
                    } else if (!ObjectUtils.isNullOrEmpty(authToken)) {
                        autologin.set(true);
                     //   GreedClient.this.doAutoLoginFromBackOffice();
                    } else {
                        if (autoAuthByRememberMeToken.get()) {
                            autologin.set(true);
                          //  GreedClient.doAutoLogin(autoLoginUserName, rememberMeToken, deviceIDBean);
                        }

                    }
                }
            });
         /*   if (!autologin.get()) {

                loginDialogController.setLoginDialogState(ILoginDialogController.LoginDialogState.AUTHORIZATION_READY);
                loginDialogController.setVisible(true);
                loginDialogController.setFocusOnFirstEmptyField(false);
                boolean dialogWithTwoViews = CompatibilityUtil.isDialogWithTwoViews(ILoginDialogController.class);
                if (!dialogWithTwoViews) {
                    LoginDialogBean loginDialogBean = loginDialogController.getLoginDialogBean();
                    if (!autoAuthByRememberMeToken.get() && !authByRememberMeTokenIsReady.get()) {
                        if (loginDialogBean.isPinSelected()) {
                            loginDialogController.obtainCaptcha();
                        }
                    } else if (loginDialogBean.isPinSelected()) {
                        loginDialogController.setPinCheckBoxSelected(false);
                    }
                }
            }
*/


            loginDialogController.setLoginDialogState(ILoginDialogController.LoginDialogState.AUTHORIZATION_READY);
            loginDialogController.setVisible(true);
            loginDialogController.setFocusOnFirstEmptyField(false);
            boolean dialogWithTwoViews = CompatibilityUtil.isDialogWithTwoViews(ILoginDialogController.class);
            if (!dialogWithTwoViews) {
                LoginDialogBean loginDialogBean = loginDialogController.getLoginDialogBean();
                if (!autoAuthByRememberMeToken.get() && !authByRememberMeTokenIsReady.get()) {
                    if (loginDialogBean.isPinSelected()) {
                        loginDialogController.obtainCaptcha();
                    }
                } else if (loginDialogBean.isPinSelected()) {
                    loginDialogController.setPinCheckBoxSelected(false);
                }
            }
        } catch (Exception var8) {
            LOGGER.error(var8.getMessage(), var8);
        }

        if (useDemo) {
         /*   DClient client = new DClient();
            client.connect(urlDemo,usernameD,passwordD);*/

           /* Thread.sleep(10000L);
            client.disconnect();
            Thread.sleep(5000L);
            client.reconnect();*/
            //wait for it to connect
//            int i = 10; //wait max ten seconds
//            while (i > 0 && !client.isConnected()) {
//                Thread.sleep(100000);
//                i--;
//            }
//            if (!client.isConnected()) {
//                LOGGER.error("Failed to connect Dukascopy servers");
//                System.exit(1);
//            }
           // String pin = PinDialog.showAndGetPin();
//            serverResponse = authClient.getAPIsAndTicketUsingLogin_SRP6(usernameD,
//                                                                        passwordD,
//                                                                        null,
//                                                                        null,
//                                                                        sessionID,
//                                                                        JFOREXSDK_PLATFORM);
        } else {

          /*  String pin = PinDialog.showAndGetPin();
            serverResponse = authClient.getAPIsAndTicketUsingLogin_SRP6(username,
                                                                        password,
                                                                        captchaId,
                                                                        pin,
                                                                        sessionID,
                                                                        JFOREXSDK_PLATFORM);*/
        }







       /* AuthorizationServerResponse serverResponse = authClient.getAPIsAndTicketUsingLogin(username,
                                                                                           password,
                                                                                           captchaId,
                                                                                           pin,
                                                                                           UUID.randomUUID().toString(),
                                                                                           JFOREXSDK_PLATFORM);
*/

        //            Matcher
        //                matcher = AuthorizationClient.RESULT_PATTERN.matcher(fastestAPIAndTicket);
        //            String ticket = matcher.group(7);

        //            LOGGER.info(ticket);

       // String fastestAPIAndTicket = serverResponse.getFastestAPIAndTicket();
       // LOGGER.info(serverResponse.getMessage());
    }


    public static BufferedImage getCaptchaImage(String jnlp) throws Exception {

//        AuthorizationProperties properties = AuthorizationPropertiesFactory.getAuthorizationProperties(url);
//
//
//        Collection<String> authServerUrls = properties.getLoginUrlStr();
//        AuthorizationClient authorizationClient = AuthorizationClient.getInstance(authServerUrls, DEFAULT_VERSION);
        //CaptchaImage imageCaptchaMap     = authorizationClient.getImageCaptcha();

        Map<String, BufferedImage> imageCaptchaMap = authClient.getImageCaptcha();
        if (!imageCaptchaMap.isEmpty()) {
            Map.Entry<String, BufferedImage> imageCaptchaEntry = imageCaptchaMap.entrySet().iterator().next();
            captchaId = imageCaptchaEntry.getKey();
            return imageCaptchaEntry.getValue();
        } else {
            return null;
        }


    }

    @SuppressWarnings("serial")
    private static class PinDialog extends JDialog {

        private final static JFrame noParentFrame = null;
        private final JTextField pinfield = new JTextField();

        public PinDialog() throws Exception {

            super(noParentFrame, "PIN Dialog", true);

            JPanel captchaPanel = new JPanel();
            captchaPanel.setLayout(new BoxLayout(captchaPanel, BoxLayout.Y_AXIS));

            final JLabel captchaImage = new JLabel();
            captchaImage.setIcon(new ImageIcon(getCaptchaImage(url)));
            captchaPanel.add(captchaImage);


            captchaPanel.add(pinfield);
            getContentPane().add(captchaPanel);

            JPanel buttonPane = new JPanel();

            JButton btnLogin = new JButton("Login");
            buttonPane.add(btnLogin);
            btnLogin.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    setVisible(false);
                    dispose();
                }
            });

            JButton btnReload = new JButton("Reload");
            buttonPane.add(btnReload);
            btnReload.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {

                    try {
                        captchaImage.setIcon(new ImageIcon(getCaptchaImage(url)));
                    } catch (Exception ex) {
                        LOGGER.info(ex.getMessage(), ex);
                    }
                }
            });
            getContentPane().add(buttonPane, BorderLayout.SOUTH);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            pack();
            setVisible(true);
        }

        static String showAndGetPin() throws Exception {

            JTextField pinfield = new PinDialog().pinfield;
            String text = pinfield.getText();
            return text;
        }
    }
}


