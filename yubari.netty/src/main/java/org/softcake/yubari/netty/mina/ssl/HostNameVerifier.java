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

package org.softcake.yubari.netty.mina.ssl;

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

        int cnLen = cn.length();
        if (cnLen >= 7 && cnLen <= 9 && cn.charAt(cnLen - 3) == '.') {
            String s = cn.substring(2, cnLen - 3);
            int x = Arrays.binarySearch(BAD_COUNTRY_2LDS, s);
            return x < 0;
        } else {
            return true;
        }
    }

    public static boolean isIP4Address(String cn) {

        boolean isIP4 = true;
        String tld = cn;
        int x = cn.lastIndexOf('.');
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

    public static String[] getDNSSubjectAlts(X509Certificate cert) {

        LinkedList<String> subjectAltList = new LinkedList<>();
        Collection<List<?>> subjectAlternativeNames = null;

        try {
            subjectAlternativeNames = cert.getSubjectAlternativeNames();
        } catch (CertificateParsingException var7) {
            LOGGER.error(var7.getMessage(), var7);

        }

        if (subjectAlternativeNames != null) {

            for (final List<?> name : subjectAlternativeNames) {
                int type = (Integer) name.get(0);
                if (type == 2) {
                    String s = (String) name.get(1);
                    subjectAltList.add(s);
                }
            }
        }

        return subjectAltList.toArray(new String[subjectAltList.size()]);

    }

    public boolean verify(String host, SSLSession session) {

        try {
            Certificate[] certs = session.getPeerCertificates();
            X509Certificate x509 = (X509Certificate) certs[0];
            this.check(new String[]{host}, x509);
            return true;
        } catch (SSLException e) {
            LOGGER.error("Verification failed: ", e);
            return false;
        }
    }

    public void check(String[] host, X509Certificate cert) throws SSLException {

        this.check(host, this.getCNs(cert), getDNSSubjectAlts(cert));
    }

    public String getCN(X509Certificate cert) {

        String[] cns = this.getCNs(cert);
        return cns != null && cns.length >= 1 ? cns[0] : null;
    }

    public void check(String[] hosts, String[] cns, String[] subjectAlts) throws SSLException {


        TreeSet<String> names = new TreeSet<>();
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
            String msg = "Certificate for " + hosts[0] + " doesn't contain CN or DNS subjectAlt";
            throw new SSLException(msg);
        }

        StringBuilder buf = new StringBuilder(32);
        boolean match = false;

        for (String cn : names) {
            cn = cn.toLowerCase();
            buf.append(" <");
            buf.append(cn);
            buf.append('>');
            if (!names.last().equals(cn)) {
                buf.append(" OR");
            }
            boolean doWildcard = cn.startsWith("*.")
                                 && cn.lastIndexOf(46) >= 0
                                 && !isIP4Address(cn)
                                 && acceptableCountryWildcard(cn);

            for (int i = 0; i < hosts.length; ++i) {
                String hostName = hosts[i].trim().toLowerCase();
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

        StringBuilder buf = new StringBuilder(32);
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

    public String[] getCNs(X509Certificate cert) {

        LinkedList<String> cnList = new LinkedList<>();
        String subjectPrincipal = cert.getSubjectX500Principal().toString();
        StringTokenizer st = new StringTokenizer(subjectPrincipal, ",");

        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            int x = tok.indexOf("CN=");
            if (x >= 0) {
                cnList.add(tok.substring(x + 3));
            }
        }

        return cnList.toArray(new String[cnList.size()]);
    }
}
