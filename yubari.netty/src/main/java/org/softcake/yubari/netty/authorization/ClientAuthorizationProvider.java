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

package org.softcake.yubari.netty.authorization;


import org.softcake.yubari.netty.client.TransportClientSession;

public interface ClientAuthorizationProvider {
    void setUserAgent(String userAgent);

    void cleanUp();

    void setChildConnectionDisabled(boolean childConnectionDisabled);

    void setDroppableMessageServerTTL(long droppableMessageServerTTL);

    void setTransportClientSession(TransportClientSession session);

    void setSessionId(String sessionID);

    void setTicket(String ticket);

    void setSessionName(String sessionName);

    void setLogin(String username);
}
