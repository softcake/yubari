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

import org.softcake.yubari.netty.mina.IoSessionWrapper;

import com.dukascopy.dds4.common.MD5;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HaloRequestMessage;
import com.dukascopy.dds4.transport.msg.system.HaloResponseMessage;
import com.dukascopy.dds4.transport.msg.system.LoginRequestMessage;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginPasswordClientAuthorizationProvider extends AbstractClientAuthorizationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginPasswordClientAuthorizationProvider.class);
    private String login;
    private String passwordMd5;
    private HaloResponseMessage haloResponseMessage;

    public LoginPasswordClientAuthorizationProvider(final String login, final String password) {
        this.login = login;
        this.passwordMd5 = MD5.getDigest(password);
    }

    public void setLogin(final String login) {
        this.login = login;
    }

    public void setPassword(final String password) {
        this.passwordMd5 = MD5.getDigest(password);
    }

    public void authorize(final IoSessionWrapper session) {
        final HaloRequestMessage haloRequestMessage = new HaloRequestMessage();
        haloRequestMessage.setPingable(true);
        haloRequestMessage.setUseragent(this.getUserAgent());
        haloRequestMessage.setSecondaryConnectionDisabled(this.isSecondaryConnectionDisabled());
        haloRequestMessage.setSecondaryConnectionMessagesTTL(this.getDroppableMessageServerTTL());
        haloRequestMessage.setSessionName(this.getSessionName());
        final ChannelFuture future = (ChannelFuture)session.write(haloRequestMessage);
        future.addListener((GenericFutureListener<ChannelFuture>) future1 -> {
            try {
                future1.get();
            } catch (final Exception var3) {
                LOGGER.error(var3.getMessage(), var3);
                getListener().authorizationError(session, var3.getLocalizedMessage());
            }

        });
    }

    public void messageReceived(final IoSessionWrapper session, final ProtocolMessage message) {
        if (message instanceof HaloResponseMessage) {
            this.haloResponseMessage = (HaloResponseMessage)message;
            final String challenge = this.haloResponseMessage.getChallenge();
            final LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername(this.login);
            loginRequestMessage.setTicket(MD5.getDigest(challenge + this.passwordMd5));
            loginRequestMessage.setSessionId(this.haloResponseMessage.getSessionId());
            loginRequestMessage.setMode(-2147483648);
            final ChannelFuture future = (ChannelFuture)session.write(loginRequestMessage);
            future.addListener((GenericFutureListener<ChannelFuture>) future1 -> {
                try {
                    future1.get();
                } catch (final Exception var3) {
                    LOGGER.error(var3.getMessage(), var3);
                    getListener().authorizationError(session, var3.getLocalizedMessage());
                }

            });
        } else if (message instanceof OkResponseMessage && this.haloResponseMessage != null) {
            this.getListener().authorized(this.haloResponseMessage.getSessionId(), session, "anonymous");
        } else if (message instanceof ErrorResponseMessage) {
            this.getListener().authorizationError(session, ((ErrorResponseMessage)message).getReason());
        } else if (this.haloResponseMessage == null) {
            this.getListener().authorizationError(session, "No halo response message");
        }

    }

    public void cleanUp() {
        this.haloResponseMessage = null;
        super.cleanUp();
    }
}
