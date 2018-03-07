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

package org.softcake.yubari.connect.authorization;

/**
 * The Client Mode of Dukascopy platform.
 *
 * @author René Neubert
 */
public enum ClientMode {

    DEMO("DEMO"),
    LIVE("LIVE");

    private final String value;

    ClientMode(final String value) {

        this.value = value;
    }

    /**
     * The String representation of the Client Mode Enum.
     *
     * @return the Client Mode as String
     */
    public String getValue() {

        return value;
    }
}
