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

package org.softcake.yubari.netty;

import java.util.UUID;

/**
 * @author The softcake authors
 */
public class SessionHandler {
    private static SessionHandler instance;
    private static Object mutex = new Object();
    private static String sessionId = UUID.randomUUID().toString();

    private SessionHandler() {

        throw new IllegalAccessError("Utility class");
    }

    public static synchronized SessionHandler getInstance() {

        SessionHandler result = instance;
        if (result == null) {
            synchronized (mutex) {
                result = instance;
                if (result == null) { instance = result = new SessionHandler(); }
            }
        }
        return result;

    }

    public static String recreateSessionId() {


        return sessionId = UUID.randomUUID().toString();
    }

    public static String getSessionId() {

        return sessionId;
    }
}
