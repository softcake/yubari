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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum PlatformZoomValue {
    x100(100, "100 %"),
    x110(110, "110 %"),
    x125(125, "125 %"),
    x150(150, "150 %");

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformZoomValue.class);
    private int value;
    private String displayedValue;

    private PlatformZoomValue(int value, String displayedValue) {
        this.value = value;
        this.displayedValue = displayedValue;
    }

    public String toString() {
        return this.displayedValue;
    }

    public static PlatformZoomValue fromValue(int valueParam) {
        PlatformZoomValue[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PlatformZoomValue enumer = var1[var3];
            if (enumer.value == valueParam) {
                return enumer;
            }
        }

        LOGGER.error("Invalid param: " + valueParam + ", default value (100%) will be used.");
        return x100;
    }

    public int getValue() {
        return this.value;
    }

    public double getMultiplierValue() {
        return (double)this.value / 100.0D;
    }
}
