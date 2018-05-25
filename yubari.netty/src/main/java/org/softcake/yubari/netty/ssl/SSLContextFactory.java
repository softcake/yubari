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

import io.reactivex.Observer;
import io.reactivex.functions.Consumer;
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
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class SSLContextFactory {
    private static final String KEY_MANAGER_FACTORY_ALGORITHM;
    private static final String KEYSTORE_PASSFILE = "keystore.passfile";
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLContextFactory.class);
    private static final String PROTOCOL = "SSL";
    private static final String KEYSTORE = "dukascopy.cert";
    private static final Object MUTEX = new Object();
    private static char[] pass;
    private static SSLContext serverInstance;
    private static SSLContext clientInstance;
    private static Observer<SecurityExceptionEvent> observer;

    static {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }


        KEY_MANAGER_FACTORY_ALGORITHM = algorithm;
        pass = new char[]{'d', 'u', 'k', 'a', 's', 'p', 'w'};
        serverInstance = null;
        clientInstance = null;
    }

    private SSLContextFactory() {

        throw new IllegalAccessError("Utility class");
    }



    public static SSLContext getInstance(final boolean server,
                                         final Consumer<SecurityExceptionEvent> listener,
                                         final String targetHost) throws SSLException {

        return getInstance(server, listener, targetHost, null);
    }

    public static SSLContext getInstance(final boolean server,
                                         final  Consumer<SecurityExceptionEvent> listener,
                                         final String targetHost,
                                         final String pathToCertificate) throws SSLException {

        final SSLContext retInstance;

        if (server) {
            retInstance = getServerInstance(pathToCertificate);

        } else {
            retInstance = getClientInstance(listener, targetHost);

        }

        return retInstance;
    }

    private static SSLContext getClientInstance(final  Consumer<SecurityExceptionEvent> listener, final String targetHost)
        throws SSLException {

        SSLContext retInstance = clientInstance;
        if (retInstance == null) {
            synchronized (MUTEX) {
                retInstance = clientInstance;
                if (retInstance == null) {

                    retInstance = createClientSSLContext(listener, targetHost);
                    clientInstance = retInstance;
                }
            }
        }
        return retInstance;
    }

    private static SSLContext getServerInstance(final String pathToCertificate) throws SSLException {

        SSLContext retInstance = serverInstance;
        if (retInstance == null) {
            synchronized (MUTEX) {
                retInstance = serverInstance;
                if (retInstance == null) {

                    retInstance = createServerSSLContext(pathToCertificate);
                    serverInstance = retInstance;
                }
            }

        }
        return retInstance;
    }

    /**
     * Creates a new server-side {@link SSLContext}.
     *
     * @param pathToCertificate path to an X.509 certificate chain file
     *
     * @return the JDK {@link SSLContext} for server object.
     * @throws SSLException
     */
    private static SSLContext createServerSSLContext(final String pathToCertificate) throws SSLException {


        try (final InputStream in = pathToCertificate == null
                                    ? SSLContextFactory.class.getClassLoader()
                                                             .getResourceAsStream(KEYSTORE)
                                    : new FileInputStream(new File(pathToCertificate))) {
            final KeyManagerFactory kmf = getKeyManagerFactory(in);
            final SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
            sslContext.init(kmf.getKeyManagers(), null, null);
            return sslContext;

        } catch (GeneralSecurityException | IOException e) {
            if (e instanceof SSLException) {
                throw (SSLException) e;
            }
            throw new SSLException("failed to initialize the server-side SSL context", e);
        }
    }

    private static KeyManagerFactory getKeyManagerFactory(final InputStream in) throws
                                                                                GeneralSecurityException,
                                                                                IOException{

        final KeyStore ks = KeyStore.getInstance("JKS");
        readStorePass();
        if (in == null) {
            throw new IOException("SSL CERTIFICATE NOT FOUND");
        }

        ks.load(in, pass);
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KEY_MANAGER_FACTORY_ALGORITHM);
        kmf.init(ks, pass);
        return kmf;
    }

    private static void readStorePass() throws IOException {

        if (System.getProperty(KEYSTORE_PASSFILE) != null) {
            LOGGER.info("Found keystore pass file name in VM properties: {}", System.getProperty(KEYSTORE_PASSFILE));


            try (final InputStream storePass = SSLContextFactory.class.getClassLoader()
                                                                      .getResourceAsStream(System.getProperty(
                                                                          KEYSTORE_PASSFILE))) {
                if (storePass == null) {
                    LOGGER.info("Store pass file not found: {}", System.getProperty(KEYSTORE_PASSFILE));
                    return;
                }


                try (final BufferedReader br = new BufferedReader(new InputStreamReader(storePass))) {
                    String line = br.readLine();

                    while (line != null) {
                        line = line.trim();
                        if (line.startsWith("#")) {
                            line = br.readLine();
                        } else {
                            if (line.length() >= 1) {
                                pass = line.toCharArray();
                                break;
                            }

                            line = br.readLine();
                        }
                    }
                }

            }
        }

    }

    private static TrustManagerFactory buildTrustManagerFactory(TrustManagerFactory trustManagerFactory)
        throws NoSuchAlgorithmException, KeyStoreException {

        // Set up trust manager factory to use our key store.
        if (trustManagerFactory == null) {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        }
        trustManagerFactory.init((KeyStore)null);

        return trustManagerFactory;
    }

    private static SSLContext createClientSSLContext(final  Consumer<SecurityExceptionEvent> listener, final String targetHost)
        throws SSLException {

        try {

            final SSLContext context = SSLContext.getInstance(PROTOCOL);
            final TrustManagerFactory factory = buildTrustManagerFactory(TrustManagerFactory.getInstance(
                KEY_MANAGER_FACTORY_ALGORITHM));

            final X509TrustManager x509TrustManager = (X509TrustManager) factory.getTrustManagers()[0];
            final DukasTrustManager trustManager = new DukasTrustManager(listener, x509TrustManager, targetHost);

           // trustManager.observeScurityEvent();
            context.init(null, new TrustManager[]{trustManager}, null);
            return context;
        } catch (GeneralSecurityException e) {

            throw new SSLException("failed to initialize the client-side SSL context", e);
        }

    }

    public static void observeSecurity(Observer<SecurityExceptionEvent> exceptionEventObserver) {
        observer = exceptionEventObserver;
    }
}
