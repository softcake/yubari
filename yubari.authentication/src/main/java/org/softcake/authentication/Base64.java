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

package org.softcake.authentication;

import java.io.ByteArrayOutputStream;

public class Base64 {


    private static final String BASE_64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private Base64() {

    }

    public static byte[] decode(String encoded) {

        byte[] output = new byte[3];
        ByteArrayOutputStream data = new ByteArrayOutputStream(encoded.length());
        int state = 1;

        int i;
        for (i = 0; i < encoded.length(); ++i) {
            char alpha = encoded.charAt(i);
            if (!Character.isWhitespace(alpha)) {
                byte c;
                if (alpha >= 65 && alpha <= 90) {
                    c = (byte) (alpha - 65);
                } else if (alpha >= 97 && alpha <= 122) {
                    c = (byte) (26 + (alpha - 97));
                } else if (alpha >= 48 && alpha <= 57) {
                    c = (byte) (52 + (alpha - 48));
                } else if (alpha == 43) {
                    c = 62;
                } else {
                    if (alpha != 47) {
                        if (alpha != 61) {
                            return new byte[]{};
                        }
                        break;
                    }

                    c = 63;
                }

                switch (state) {
                    case 1:
                        output[0] = (byte) (c << 2);
                        break;
                    case 2:
                        output[0] |= (byte) (c >>> 4);
                        output[1] = (byte) ((c & 15) << 4);
                        break;
                    case 3:
                        output[1] |= (byte) (c >>> 2);
                        output[2] = (byte) ((c & 3) << 6);
                        break;
                    case 4:
                        output[2] |= c;
                        data.write(output, 0, output.length);
                }

                state = state < 4 ? state + 1 : 1;
            }
        }

        if (i < encoded.length()) {
            switch (state) {
                case 3:
                    data.write(output, 0, 1);
                    return encoded.charAt(i) == 61 && encoded.charAt(i + 1) == 61 ? data.toByteArray() : null;
                case 4:
                    data.write(output, 0, 2);
                    return encoded.charAt(i) == 61 ? data.toByteArray() : null;
                default:
                    return new byte[]{};
            }
        } else {
            return state == 1 ? data.toByteArray() : new byte[]{};
        }
    }

    public static String encode(byte[] data) {

        char[] output = new char[4];
        int state = 1;
        int restbits = 0;
        int chunks = 0;
        StringBuffer encoded = new StringBuffer();

        for (int i = 0; i < data.length; ++i) {
            int ic = (data[i] >= 0) ? data[i] : ((data[i] & 127) + 128);
            switch (state) {
                case 1:
                    output[0] = BASE_64.charAt(ic >>> 2);
                    restbits = ic & 3;
                    break;
                case 2:
                    output[1] = BASE_64.charAt((restbits << 4) | (ic >>> 4));
                    restbits = ic & 15;
                    break;
                case 3:
                    output[2] = BASE_64.charAt((restbits << 2) | (ic >>> 6));
                    output[3] = BASE_64.charAt(ic & 63);
                    encoded.append(output);
                    ++chunks;
                    if (chunks % 19 == 0) {
                        encoded.append("\r\n");
                    }
            }

            state = (state < 3) ? (state + 1) : 1;
        }

        switch (state) {
            case 2:
                output[1] = BASE_64.charAt(restbits << 4);
                output[2] = output[3] = 61;
                encoded.append(output);
                break;
            case 3:
                output[2] = BASE_64.charAt(restbits << 2);
                output[3] = 61;
                encoded.append(output);
        }

        return encoded.toString();
    }
}
