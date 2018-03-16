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

package org.softcake.yubari.netty.mina;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class SSLContextFactory {
    private static final String KEYSTORE_PASSFILE = "keystore.passfile";
    private static final String SSL_KEY_MANAGER_FACTORY_ALGORITHM = "ssl.KeyManagerFactory.algorithm";
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLContextFactory.class);
    private static final String PROTOCOL = "SSL";
    private static final String KEY_MANAGER_FACTORY_ALGORITHM;
    private static final String KEYSTORE = "dukascopy.cert";
    private static char[] PW;
    private static SSLContext serverInstance;
    private static SSLContext clientInstance;
    private static String clientTargetHost;
    private static ClientSSLContextListener clientListener;
    private static Object mutex = new Object();

    static {
        String algorithm = Security.getProperty(SSL_KEY_MANAGER_FACTORY_ALGORITHM);
        if (algorithm == null) {
            algorithm = "SunX509";
        }


        KEY_MANAGER_FACTORY_ALGORITHM = algorithm;
        PW = new char[]{'d', 'u', 'k', 'a', 's', 'p', 'w'};
        serverInstance = null;
        clientInstance = null;
    }

    public SSLContextFactory() {

    }


    public static SSLContext getInstance(boolean server, ClientSSLContextListener listener, String targetHost)
        throws GeneralSecurityException, IOException {

        return getInstance(server, listener, targetHost, null);
    }

    public static SSLContext getInstance(boolean server,
                                         ClientSSLContextListener listener,
                                         String targetHost,
                                         TrustSubject trustSubject) throws GeneralSecurityException, IOException {

        return getInstance(server, listener, targetHost, trustSubject, null);
    }

    public static SSLContext getInstance(boolean server,
                                         ClientSSLContextListener listener,
                                         String targetHost,
                                         TrustSubject trustSubject,
                                         String pathToCertificate) throws GeneralSecurityException, IOException {

        SSLContext retInstance;

        if (server) {
            retInstance = serverInstance;
            if (retInstance == null) {
                synchronized (mutex) {
                    retInstance = serverInstance;
                    if (retInstance == null) {

                        serverInstance = retInstance = createServerSSLContext(pathToCertificate);
                    }
                }

            }

        } else {
            retInstance = clientInstance;
            if (retInstance == null) {
                synchronized (mutex) {
                    retInstance = clientInstance;
                    if ((retInstance == null)
                        || ((clientTargetHost != null) && !clientTargetHost.equals(targetHost))
                        || ((clientListener != null) && !clientListener.equals(listener))) {

                        clientInstance = retInstance = createClientSSLContext(listener, targetHost, trustSubject);
                        clientTargetHost = targetHost;
                        clientListener = listener;
                    }
                }
            }

        }

        return retInstance;
    }

    private static SSLContext createServerSSLContext(String pathToCertificate)
        throws GeneralSecurityException, IOException {

        KeyStore ks = KeyStore.getInstance("JKS");
        readStorePass();
        LOGGER.info("Reading SSL certificate");

        try (InputStream in = pathToCertificate == null ? SSLContextFactory.class.getClassLoader().getResourceAsStream(
            KEYSTORE) : new FileInputStream(new File(pathToCertificate))) {

            if (in == null) {
                throw new IOException("SSL CERTIFICATE NOT FOUND");
            }
            ks.load(in, PW);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KEY_MANAGER_FACTORY_ALGORITHM);
        kmf.init(ks, PW);
        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    private static void readStorePass() throws IOException {

        if (System.getProperty(KEYSTORE_PASSFILE) != null) {
            LOGGER.info("Found keystore pass file name in VM properties: {}", System.getProperty(KEYSTORE_PASSFILE));


            try (InputStream storePass = SSLContextFactory.class.getClassLoader()
                                                                .getResourceAsStream(System.getProperty(
                                                                    KEYSTORE_PASSFILE))) {
                if (storePass == null) {
                    LOGGER.info("Store pass file not found: {}", System.getProperty(KEYSTORE_PASSFILE));
                    return;
                }


                try (BufferedReader br = new BufferedReader(new InputStreamReader(storePass))) {
                    String line = br.readLine();

                    while (line != null) {
                        line = line.trim();
                        if (line.startsWith("#")) {
                            line = br.readLine();
                        } else {
                            if (line.length() >= 1) {
                                PW = line.toCharArray();
                                LOGGER.info("Key store pass read!!!");
                                break;
                            }

                            line = br.readLine();
                        }
                    }
                }

            }
        }

    }

    private static SSLContext createClientSSLContext(ClientSSLContextListener listener,
                                                     String targetHost,
                                                     TrustSubject trustSubject) throws GeneralSecurityException {

        SSLContext context = SSLContext.getInstance(PROTOCOL);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(KEY_MANAGER_FACTORY_ALGORITHM);
        factory.init((KeyStore) null);
        X509TrustManager manager = (X509TrustManager) factory.getTrustManagers()[0];
        DDSTrustManager mg = new DDSTrustManager(listener, manager, trustSubject);
        mg.setHostName(targetHost);
        context.init(null, new TrustManager[]{mg}, null);
        return context;
    }


}
