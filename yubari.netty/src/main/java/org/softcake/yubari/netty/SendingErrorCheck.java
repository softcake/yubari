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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author The softcake authors
 */
final class SendingErrorCheck extends AbstractSendingCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendingErrorCheck.class);
    private static final AtomicLong LAST_SENDING_ERROR_TIME = new AtomicLong(System.nanoTime() - 100000000000L);

    SendingErrorCheck(final ChannelFuture channelFuture, final TransportClientSession clientSession) {

        super(channelFuture, clientSession);
    }

    @Override
    void process() {

        if (!channelFuture.isDone() && isTimeOutReached(LAST_SENDING_ERROR_TIME)) {
            final long executionTime = System.currentTimeMillis() - procStartTime;
            logMessage(executionTime);
            channelFuture.addListener(getMessageListener(executionTime));
        }
    }

    @Override
    void logMessage(final long executionTime) {

        LOGGER.error("[{}] Message was not sent in timeout time [{}] and is still waiting it's turn, CRITICAL "
                     + "SEND TIME: {}ms, possible network problem",
                     clientSession.getTransportName(),
                     clientSession.getSendCompletionErrorDelay(),
                     executionTime);
    }

    @Override
    ChannelFutureListener getMessageListener(final long executionTime) {

        return future -> {
            if (future.isSuccess()) {
                LOGGER.error(
                    "[{}] Message sending took {}ms, critical timeout time {}ms, possible network"
                    + " problem",
                    clientSession.getTransportName(),
                    executionTime,
                    clientSession.getSendCompletionErrorDelay());
            }
        };
    }
}
