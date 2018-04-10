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

import org.softcake.cherry.core.base.PreCheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509TrustManager;

class DDSTrustManager implements X509TrustManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DDSTrustManager.class);
    private final TrustSubject trustSubject;
    private X509TrustManager sunX509TrustManager;
    private ClientSSLContextListener listener;
    private HostNameVerifier verifier;
    private String hostName;

    public DDSTrustManager(final ClientSSLContextListener listener, final X509TrustManager manager) {

        this(listener, manager, null);
    }

    public DDSTrustManager(final ClientSSLContextListener listener, final X509TrustManager manager, final TrustSubject trustSubject) {

        this.verifier = new HostNameVerifier();
        this.listener = listener;
        this.sunX509TrustManager = manager;
        this.trustSubject = trustSubject;
    }

    public String getHostName() {

        return this.hostName;
    }

    public void setHostName(final String hostName) {

        this.hostName = hostName;
    }

    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {

        PreCheck.parameterNotNullOrEmpty(chain, "chain");

        if (TrustSubject.ANDROID.equals(this.trustSubject)) {
            final boolean[] peerCertKeyUsage = chain[0].getKeyUsage();
            if (peerCertKeyUsage != null) {
                peerCertKeyUsage[3] = true;
            }
        }


        try {

            LOGGER.debug("Checking server is trusted for certificates chain:");


            if (!PreCheck.isParamNullOrEmpty(chain)) {


                for (int i = 0; i < chain.length; ++i) {
                    final X509Certificate x509Certificate = chain[i];

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
            } catch (final SSLException e) {
                LOGGER.error("SSLException while checking host name validity for host [{}] and certificate subject "
                             + "[{}]", this.hostName, chain[0].getSubjectDN().getName());
                throw new CertificateException(e.getMessage());
            }
        } catch (final CertificateException e) {
            LOGGER.error("Certificate exception for certificates chain:",e);
            if (chain != null && chain.length > 0) {

                for (int i = 0; i < chain.length; ++i) {
                    final X509Certificate x509Certificate = chain[i];

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

    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {

        try {
            this.sunX509TrustManager.checkClientTrusted(chain, authType);
        } catch (final CertificateException var4) {
            this.listener.securityException(chain, authType, var4);
        }

    }

    public X509Certificate[] getAcceptedIssuers() {

        return this.sunX509TrustManager.getAcceptedIssuers();
    }
}
