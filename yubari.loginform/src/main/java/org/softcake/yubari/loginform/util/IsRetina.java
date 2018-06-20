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

package org.softcake.yubari.loginform.util;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

public class IsRetina {
    public static final boolean isRetina = isRetina();

    public IsRetina() {
    }

    private static boolean isRetina() {
        if (isJavaVersionAtLeast("1.7.0_40") && isOracleJvm()) {
            GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice device = env.getDefaultScreenDevice();

            try {
                Field field = device.getClass().getDeclaredField("scale");
                if (field != null) {
                    field.setAccessible(true);
                    Object scale = field.get(device);
                    if (scale instanceof Integer && (Integer)scale == 2) {
                        return true;
                    }
                }
            } catch (Exception var4) {
                ;
            }
        }

        return false;
    }

    private static boolean isJavaVersionAtLeast(String v) {
        return compareVersionNumbers(System.getProperty("java.runtime.version"), v) >= 0;
    }

    private static int compareVersionNumbers(String v1, String v2) {
        if (v1 == null && v2 == null) {
            return 0;
        } else if (v1 == null) {
            return -1;
        } else if (v2 == null) {
            return 1;
        } else {
            String[] part1 = v1.split("[\\.\\_\\-]");
            String[] part2 = v2.split("[\\.\\_\\-]");

            int idx;
            for(idx = 0; idx < part1.length && idx < part2.length; ++idx) {
                String p1 = part1[idx];
                String p2 = part2[idx];
                int cmp;
                if (p1.matches("\\d+") && p2.matches("\\d+")) {
                    cmp = (new Integer(p1)).compareTo(new Integer(p2));
                } else {
                    cmp = part1[idx].compareTo(part2[idx]);
                }

                if (cmp != 0) {
                    return cmp;
                }
            }

            if (part1.length == part2.length) {
                return 0;
            } else {
                boolean left = part1.length > idx;

                for(String[] parts = left ? part1 : part2; idx < parts.length; ++idx) {
                    String p = parts[idx];
                    int cmp;
                    if (p.matches("\\d+")) {
                        cmp = (new Integer(p)).compareTo(0);
                    } else {
                        cmp = 1;
                    }

                    if (cmp != 0) {
                        return left ? cmp : -cmp;
                    }
                }

                return 0;
            }
        }
    }

    private static boolean isOracleJvm() {
        String vendor = System.getProperty("java.vm.vendor");
        return vendor != null && vendor.toLowerCase().indexOf("oracle") >= 0;
    }
}
