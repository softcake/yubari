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

import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HaloRequestMessage;
import com.dukascopy.dds4.transport.msg.system.HaloResponseMessage;
import com.dukascopy.dds4.transport.msg.system.LoginRequestMessage;
import com.dukascopy.dds4.transport.msg.system.OkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GreedClientAuthorizationProvider extends AbstractClientAuthorizationProvider {
    protected static final Logger LOGGER = LoggerFactory.getLogger(GreedClientAuthorizationProvider.class);
    private String login;
    private String sessionId;
    private String ticket;

    public GreedClientAuthorizationProvider(String login, String ticket, String sessionId) {
        this.login = login;
        this.sessionId = sessionId;
        this.ticket = ticket;
    }

    public void authorize(IoSessionWrapper ioSession) {
        HaloRequestMessage haloRequestMessage = new HaloRequestMessage();
        haloRequestMessage.setPingable(true);
        haloRequestMessage.setUseragent(this.getUserAgent());
        haloRequestMessage.setSecondaryConnectionDisabled(this.isSecondaryConnectionDisabled());
        haloRequestMessage.setSecondaryConnectionMessagesTTL(this.getDroppableMessageServerTTL());
        haloRequestMessage.setSessionName(this.getSessionName());
        ioSession.write(haloRequestMessage);
    }

    public void messageReceived(IoSessionWrapper ioSession, ProtocolMessage protocolMessage) {
        if (protocolMessage instanceof OkResponseMessage) {
            LOGGER.info("!!!:" + this.getListener());
            this.getListener().authorized(this.sessionId, ioSession, this.login);
        } else if (protocolMessage instanceof ErrorResponseMessage) {
            this.getListener().authorizationError(ioSession, ((ErrorResponseMessage)protocolMessage).getReason());
        } else if (protocolMessage instanceof HaloResponseMessage) {
            LoginRequestMessage loginRequestMessage = new LoginRequestMessage();
            loginRequestMessage.setUsername(this.login);
            loginRequestMessage.setTicket(this.ticket);
            loginRequestMessage.setSessionId(this.sessionId);
            ioSession.write(loginRequestMessage);
        }

    }

    public void cleanUp() {
    }

    public String getLogin() {
        return this.login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTicket() {
        return this.ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }
}
