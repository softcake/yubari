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


import org.softcake.yubari.netty.AuthorizationProviderListener;
import org.softcake.yubari.netty.mina.IoSessionWrapper;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.reactivex.functions.Consumer;

public interface ClientAuthorizationProvider {
    void setUserAgent(String var1);

    void authorize(IoSessionWrapper var1);
   void authorize(final Consumer<Object> ioSession);
    void messageReceived(IoSessionWrapper var1, ProtocolMessage var2);

    void cleanUp();

    void setChildConnectionDisabled(boolean var1);

    void setDroppableMessageServerTTL(long var1);

    void setSessionName(String var1);

    void setListener(AuthorizationProviderListener var1);
}
