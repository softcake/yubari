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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeSet;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

public class HostNameVerifier implements HostnameVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(HostNameVerifier.class);
    private static final String[] BAD_COUNTRY_2LDS = new String[]{"ac",
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
    private static final String[] LOCALHOSTS = new String[]{"::1", "127.0.0.1", "localhost", "localhost.localdomain"};

    static {
        Arrays.sort(BAD_COUNTRY_2LDS);
        Arrays.sort(LOCALHOSTS);
    }

    public static boolean acceptableCountryWildcard(final String cn) {

        final int cnLen = cn.length();
        if (cnLen >= 7 && cnLen <= 9 && cn.charAt(cnLen - 3) == '.') {
            final String s = cn.substring(2, cnLen - 3);
            final int x = Arrays.binarySearch(BAD_COUNTRY_2LDS, s);
            return x < 0;
        } else {
            return true;
        }
    }

    public static boolean isIP4Address(final String cn) {

        boolean isIP4 = true;
        String tld = cn;
        final int x = cn.lastIndexOf('.');
        if (x >= 0 && x + 1 < cn.length()) {
            tld = cn.substring(x + 1);
        }

        for (int i = 0; i < tld.length(); ++i) {
            if (!Character.isDigit(tld.charAt(0))) {
                isIP4 = false;
                break;
            }
        }

        return isIP4;
    }

    public static String[] getDNSSubjectAlts(final X509Certificate cert) {

        final LinkedList<String> subjectAltList = new LinkedList<>();
        Collection<List<?>> subjectAlternativeNames = null;

        try {
            subjectAlternativeNames = cert.getSubjectAlternativeNames();
        } catch (final CertificateParsingException var7) {
            LOGGER.error(var7.getMessage(), var7);

        }

        if (subjectAlternativeNames != null) {

            for (final List<?> name : subjectAlternativeNames) {
                final int type = (Integer) name.get(0);
                if (type == 2) {
                    final String s = (String) name.get(1);
                    subjectAltList.add(s);
                }
            }
        }

        return subjectAltList.toArray(new String[subjectAltList.size()]);

    }

    public boolean verify(final String host, final SSLSession session) {

        try {
            final Certificate[] certs = session.getPeerCertificates();
            final X509Certificate x509 = (X509Certificate) certs[0];
            this.check(new String[]{host}, x509);
            return true;
        } catch (final SSLException e) {
            LOGGER.error("Verification failed: ", e);
            return false;
        }
    }

    public void check(final String[] host, final X509Certificate cert) throws SSLException {

        this.check(host, this.getCNs(cert), getDNSSubjectAlts(cert));
    }

    public String getCN(final X509Certificate cert) {

        final String[] cns = this.getCNs(cert);
        return cns != null && cns.length >= 1 ? cns[0] : null;
    }

    public void check(final String[] hosts, final String[] cns, final String[] subjectAlts) throws SSLException {


        final TreeSet<String> names = new TreeSet<>();
        if (cns != null && cns.length > 0 && cns[0] != null) {
            names.add(cns[0]);
        }

        if (subjectAlts != null) {
            for (int i = 0; i < subjectAlts.length; ++i) {
                if (subjectAlts[i] != null) {
                    names.add(subjectAlts[i]);
                }
            }
        }

        if (names.isEmpty()) {
            final String msg = "Certificate for " + hosts[0] + " doesn't contain CN or DNS subjectAlt";
            throw new SSLException(msg);
        }

        final StringBuilder buf = new StringBuilder(32);
        boolean match = false;

        for (String cn : names) {
            cn = cn.toLowerCase();
            buf.append(" <");
            buf.append(cn);
            buf.append('>');
            if (!names.last().equals(cn)) {
                buf.append(" OR");
            }
            final boolean doWildcard = cn.startsWith("*.")
                                       && cn.lastIndexOf(46) >= 0
                                       && !isIP4Address(cn)
                                       && acceptableCountryWildcard(cn);

            for (final String host : hosts) {
                final String hostName = host.trim().toLowerCase();
                if (doWildcard) {
                    match = hostName.endsWith(cn.substring(1));
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
            throw new SSLException("hostname in certificate didn't match: " + getHostNames(hosts) + " !=" + buf);
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

    public String[] getCNs(final X509Certificate cert) {

        final LinkedList<String> cnList = new LinkedList<>();
        final String subjectPrincipal = cert.getSubjectX500Principal().toString();
        final StringTokenizer st = new StringTokenizer(subjectPrincipal, ",");

        while (st.hasMoreTokens()) {
            final String tok = st.nextToken();
            final int x = tok.indexOf("CN=");
            if (x >= 0) {
                cnList.add(tok.substring(x + 3));
            }
        }

        return cnList.toArray(new String[0]);
    }
}
