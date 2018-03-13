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

package org.softcake.yubari.netty.util;

import java.lang.Character.UnicodeBlock;

public final class StrUtils {
    public StrUtils() {
    }

    public static Long str2Long(String s) {
        if (isNullOrEmpty(s)) {
            return null;
        } else {
            Long ret = null;

            try {
                ret = Long.parseLong(s);
            } catch (NumberFormatException var3) {
                ;
            }

            return ret;
        }
    }

    public static Integer str2Int(String s) {
        if (isNullOrEmpty(s)) {
            return null;
        } else {
            Integer ret = null;

            try {
                ret = Integer.parseInt(s);
            } catch (NumberFormatException var3) {
                ;
            }

            return ret;
        }
    }

    public static String trim(String s, int numChars) {
        if (isNullOrEmpty(s)) {
            return null;
        } else {
            return s.length() <= numChars ? s : s.substring(0, numChars);
        }
    }

    public static boolean isNullOrEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static String firstNonEmpty(String first, String second) {
        return !isNullOrEmpty(first) ? first : checkNotEmpty(second);
    }

    public static String checkNotEmpty(String s) {
        if (isNullOrEmpty(s)) {
            throw new IllegalArgumentException("String is empty");
        } else {
            return s;
        }
    }

    public static boolean isUTF(String s) {
        if (!isNullOrEmpty(s)) {
            char[] var1 = s.trim().toCharArray();
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                char c = var1[var3];
                if (UnicodeBlock.of(c) != UnicodeBlock.BASIC_LATIN) {
                    return true;
                }
            }
        }

        return false;
    }

    public static String toSafeString(Object o, int maxLength) {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength of wrong value " + maxLength);
        } else {
            String result;
            if (o == null) {
                result = "null";
            } else {
                result = o.toString();
            }

            String maxLengthResult = maxLength > result.length() ? result : result.substring(0, maxLength);
            return maxLengthResult;
        }
    }
}
