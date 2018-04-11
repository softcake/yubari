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


import org.softcake.yubari.netty.mina.IoSessionWrapper;

import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HaloRequestMessage;
import com.dukascopy.dds4.transport.msg.system.HaloResponseMessage;
import com.dukascopy.dds4.transport.msg.system.LoginRequestMessage;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;
import io.reactivex.functions.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

public class AnonymousClientAuthorizationProvider extends AbstractClientAuthorizationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnonymousClientAuthorizationProvider.class);
    private HaloResponseMessage haloResponseMessage;

    public AnonymousClientAuthorizationProvider() {

    }

    public void authorize(final IoSessionWrapper session) {

        final HaloRequestMessage haloRequestMessage = new HaloRequestMessage();
        haloRequestMessage.setPingable(true);
        haloRequestMessage.setUseragent(this.getUserAgent());
        haloRequestMessage.setSecondaryConnectionDisabled(this.isSecondaryConnectionDisabled());
        haloRequestMessage.setSecondaryConnectionMessagesTTL(this.getDroppableMessageServerTTL());
        haloRequestMessage.setSessionName(this.getSessionName());
        final ChannelFuture future = (ChannelFuture) session.write(haloRequestMessage);
        future.addListener(getChannelFutureGenericFutureListener());
    }

    @Override
    public void authorize(final Consumer<Object> ioSession) {

    }

    public void messageReceived(final IoSessionWrapper session, final ProtocolMessage message) {

        if (message instanceof HaloResponseMessage) {
            this.haloResponseMessage = (HaloResponseMessage) message;
            final LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername("anonymous");
            loginRequestMessage.setTicket(this.haloResponseMessage.getChallenge());
            loginRequestMessage.setSessionId(this.haloResponseMessage.getSessionId());
            loginRequestMessage.setMode(-2147483648);
            final ChannelFuture future = (ChannelFuture) session.write(loginRequestMessage);
            future.addListener(getChannelFutureGenericFutureListener());
        } else if (message instanceof OkResponseMessage && this.haloResponseMessage != null) {
            this.getListener().authorized(this.haloResponseMessage.getSessionId(), "anonymous");
        } else if (message instanceof ErrorResponseMessage) {
            this.getListener().authorizationError(((ErrorResponseMessage) message).getReason());
        } else if (this.haloResponseMessage == null) {
            this.getListener().authorizationError( "No halo response message");
        }

    }

    public GenericFutureListener<ChannelFuture> getChannelFutureGenericFutureListener() {

        return channelFuture -> {

            try {
                channelFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Error occurred...", e);
                getListener().authorizationError( e.getLocalizedMessage());
            }

        };
    }

    public void cleanUp() {

        this.haloResponseMessage = null;
        super.cleanUp();
    }
}
