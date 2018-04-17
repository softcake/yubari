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

import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.RequestListenableFuture;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author René Neubert
 */
public class HeartbeatOkResponseProcessor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatOkResponseProcessor.class);
    private static final String PRIMARY = "Primary";
    private static final String SECONDARY = "Secondary";
    final ChannelAttachment attachment;
    private RequestListenableFuture future;
private final long startTime;
    final PingManager pingManager;
    final AtomicLong pingSocketWriteInterval;
    final IPingListener pingListener;
    final HeartbeatRequestMessage pingRequestMessage;
    final boolean isPrimary;
    final long pingTimeout;
    ClientConnector clientConnector;

    public HeartbeatOkResponseProcessor(ClientConnector clientConnector,final ChannelAttachment attachment,
                                        final RequestListenableFuture future,
                                        final long startTime,
                                        final PingManager pingManager,
                                        final AtomicLong pingSocketWriteInterval,
                                        final IPingListener pingListener,
                                        final HeartbeatRequestMessage pingRequestMessage,
                                        final boolean isPrimary,
                                        final long pingTimeout) {
this.clientConnector = clientConnector;
        this.attachment = attachment;
        this.future = future;
        this.startTime = startTime;
        this.pingManager = pingManager;
        this.pingSocketWriteInterval = pingSocketWriteInterval;
        this.pingListener = pingListener;
        this.pingRequestMessage = pingRequestMessage;
        this.isPrimary = isPrimary;
        this.pingTimeout = pingTimeout;

    }

    @Override
    public void run() {


        final long now = System.currentTimeMillis();

        try {

            final ProtocolMessage protocolMessage = future.get();

            if (protocolMessage instanceof HeartbeatOkResponseMessage) {
                final HeartbeatOkResponseMessage message = (HeartbeatOkResponseMessage) protocolMessage;
                final long turnOverTime = now - startTime;

                attachment.pingSuccessfull();

                final Double systemCpuLoad = ClientConnector.this.sendCpuInfoToServer
                                             ? pingManager.getSystemCpuLoad()
                                             : null;
                final Double processCpuLoad = ClientConnector.this.sendCpuInfoToServer
                                              ? pingManager.getProcessCpuLoad()
                                              : null;
                pingManager.addPing(turnOverTime,
                                    pingSocketWriteInterval.get(),
                                    systemCpuLoad,
                                    processCpuLoad,
                                    message.getSocketWriteInterval(),
                                    message.getSystemCpuLoad(),
                                    message.getProcessCpuLoad());


                if (pingListener != null) {
                    pingListener.pingSucceded(turnOverTime,
                                              pingSocketWriteInterval.get(),
                                              systemCpuLoad,
                                              processCpuLoad,
                                              message.getSocketWriteInterval(),
                                              message.getSystemCpuLoad(),
                                              message.getProcessCpuLoad());
                }


                LOGGER.debug("{} synchronization ping response received: {}, time: {}",
                             isPrimary ? PRIMARY : SECONDARY,
                             protocolMessage,
                             turnOverTime);

            } else {
                attachment.pingFailed();
                pingManager.pingFailed();
                safeNotifyPingFailed();

                LOGGER.debug("Server has returned unknown response type for ping request! Time - {}",
                             (now - startTime));

            }
        } catch (InterruptedException | ExecutionException e) {

            if (this.clientConnector.isOnline()) {
                attachment.pingFailed();
                pingManager.pingFailed();
                safeNotifyPingFailed();
                LOGGER.error("{} session ping failed: {}, timeout: {}, syncRequestId: {}",
                             isPrimary ? PRIMARY : SECONDARY,
                             e.getMessage(),
                             pingTimeout,
                             pingRequestMessage.getSynchRequestId());
            }
        } finally {
            checkPingFailed(isPrimary, attachment, pingTimeout, now);
        }

    }
    private void safeNotifyPingFailed() {

        if (this.pingListener != null) {
            this.pingListener.pingFailed();
        }
    }
    private static boolean isIoProcessing(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        if (attachment == null) {
            return false;
        } else {
            final long lastIoTime = attachment.getLastIoTime();

            return lastIoTime > 0L && now - lastIoTime < pingTimeout;

        }
    }


    private void checkPingFailed(final boolean isPrimary,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final long now) {

      /*  if (!this.isOnline() || !this.isPingFailed(attachment, pingTimeout, now)) {
            return;
        }

        final Channel channel = isPrimary ? this.primaryChannel : this.childChannel;
        if (channel != null) {
            channel.disconnect();
            channel.close();
        }

        LOGGER.warn("[{}] {} session ping timeout {}ms. Disconnecting session...",
                    this.clientSession.getTransportName(),
                    isPrimary ? PRIMARY : SECONDARY,
                    isPrimary
                    ? this.clientSession.getPrimaryConnectionPingTimeout()
                    : this.clientSession.getSecondaryConnectionPingTimeout());


        if (isPrimary) {

            if (this.disconnectReason == null) {
                this.disconnectReason = new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                   "Primary session ping timeout");
            }

            this.tryToSetState(ClientState.ONLINE, ClientState.DISCONNECTING, Boolean.TRUE);

            synchronized (this.stateWaitLock) {
                this.stateWaitLock.notifyAll();
            }
        }*/

    }

}
