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

package org.softcake.yubari.netty.client;

import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.ssl.SecurityExceptionEvent;

import com.dukascopy.dds4.transport.msg.system.ChildSocketAuthAcceptorMessage;
import com.dukascopy.dds4.transport.msg.system.PrimarySocketAuthAcceptorMessage;
import io.netty.channel.Channel;
import io.reactivex.Observer;
import io.reactivex.functions.Consumer;

/**
 * @author René Neubert
 */
public interface IClientConnector   {
    ClientDisconnectReason getDisconnectReason();

    Channel getPrimaryChannel();

    boolean isOnline();

    Channel getChildChannel();

    void connect();

    void disconnect();

    void disconnect(ClientDisconnectReason reason);

    Consumer<SecurityExceptionEvent> observeSslSecurity();

    void terminate();

    void observe(Observer<ClientConnector.ClientState> observer);

    void primaryChannelDisconnected();

    void childChannelDisconnected();

    void sslHandshakeSuccess();

    void protocolVersionNegotiationSuccess();

    void authorizingSuccess(String sessionId, String login);

    void authorizationError(String reason);

    void setPrimarySocketAuthAcceptorMessage(PrimarySocketAuthAcceptorMessage requestMessage);

    void setChildSocketAuthAcceptorMessage(ChildSocketAuthAcceptorMessage requestMessage);

    boolean isConnecting();

}
