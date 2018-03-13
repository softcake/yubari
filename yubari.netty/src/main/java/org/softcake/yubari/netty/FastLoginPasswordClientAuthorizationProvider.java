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

import com.dukascopy.dds4.common.MD5;
import com.dukascopy.dds4.transport.common.protocol.mina.IoSessionWrapper;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.TransportAuthRequestMessage;
import com.dukascopy.dds4.transport.msg.system.TransportAuthResponseMessage;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastLoginPasswordClientAuthorizationProvider extends AbstractClientAuthorizationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(FastLoginPasswordClientAuthorizationProvider.class);
    private String login;
    private String passwordMd5;
    private String sessionId;
    private final boolean hashPassword;

    public FastLoginPasswordClientAuthorizationProvider(boolean hashPassword) {
        this.hashPassword = hashPassword;
    }

    public FastLoginPasswordClientAuthorizationProvider(String login, String password) {
        this.login = login;
        this.hashPassword = true;
        this.passwordMd5 = MD5.getDigest(password);
    }

    public FastLoginPasswordClientAuthorizationProvider(String login, String password, String sessionId, boolean hashPassword) {
        this.login = login;
        this.sessionId = sessionId;
        this.passwordMd5 = hashPassword ? MD5.getDigest(password) : password;
        this.hashPassword = hashPassword;
    }

    public void authorize(final IoSessionWrapper session) {
        TransportAuthRequestMessage request = new TransportAuthRequestMessage();
        request.setUseragent(this.getUserAgent());
        request.setSecondaryConnectionEnabled(!this.isSecondaryConnectionDisabled());
        request.setDroppableMessageTTL(this.getDroppableMessageServerTTL());
        request.setSessionName(this.getSessionName());
        request.setSessionId(this.sessionId);
        request.setTicket(this.hashPassword ? MD5.getDigest(this.passwordMd5) : this.passwordMd5);
        request.setUsername(this.login);
        ChannelFuture future = (ChannelFuture)session.write(request);
        future.addListener(new GenericFutureListener<ChannelFuture>() {
            public void operationComplete(ChannelFuture future) throws Exception {
                try {
                    future.get();
                } catch (Exception var3) {
                    FastLoginPasswordClientAuthorizationProvider.LOGGER.error(var3.getMessage(), var3);
                    FastLoginPasswordClientAuthorizationProvider.this.getListener().authorizationError(session, var3.getLocalizedMessage());
                }

            }
        });
    }

    public void messageReceived(IoSessionWrapper session, ProtocolMessage message) {
        if (message instanceof TransportAuthResponseMessage) {
            TransportAuthResponseMessage response = (TransportAuthResponseMessage)message;
            if (response.isAuthorized()) {
                this.getListener().authorized(response.getSessionId(), session, this.login);
            } else {
                this.getListener().authorizationError(session, response.getReason());
            }
        } else {
            this.getListener().authorizationError(session, "Unsupported response message from server " + message);
        }

    }

    public void cleanUp() {
        super.cleanUp();
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPasswordMd5() {
        return this.passwordMd5;
    }

    public void setPasswordMd5(String passwordMd5) {
        this.passwordMd5 = passwordMd5;
    }

    public String getLogin() {
        return this.login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setPassword(String password) {
        this.passwordMd5 = MD5.getDigest(password);
    }
}
