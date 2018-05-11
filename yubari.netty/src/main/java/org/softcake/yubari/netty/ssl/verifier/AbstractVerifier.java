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
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.TreeSet;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * @author The Softcake Authors.
 */
abstract class AbstractVerifier implements SSLHostnameVerifier {

    /**
     * This contains a list of 2nd-level domains that aren't allowed to
     * have wildcards when combined with country-codes.
     * For example: [*.co.uk].
     * <p/>
     * The [*.co.uk] problem is an interesting one.  Should we just hope
     * that CA's would never foolishly allow such a certificate to happen?
     * Looks like we're the only implementation guarding against this.
     * Firefox, Curl, Sun Java 1.4, 5, 6 don't bother with this check.
     */
    private static final String[] BAD_COUNTRY_2LDS = {"ac",
                                                      "co",
                                                      "com",
                                                      "ed",
                                                      "edu",
                                                      "go",
                                                      "gouv",
                                                      "gov",
                                                      "info",
                                                      "lg",
                                                      "ne",
                                                      "net",
                                                      "or",
                                                      "org"};

    private static final String[] LOCALHOSTS = {"::1", "127.0.0.1", "localhost", "localhost.localdomain"};


    static {
        // Just in case developer forgot to manually sort the array.  :-)
        Arrays.sort(BAD_COUNTRY_2LDS);
        Arrays.sort(LOCALHOSTS);
    }

    AbstractVerifier() {}

    private static boolean isIP4Address(final String cn) {

        boolean isIP4 = true;
        String tld = cn;
        final int x = cn.lastIndexOf('.');
        // We only bother analyzing the characters after the final dot
        // in the name.
        if (x >= 0 && x + 1 < cn.length()) {
            tld = cn.substring(x + 1);
        }
        for (int i = 0; i < tld.length(); i++) {
            if (!Character.isDigit(tld.charAt(0))) {
                isIP4 = false;
                break;
            }
        }
        return isIP4;
    }

    private static boolean acceptableCountryWildcard(final String cn) {

        final int cnLen = cn.length();
        if (cnLen >= 7 && cnLen <= 9) {
            // Look for the '.' in the 3rd-last position:
            if (cn.charAt(cnLen - 3) == '.') {
                // Trim off the [*.] and the [.XX].
                final String s = cn.substring(2, cnLen - 3);
                // And test against the sorted array of bad 2lds:
                final int x = Arrays.binarySearch(BAD_COUNTRY_2LDS, s);
                return x < 0;
            }
        }
        return true;
    }

    static boolean isLocalhost(String host) {

        host = host != null ? host.trim().toLowerCase() : "";
        if (host.startsWith("::1")) {
            final int x = host.lastIndexOf('%');
            if (x >= 0) {
                host = host.substring(0, x);
            }
        }
        final int x = Arrays.binarySearch(LOCALHOSTS, host);
        return x >= 0;
    }

    /**
     * Counts the number of dots "." in a string.
     *
     * @param s string to count dots from
     * @return number of dots
     */
    private static int countDots(final String s) {

        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') {
                count++;
            }
        }
        return count;
    }

    /**
     * The javax.net.ssl.HostnameVerifier contract.
     *
     * @param host    'hostname' we used to create our socket
     * @param session SSLSession with the remote server
     * @return true if the host matched the one in the certificate.
     */
    @Override
    public boolean verify(final String host, final SSLSession session) {

        try {
            final Certificate[] certs = session.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            check(new String[]{host}, x509);
            return true;
        } catch (final SSLException e) {
            return false;
        }
    }

    @Override
    public void check(final String host, final SSLSocket ssl) throws IOException {

        check(new String[]{host}, ssl);
    }

    @Override
    public void check(final String host, final X509Certificate cert) throws SSLException {

        check(new String[]{host}, cert);
    }

    @Override
    public void check(final String host, final String[] cns, final String[] subjectAlts) throws SSLException {

        check(new String[]{host}, cns, subjectAlts);
    }

    @Override
    public void check(final String[] host, final SSLSocket ssl) throws IOException {

        if (host == null) {
            throw new NullPointerException("host to verify is null");
        }

        SSLSession session = ssl.getSession();
        if (session == null) {
            // In our experience this only happens under IBM 1.4.x when
            // spurious (unrelated) certificates show up in the server'
            // chain.  Hopefully this will unearth the real problem:
            final InputStream in = ssl.getInputStream();
            in.available();
                /*
                  If you're looking at the 2 lines of code above because
                  you're running into a problem, you probably have two
                  options:
                    #1.  Clean up the certificate chain that your server
                         is presenting (e.g. edit "/etc/apache2/server.crt"
                         or wherever it is your server's certificate chain
                         is defined).
                                               OR
                    #2.   Upgrade to an IBM 1.5.x or greater JVM, or switch
                          to a non-IBM JVM.
                */

            // If ssl.getInputStream().available() didn't cause an
            // exception, maybe at least now the session is available?
            session = ssl.getSession();
            if (session == null) {
                // If it's still null, probably a startHandshake() will
                // unearth the real problem.
                ssl.startHandshake();

                // Okay, if we still haven't managed to cause an exception,
                // might as well go for the NPE.  Or maybe we're okay now?
                session = ssl.getSession();
            }
        }
        final Certificate[] certs;
        try {
            certs = session.getPeerCertificates();
        } catch (final SSLPeerUnverifiedException e) {
            final InputStream in = ssl.getInputStream();
            in.available();

            throw e;
        }
        final X509Certificate x509 = (X509Certificate) certs[0];
        check(host, x509);
    }

    @Override
    public void check(final String[] host, final X509Certificate cert) throws SSLException {

        final String[] cns = Certificates.getCNs(cert);
        final String[] subjectAlts = Certificates.getDNSSubjectAlts(cert);
        check(host, cns, subjectAlts);
    }

    public void check(final String[] hosts,
                      final String[] cns,
                      final String[] subjectAlts,
                      final boolean ie6,
                      final boolean strictWithSubDomains) throws SSLException {

        // Build the list of names we're going to check.  Our DEFAULT and
        // STRICT implementations of the HostnameVerifier only use the
        // first CN provided.  All other CNs are ignored.
        // (Firefox, wget, curl, Sun Java 1.4, 5, 6 all work this way).
        final TreeSet<String> names = new TreeSet<String>();
        if (cns != null && cns.length > 0 && cns[0] != null) {
            names.add(cns[0]);
            if (ie6) {
                names.addAll(Arrays.asList(cns).subList(1, cns.length));
            }
        }

        if (subjectAlts != null) {
            for (final String subjectAlt : subjectAlts) {
                if (subjectAlt != null) {
                    names.add(subjectAlt);
                }
            }
        }
        if (names.isEmpty()) {
            final String msg = "Certificate for " + hosts[0] + " doesn't contain CN or DNS subjectAlt";
            throw new SSLException(msg);
        }


        boolean match = false;

        for (String cn : names) {
            cn = cn.toLowerCase();
            // The CN better have at least two dots if it wants wildcard
            // action.  It also can't be [*.co.uk] or [*.co.jp] or
            // [*.org.uk], etc...
            final boolean doWildcard = cn.startsWith("*.")
                                       && cn.lastIndexOf(46) >= 0
                                       && !isIP4Address(cn)
                                       && acceptableCountryWildcard(cn);

            for (final String host : hosts) {
                final String hostName = host.trim().toLowerCase();
                if (doWildcard) {
                    match = hostName.endsWith(cn.substring(1));
                    if (match && strictWithSubDomains) {
                        // If we're in strict mode, then [*.foo.com] is not
                        // allowed to match [a.b.foo.com]
                        match = countDots(hostName) == countDots(cn);
                    }
                } else {
                    match = hostName.equals(cn);
                }

                if (match) {
                    break;
                }
            }
            if (match) {
                break;
            }

        }

        if (!match) {
            throw new SSLException("hostname in certificate didn't match: " + getHostNames(hosts) + " !=" + getCNs(names));
        }
    }
    private String getHostNames(final String[] hosts) {

        final StringBuilder buf = new StringBuilder(32);
        buf.append('<');

        for (int i = 0; i < hosts.length; ++i) {
            String h = hosts[i];
            h = h != null ? h.trim().toLowerCase() : "";
            hosts[i] = h;
            if (i > 0) {
                buf.append('/');
            }

            buf.append(h);
        }

        buf.append('>');

        return buf.toString();
    }

    private String getCNs(final TreeSet<String> names) {

        final StringBuilder buf = new StringBuilder(32);
        for (String cn : names) {
            cn = cn.toLowerCase();
            // Store CN in StringBuffer in case we need to report an error.
            buf.append(" <");
            buf.append(cn);
            buf.append('>');
            if (!names.last().equals(cn)) {
                buf.append(" OR");
            }
        }

        return buf.toString();
    }
}
