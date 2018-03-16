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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509TrustManager;

class DDSTrustManager implements X509TrustManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DDSTrustManager.class);
    private X509TrustManager sunX509TrustManager;
    private ClientSSLContextListener listener;
    private HostNameVerifier verifier;
    private String hostName;
    private final TrustSubject trustSubject;

    public DDSTrustManager(ClientSSLContextListener listener, X509TrustManager manager) {

        this(listener, manager, null);
    }

    public DDSTrustManager(ClientSSLContextListener listener, X509TrustManager manager, TrustSubject trustSubject) {

        this.verifier = new HostNameVerifier();
        this.listener = listener;
        this.sunX509TrustManager = manager;
        this.trustSubject = trustSubject;
    }

    public String getHostName() {

        return this.hostName;
    }

    public void setHostName(String hostName) {

        this.hostName = hostName;
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

        if (TrustSubject.ANDROID.equals(this.trustSubject)) {
            boolean[] peerCertKeyUsage = chain[0].getKeyUsage();
            if (peerCertKeyUsage != null) {
                peerCertKeyUsage[3] = true;
            }
        }


        try {

            LOGGER.debug("Checking server is trusted for certificates chain:");


            if (chain != null && chain.length > 0) {


                for (int i = 0; i < chain.length; ++i) {
                    X509Certificate x509Certificate = chain[i];

                    LOGGER.debug("\tIssuer: {}; Subject: {}; SN: {}; Basic constraints: {}",
                                 x509Certificate.getIssuerDN().getName(),
                                 x509Certificate.getSubjectDN().getName(),
                                 x509Certificate.getSerialNumber().toString(16),
                                 x509Certificate.getBasicConstraints());

                }
            } else {
                LOGGER.debug("\tchain is empty");
            }

            this.sunX509TrustManager.checkServerTrusted(chain, authType);

            try {

                LOGGER.debug("Checking host name validity for host [{}] and certificate subject [{}]",
                             this.hostName,
                             chain[0].getSubjectDN().getName());


                this.verifier.check(new String[]{this.hostName}, chain[0]);
            } catch (SSLException e) {
                LOGGER.error("SSLException while checking host name validity for host [{}] and certificate subject "
                             + "[{}]",
                             this.hostName,
                             chain[0].getSubjectDN().getName());
                throw new CertificateException(e.getMessage());
            }
        } catch (CertificateException e) {
            LOGGER.error("Certificate exception for certificates chain:");
            if (chain != null && chain.length > 0) {

                for (int i = 0; i < chain.length; ++i) {
                    X509Certificate x509Certificate = chain[i];

                    LOGGER.debug("\tIssuer: {}; Subject: {}; SN: {}; Basic constraints: {}",
                                 x509Certificate.getIssuerDN().getName(),
                                 x509Certificate.getSubjectDN().getName(),
                                 x509Certificate.getSerialNumber().toString(16),
                                 x509Certificate.getBasicConstraints());

                }
            } else {
                LOGGER.error("\tchain is empty");
            }

            this.listener.securityException(chain, authType, e);
        }

    }

    public void checkClientTrusted(X509Certificate[] chain, String authType) {

        try {
            this.sunX509TrustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException var4) {
            this.listener.securityException(chain, authType, var4);
        }

    }

    public X509Certificate[] getAcceptedIssuers() {

        return this.sunX509TrustManager.getAcceptedIssuers();
    }
}
