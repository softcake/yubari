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

public interface SateMachineClient {
    void onIdleEnter(ClientSateConnector2.ClientState state);

    void onIdleExit(ClientSateConnector2.ClientState state);

    void onConnectingEnter(ClientSateConnector2.ClientState state);

    void onConnectingExit(ClientSateConnector2.ClientState state);

    void onSslHandshakeEnter(ClientSateConnector2.ClientState state);

    void onSslHandshakeExit(ClientSateConnector2.ClientState state);

    void onProtocolVersionNegotiationEnter(ClientSateConnector2.ClientState state);

    void onProtocolVersionNegotiationExit(ClientSateConnector2.ClientState state);

    void onAuthorizingEnter(ClientSateConnector2.ClientState state);

    void onAuthorizingExit(ClientSateConnector2.ClientState state);

    void onOnlineEnter(ClientSateConnector2.ClientState state);

    void onOnlineExit(ClientSateConnector2.ClientState state);

    void onDisconnectingEnter(ClientSateConnector2.ClientState state);

    void onDisconnectingExit(ClientSateConnector2.ClientState state);

    void onDisconnectedEnter(ClientSateConnector2.ClientState state);

    void onDisconnectedExit(ClientSateConnector2.ClientState state);

}
