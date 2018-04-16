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

package org.softcake.yubari.netty.ssl;

import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyStore;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class DukasTrustMangerFactory extends SimpleTrustManagerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DDSTrustManager.class);

    private X509TrustManager sunX509TrustManager;
    private ClientSSLContextListener listener;
    private HostNameVerifier verifier;
    private String hostName;


    private final TrustManager tm = new DDSTrustManager(null,null,null);

    @Override
    protected void engineInit(final KeyStore keyStore) throws Exception {

    }

    @Override
    protected void engineInit(final ManagerFactoryParameters managerFactoryParameters) throws Exception {

    }

    @Override
    public TrustManager[] engineGetTrustManagers() {

        return new TrustManager[] { tm };
    }
}
