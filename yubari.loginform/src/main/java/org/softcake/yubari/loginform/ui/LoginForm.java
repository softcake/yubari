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

package org.softcake.yubari.loginform.ui;

import org.softcake.yubari.loginform.controller.LoginDialogMode;
import org.softcake.yubari.loginform.resources.IResourcesProvider;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginForm extends JFrame {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginForm.class);
    public static final String ID_JF_LOGINFORM = "ID_JF_LOGINFORM";
    public static final String TITLE = "FX Marketplace";
    private static final Dimension MIN_SIZE_STAND_ALONE = new Dimension(280, 100);
    private static final Dimension MIN_SIZE_WEB_START = new Dimension(290, 10);
    private volatile LoginDialogController loginDialogController = null;

    public LoginForm() {
        this.build();
    }

    public LoginForm(LoginDialogController loginDialogController) {
        this.loginDialogController = loginDialogController;
        if (this.loginDialogController.getLoginDialogMode() == LoginDialogMode.STAND_ALONE) {
            ;
        }

        this.build();
    }

    public void build() {
        this.setTitle("FX Marketplace");
        this.setName("ID_JF_LOGINFORM");
        if (this.loginDialogController != null) {
            try {
                IResourcesProvider resourcesProvider = this.loginDialogController.getResourcesProvider();
                if (resourcesProvider != null) {
                    Image platformLogo = resourcesProvider.getPlatformLogo();
                    this.setIconImage(platformLogo);
                }
            } catch (Throwable var4) {
                LOGGER.error(var4.getMessage(), var4);
            }
        }

        if (this.loginDialogController == null) {
            this.loginDialogController = new LoginDialogController();
        }

        LoginPanel loginPanel = new LoginPanel(this.loginDialogController);
        this.setContentPane(loginPanel);
        LoginDialogMode loginDialogMode = this.loginDialogController.getLoginDialogMode();
        Dimension MIN_SIZE;
        switch(loginDialogMode) {
        case STAND_ALONE:
            MIN_SIZE = MIN_SIZE_STAND_ALONE;
            break;
        case WEB_START:
            MIN_SIZE = MIN_SIZE_STAND_ALONE;
            break;
        default:
            MIN_SIZE = MIN_SIZE_STAND_ALONE;
        }

        this.setMinimumSize(MIN_SIZE);
        this.setDefaultCloseOperation(0);
        this.setResizable(false);
        this.setLocationRelativeTo((Component)null);
        this.loginDialogController.setWindow(this);
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                LoginForm.this.revalidate();
                LoginForm.this.repaint();
                LoginForm.this.pack();
                LoginForm.this.setLocationRelativeTo((Component)null);
            }
        });
    }

    public LoginDialogController getLoginDialogController() {
        return this.loginDialogController;
    }
}
