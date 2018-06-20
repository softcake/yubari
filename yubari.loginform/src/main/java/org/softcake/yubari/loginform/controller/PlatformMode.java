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

package org.softcake.yubari.loginform.controller;

public enum PlatformMode {
    JFOREX("JFOREX"),
    STANDARD("STANDARD");

    private final String modeName;

    private PlatformMode(String modeName) {
        this.modeName = modeName;
    }

    public String getModeName() {
        return this.modeName;
    }

    public static PlatformMode fromValue(String modeName) {
        PlatformMode[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PlatformMode enumer = var1[var3];
            if (enumer.modeName.equals(modeName)) {
                return enumer;
            }
        }

        throw new IllegalArgumentException("Invalid param: " + modeName);
    }

    public static PlatformMode fromJNLPValue(String modeName) {
        String jclient = "jclient";
        String jforex = "jforex";
        if ("jclient".equalsIgnoreCase(modeName)) {
            return STANDARD;
        } else if ("jforex".equalsIgnoreCase(modeName)) {
            return JFOREX;
        } else {
            throw new IllegalArgumentException("Invalid param: " + modeName);
        }
    }

    public static PlatformMode fromValue(boolean isJForexMode) {
        return isJForexMode ? JFOREX : STANDARD;
    }

    public String toString() {
        return this.modeName;
    }
}
