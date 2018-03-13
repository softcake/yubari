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

import com.dukascopy.dds4.transport.common.protocol.mina.IoSessionWrapper;
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

public class AnonymousClientAuthorizationProvider extends AbstractClientAuthorizationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnonymousClientAuthorizationProvider.class);
    private HaloResponseMessage haloResponseMessage;

    public AnonymousClientAuthorizationProvider() {
    }

    public void authorize(final IoSessionWrapper session) {
        HaloRequestMessage haloRequestMessage = new HaloRequestMessage();
        haloRequestMessage.setPingable(true);
        haloRequestMessage.setUseragent(this.getUserAgent());
        haloRequestMessage.setSecondaryConnectionDisabled(this.isSecondaryConnectionDisabled());
        haloRequestMessage.setSecondaryConnectionMessagesTTL(this.getDroppableMessageServerTTL());
        haloRequestMessage.setSessionName(this.getSessionName());
        ChannelFuture future = (ChannelFuture)session.write(haloRequestMessage);
        future.addListener(new GenericFutureListener<ChannelFuture>() {
            public void operationComplete(ChannelFuture future) throws Exception {
                try {
                    future.get();
                } catch (Exception var3) {
                    AnonymousClientAuthorizationProvider.LOGGER.error(var3.getMessage(), var3);
                    AnonymousClientAuthorizationProvider.this.getListener().authorizationError(session, var3.getLocalizedMessage());
                }

            }
        });
    }

    public void messageReceived(final IoSessionWrapper session, ProtocolMessage message) {
        if (message instanceof HaloResponseMessage) {
            this.haloResponseMessage = (HaloResponseMessage)message;
            LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername("anonymous");
            loginRequestMessage.setTicket(this.haloResponseMessage.getChallenge());
            loginRequestMessage.setSessionId(this.haloResponseMessage.getSessionId());
            loginRequestMessage.setMode(-2147483648);
            ChannelFuture future = (ChannelFuture)session.write(loginRequestMessage);
            future.addListener(new GenericFutureListener<ChannelFuture>() {
                public void operationComplete(ChannelFuture future) throws Exception {
                    try {
                        future.get();
                    } catch (Exception var3) {
                        AnonymousClientAuthorizationProvider.LOGGER.error(var3.getMessage(), var3);
                        AnonymousClientAuthorizationProvider.this.getListener().authorizationError(session, var3.getLocalizedMessage());
                    }

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
