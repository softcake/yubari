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

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
final class X509TrustManagerWrapper extends X509ExtendedTrustManager {

    private final X509TrustManager delegate;

    X509TrustManagerWrapper(X509TrustManager delegate) {
        this.delegate = checkNotNull(delegate, "delegate");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String s) throws CertificateException {
        delegate.checkClientTrusted(chain, s);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String s, Socket socket)
            throws CertificateException {
        delegate.checkClientTrusted(chain, s);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String s, SSLEngine sslEngine)
            throws CertificateException {
        delegate.checkClientTrusted(chain, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
        delegate.checkServerTrusted(chain, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s, Socket socket)
            throws CertificateException {
        delegate.checkServerTrusted(chain, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s, SSLEngine sslEngine)
            throws CertificateException {
        delegate.checkServerTrusted(chain, s);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }
}
