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

package org.softcake.yubari.netty.ssl.verifier;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public interface SSLHostnameVerifier extends javax.net.ssl.HostnameVerifier {

    /**
     * The DEFAULT HostnameVerifier works the same way as Curl and Firefox.
     * <p/>
     * The hostname must match either the first CN, or any of the subject-alts.
     * A wildcard can occur in the CN, and in any of the subject-alts.
     * <p/>
     * The only difference between DEFAULT and STRICT is that a wildcard (such
     * as "*.foo.com") with DEFAULT matches all subdomains, including
     * "a.b.foo.com".
     */
    SSLHostnameVerifier DEFAULT = new AbstractVerifier() {
        @Override
        public final void check(final String[] hosts, final String[] cns, final String[] subjectAlts)
            throws SSLException {

            check(hosts, cns, subjectAlts, false, false);
        }

        @Override
        public final String toString() { return "DEFAULT"; }
    };
    /**
     * The DEFAULT_AND_LOCALHOST HostnameVerifier works like the DEFAULT
     * one with one additional relaxation:  a host of "localhost",
     * "localhost.localdomain", "127.0.0.1", "::1" will always pass, no matter
     * what is in the server's certificate.
     */
    SSLHostnameVerifier DEFAULT_AND_LOCALHOST = new AbstractVerifier() {
        @Override
        public final void check(final String[] hosts, final String[] cns, final String[] subjectAlts)
            throws SSLException {

            if (isLocalhost(hosts[0])) {
                return;
            }
            check(hosts, cns, subjectAlts, false, false);
        }

        @Override
        public final String toString() { return "DEFAULT_AND_LOCALHOST"; }
    };
    /**
     * The STRICT HostnameVerifier works the same way as java.net.URL in Sun
     * Java 1.4, Sun Java 5, Sun Java 6.  It's also pretty close to IE6.
     * This implementation appears to be compliant with RFC 2818 for dealing
     * with wildcards.
     * <p/>
     * The hostname must match either the first CN, or any of the subject-alts.
     * A wildcard can occur in the CN, and in any of the subject-alts.  The
     * one divergence from IE6 is how we only check the first CN.  IE6 allows
     * a match against any of the CNs present.  We decided to follow in
     * Sun Java 1.4's footsteps and only check the first CN.
     * <p/>
     * A wildcard such as "*.foo.com" matches only subdomains in the same
     * level, for example "a.foo.com".  It does not match deeper subdomains
     * such as "a.b.foo.com".
     */
    SSLHostnameVerifier STRICT = new AbstractVerifier() {
        @Override
        public final void check(final String[] host, final String[] cns, final String[] subjectAlts)
            throws SSLException {

            check(host, cns, subjectAlts, false, true);
        }

        @Override
        public final String toString() { return "STRICT"; }
    };
    /**
     * The STRICT_IE6 HostnameVerifier works just like the STRICT one with one
     * minor variation:  the hostname can match against any of the CN's in the
     * server's certificate, not just the first one.  This behaviour is
     * identical to IE6's behaviour.
     */
    SSLHostnameVerifier STRICT_IE6 = new AbstractVerifier() {
        @Override
        public final void check(final String[] host, final String[] cns, final String[] subjectAlts)
            throws SSLException {

            check(host, cns, subjectAlts, true, true);
        }

        @Override
        public final String toString() { return "STRICT_IE6"; }
    };
    /**
     * The ALLOW_ALL HostnameVerifier essentially turns hostname verification
     * off.  This implementation is a no-op, and never throws the SSLException.
     */
    SSLHostnameVerifier ALLOW_ALL = new AbstractVerifier() {
        @Override
        public final void check(final String[] host, final String[] cns, final String[] subjectAlts) {
            // Allow everything - so never blowup.
        }

        @Override
        public final String toString() { return "ALLOW_ALL"; }
    };

    @Override
    boolean verify(String host, SSLSession session);

    void check(String host, SSLSocket ssl) throws IOException;

    void check(String host, X509Certificate cert) throws SSLException;

    void check(String host, String[] cns, String[] subjectAlts) throws SSLException;

    void check(String[] hosts, SSLSocket ssl) throws IOException;

    void check(String[] hosts, X509Certificate cert) throws SSLException;

    /**
     * Checks to see if the supplied hostname matches any of the supplied CNs
     * or "DNS" Subject-Alts.  Most implementations only look at the first CN,
     * and ignore any additional CNs.  Most implementations do look at all of
     * the "DNS" Subject-Alts. The CNs or Subject-Alts may contain wildcards
     * according to RFC 2818.
     *
     * @param cns         CN fields, in order, as extracted from the X.509
     *                    certificate.
     * @param subjectAlts Subject-Alt fields of type 2 ("DNS"), as extracted
     *                    from the X.509 certificate.
     * @param hosts       The array of hostnames to verify.
     * @throws SSLException If verification failed.
     */
    void check(String[] hosts, String[] cns, String[] subjectAlts) throws SSLException;


}