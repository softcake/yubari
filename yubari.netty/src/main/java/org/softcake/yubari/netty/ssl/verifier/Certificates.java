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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

final class Certificates {
    private static final Logger LOGGER = LoggerFactory.getLogger(Certificates.class);

    private Certificates() {
        throw new IllegalAccessError("Utility class");
    }

    static String[] getCNs(final X509Certificate cert) {

        final List<String> cnList = new LinkedList<>();
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


    /**
     * Extracts the array of SubjectAlt DNS names from an X509Certificate.
     * Returns null if there aren't any.
     * <p/>
     * Note:  Java doesn't appear able to extract international characters
     * from the SubjectAlts.  It can only extract international characters
     * from the CN field.
     * <p/>
     * (Or maybe the version of OpenSSL I'm using to test isn't storing the
     * international characters correctly in the SubjectAlts?).
     *
     * @param cert X509Certificate
     * @return Array of SubjectALT DNS names stored in the certificate.
     */
    static String[] getDNSSubjectAlts(final X509Certificate cert) {


        Collection<List<?>> c = null;
        try {
            c = cert.getSubjectAlternativeNames();
        } catch (final CertificateParsingException e) {
            LOGGER.error("Exception while parsing subject alternative names", e);
        }

        if (c == null) {
            return new String[0];
        }

        final List<String> subjectAltList = new LinkedList<>();

        for (final List<?> name : c) {
            final int type = (Integer) name.get(0);
            // If type is 2, then we've got a dNSName
            if (type == 2) {
                final String s = (String) name.get(1);
                subjectAltList.add(s);
            }
        }


        return subjectAltList.toArray(new String[0]);

    }

    /**
     * Extracts the first CN from an X509Certificate.
     * Returns empty {@link String} if there aren't any.
     * <p/>
     * Note: Java can only extract international characters
     * from the CN field.
     * <p/>
     *
     * @param cert X509Certificate
     * @return Array of CN's stored in the certificate.
     */
    public static String getCN(final X509Certificate cert) {

        final String[] cns = getCNs(cert);
        return cns.length >= 1 ? cns[0] : "";
    }
}
