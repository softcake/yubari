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

import com.dukascopy.login.controller.ILoginDialogController;
import com.dukascopy.login.controller.IPlatformAuthorizationController;
import com.dukascopy.login.controller.LoginDialogComponents;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CancelAuthorizationListener implements ActionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CancelAuthorizationListener.class);
    private ILoginDialogController loginDialogController;

    public CancelAuthorizationListener(ILoginDialogController loginDialogController) {
        this.loginDialogController = loginDialogController;
    }

    public void actionPerformed(ActionEvent e) {
        try {
            IPlatformAuthorizationController platformAuthorizationController = this.loginDialogController.getPlatformAuthorizationController();
            this.loginDialogController.setEnabled(false, new LoginDialogComponents[]{LoginDialogComponents.OK_BUTTON});
            platformAuthorizationController.cancel();
        } catch (Exception var3) {
            LOGGER.error(var3.getMessage(), var3);
        }

    }
}
