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

package org.softcake.yubari.loginform.ui.helper;

import org.softcake.yubari.loginform.utils.WaitHelper;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AWTThreadExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AWTThreadExecutor.class);

    public AWTThreadExecutor() {
    }

    protected abstract void invoke();

    public void execute() {
        if (SwingUtilities.isEventDispatchThread()) {
            this.invoke();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    AWTThreadExecutor.this.invoke();
                }
            });
        }

    }

    public void executeAndWait() {
        if (SwingUtilities.isEventDispatchThread()) {
            this.invoke();
        } else {
            this.executeAndWaitImpl();
        }

    }

    private void executeAndWaitImpl() {
        final WaitHelper waitHelper = new WaitHelper();
        Runnable r = new Runnable() {
            public void run() {
                try {
                    SwingUtilities.invokeAndWait(new Runnable() {
                        public void run() {
                            try {
                                AWTThreadExecutor.this.invoke();
                            } catch (Exception var5) {
                                AWTThreadExecutor.LOGGER.error(var5.getMessage(), var5);
                            } finally {
                                waitHelper.doNotify();
                            }

                        }
                    });
                } catch (InvocationTargetException var2) {
                    AWTThreadExecutor.LOGGER.error("Invocation error", var2);
                } catch (InterruptedException var3) {
                    AWTThreadExecutor.LOGGER.error("AWT thread interrupted", var3);
                }

            }
        };
        (new Thread(r)).start();
        waitHelper.doWait();
    }
}
