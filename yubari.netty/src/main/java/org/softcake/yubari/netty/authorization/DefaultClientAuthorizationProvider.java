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


import org.softcake.yubari.netty.AuthorizationCompletionEvent;
import org.softcake.yubari.netty.DefaultChannelWriter;
import org.softcake.yubari.netty.client.TransportClientSession;

import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HaloRequestMessage;
import com.dukascopy.dds4.transport.msg.system.HaloResponseMessage;
import com.dukascopy.dds4.transport.msg.system.LoginRequestMessage;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class DefaultClientAuthorizationProvider extends AbstractClientAuthorizationProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultClientAuthorizationProvider.class);
    private String userAgent;
    private String login;
    private String sessionId;
    private String ticket;
    private TransportClientSession session;
    private long droppableMessageServerTTL;
    private Boolean childConnectionDisabled;
    private String sessionName;

    public DefaultClientAuthorizationProvider(final String login,
                                              final String ticket,
                                              final String sessionId,
                                              final String userAgent) {

        this.login = login;
        this.sessionId = sessionId;
        this.ticket = ticket;
        this.userAgent = userAgent;
    }


    void handleAuthorization(final ChannelHandlerContext ctx) {

        LOGGER.debug("[{}] Calling authorize on authorization provider", this.session.getTransportName());

        final HaloRequestMessage haloRequestMessage = new HaloRequestMessage();
        haloRequestMessage.setPingable(true);
        haloRequestMessage.setUseragent(this.userAgent);
        haloRequestMessage.setSecondaryConnectionDisabled(childConnectionDisabled);
        haloRequestMessage.setSecondaryConnectionMessagesTTL(this.droppableMessageServerTTL);
        haloRequestMessage.setSessionName(this.sessionName);

        DefaultChannelWriter.writeMessage(this.session, ctx.channel(), haloRequestMessage)
                            .subscribe();
    }


    void processAuthorizationMessage(final ChannelHandlerContext ctx,
                                     final ProtocolMessage protocolMessage) {

        if (protocolMessage instanceof OkResponseMessage) {
            LOGGER.debug(
                "[{}] Received AUTHORIZED notification from the authorization provider. SessionId [{}], userName [{}]",
                this.session.getTransportName(),
                this.sessionId,
                this.login);

            this.session.setServerSessionId(this.sessionId);

            ctx.fireUserEventTriggered(AuthorizationCompletionEvent.success());

        } else if (protocolMessage instanceof ErrorResponseMessage) {

            final String errorReason = ((ErrorResponseMessage) protocolMessage).getReason();

            LOGGER.error("[{}] Received AUTHORIZATION_ERROR notification from the authorization provider, reason: [{}]",
                         this.session.getTransportName(), errorReason);

            ctx.fireUserEventTriggered(AuthorizationCompletionEvent.failed(new Exception(errorReason)));


        } else if (protocolMessage instanceof HaloResponseMessage) {
            final LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername(this.login);
            loginRequestMessage.setTicket(this.ticket);
            loginRequestMessage.setSessionId(this.sessionId);

            DefaultChannelWriter.writeMessage(this.session,
                                              this.session.getClientConnector()
                                                          .getPrimaryChannel(),
                                              loginRequestMessage)
                                .subscribe();
        }
    }
    @Override
    public void setTransportClientSession(final TransportClientSession session) {

        this.session = session;
    }

    @Override
    public void setSessionId(final String sessionID) {

        this.sessionId = sessionID;
    }

    @Override
    public void setTicket(final String ticket) {

        this.ticket = ticket;
    }

    @Override
    public void setLogin(final String username) {

        this.login = username;
    }


    @Override
    public void setChildConnectionDisabled(final boolean childConnectionDisabled) {

        this.childConnectionDisabled = childConnectionDisabled;
    }

    @Override
    public void setDroppableMessageServerTTL(final long droppableMessageServerTTL) {

        this.droppableMessageServerTTL = droppableMessageServerTTL;
    }


    @Override
    public void setUserAgent(final String userAgent) {

        this.userAgent = userAgent;
    }

    @Override
    public void cleanUp() {


    }

    @Override
    public void setSessionName(final String sessionName) {

        this.sessionName = sessionName;
    }
}


