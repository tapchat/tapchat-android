/*
 * Copyright (C) 2014 Eric Butler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tapchatapp.android.network.ssl;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLSession;

public final class CertUtil {

    public static final String SHA1 = "SHA-1";

    private CertUtil() { }

    public static byte[] getEncodedCertificate(SSLSession session) {
        try {
            return session.getPeerCertificateChain()[0].getEncoded();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String certHash(final X509Certificate cert, String digest) {
        try {
            return certHash(cert.getEncoded(), digest);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String certHash(byte[] encoded, String digest) {
        try {
            MessageDigest md = MessageDigest.getInstance(digest);
            md.update(encoded);
            return hexString(md.digest());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String hexString(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            builder.append(String.format("%02x", data[i]));
            if (i < data.length - 1)
                builder.append(":");
        }
        return builder.toString();
    }
}
