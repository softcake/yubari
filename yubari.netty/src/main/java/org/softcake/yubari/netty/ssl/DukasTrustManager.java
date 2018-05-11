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
import org.softcake.yubari.netty.ssl.verifier.SSLHostnameVerifier;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Observer;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.X509TrustManager;

class DukasTrustManager implements X509TrustManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DukasTrustManager.class);
    private final PublishSubject<SecurityExceptionEvent> observable;
    private ObservableEmitter<SecurityExceptionEvent> subscriber;
    private X509TrustManager sunX509TrustManager;
    private String hostName;

    DukasTrustManager(final Consumer<SecurityExceptionEvent> listener, final X509TrustManager manager, final String hostName) {


        this.sunX509TrustManager = PreCheck.parameterNotNull(manager, "manager");
        this.hostName = PreCheck.parameterNotNullOrEmpty(hostName, "hostName");
        this.observable = PublishSubject.create();
        observable.subscribe(listener);
        //PreCheck.parameterNotNull(listener, "listener").subscribe(this.observable);
    }

    private static void logCertificateChain(final X509Certificate[] chain) {

        for (final X509Certificate x509Certificate : chain) {
            LOGGER.debug("\tIssuer: {}; Subject: {}; SN: {}; Basic constraints: {}",
                         x509Certificate.getIssuerDN().getName(),
                         x509Certificate.getSubjectDN().getName(),
                         x509Certificate.getSerialNumber().toString(16),
                         x509Certificate.getBasicConstraints());

        }
    }

    public void observeScurityEvent(Observer<SecurityExceptionEvent> event){
        observable.subscribe(event);
    }

    public void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {


        PreCheck.parameterNotNullOrEmpty(chain, "chain");


        try {

            LOGGER.debug("Checking server is trusted for certificates chain:{}","");

            if (!PreCheck.isParamNullOrEmpty(chain)) {
                logCertificateChain(chain);
            } else {
                LOGGER.debug("\tchain is empty{}","");
            }

            this.sunX509TrustManager.checkServerTrusted(chain, authType);
            checkTrusted(chain[0]);
        } catch (final CertificateException e) {
            LOGGER.error("Certificate exception for certificates chain:", e);

            if (!PreCheck.isParamNullOrEmpty(chain)) {
                logCertificateChain(chain);
            } else {
                LOGGER.error("\tchain is empty{}","");
            }

            observable.onNext(new SecurityExceptionEvent(chain, authType, e));
        }

    }

    private void checkTrusted(final X509Certificate x509Certificate) throws CertificateException {

        try {


            LOGGER.debug("Checking host name validity for host [{}] and certificate subject [{}]",
                         this.hostName,
                         x509Certificate.getSubjectDN().getName());

            SSLHostnameVerifier.DEFAULT.check(this.hostName, x509Certificate);

        } catch (final SSLException e) {
            LOGGER.error("SSLException while checking host name validity for host [{}] and certificate subject "
                         + "[{}]", this.hostName, x509Certificate.getSubjectDN().getName());
            throw new CertificateException(e.getMessage());
        }
    }

    public void checkClientTrusted(final X509Certificate[] chain, final String authType) {

        try {
            this.sunX509TrustManager.checkClientTrusted(chain, authType);
        } catch (final CertificateException e) {
            observable.onNext(new SecurityExceptionEvent(chain, authType, e));
        }
    }


    public X509Certificate[] getAcceptedIssuers() {

        return this.sunX509TrustManager.getAcceptedIssuers();
    }
}
