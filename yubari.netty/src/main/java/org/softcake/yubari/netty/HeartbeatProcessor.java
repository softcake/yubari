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

import org.softcake.yubari.netty.stream.BlockingBinaryStream;

import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.ping.PingStats;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author The softcake authors
 */
public class HeartbeatProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatProcessor.class);
    private final Map<String, BlockingBinaryStream> streams = new HashMap<>();
    private final TransportClientSession clientSession;
    private final boolean sendCpuInfoToServer;

    public HeartbeatProcessor(final TransportClientSession clientSession) {

        this.clientSession = clientSession;
        this.sendCpuInfoToServer = clientSession.isSendCpuInfoToServer();
    }

    public void process(final ChannelHandlerContext ctx,
                        final ChannelAttachment attachment,
                        final HeartbeatRequestMessage requestMessage) {

        try {
            final HeartbeatOkResponseMessage okResponseMessage = new HeartbeatOkResponseMessage();
            okResponseMessage.setRequestTime(requestMessage.getRequestTime());
            okResponseMessage.setReceiveTime(System.currentTimeMillis());
            okResponseMessage.setSynchRequestId(requestMessage.getSynchRequestId());

            final PingManager pm = this.clientSession.getPingManager(attachment.isPrimaryConnection());
            final Double systemCpuLoad = this.sendCpuInfoToServer ? pm.getSystemCpuLoad() : null;
            final Double processCpuLoad = this.sendCpuInfoToServer ? pm.getProcessCpuLoad() : null;

            okResponseMessage.setProcessCpuLoad(processCpuLoad);
            okResponseMessage.setSystemCpuLoad(systemCpuLoad);
            okResponseMessage.setAvailableProcessors(pm.getAvailableProcessors());

            PingStats generalStats = pm.getGeneralStats();
            if (generalStats != null) {
                okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval()
                                                                     .getRoundedLast());
            } else {
                generalStats = this.clientSession.getPingManager(attachment.isPrimaryConnection())
                                                 .getGeneralStats();
                if (generalStats != null) {
                    okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval()
                                                                         .getRoundedLast());
                }
            }

            if (!this.clientSession.isTerminating()) {
                clientSession.getProtocolHandler().writeMessage(ctx.channel(), okResponseMessage);
            }
        } catch (final Exception e) {
            LOGGER.error("[{}] ", clientSession.getTransportName(), e);
            final ErrorResponseMessage errorMessage = new ErrorResponseMessage(String.format(
                "Error occurred while processing the message [%s]. Error message: [%s:%s]",
                requestMessage,
                e.getClass().getName(),
                e.getMessage()));
            errorMessage.setSynchRequestId(requestMessage.getSynchRequestId());
            if (!this.clientSession.isTerminating()) {
                clientSession.getProtocolHandler().writeMessage(ctx.channel(), errorMessage);
            }
        }
    }

}
