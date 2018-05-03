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

package org.softcake.yubari.netty.client.processors;

import static org.softcake.yubari.netty.TransportAttributeKeys.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY;

import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.TransportClientSession;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.MessageSentListener;
import org.softcake.yubari.netty.mina.RequestListenableFuture;
import org.softcake.yubari.netty.stream.BlockingBinaryStream;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.ping.PingStats;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.ErrorResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author The softcake authors
 */
public class HeartbeatProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatProcessor.class);
    private static final String PRIMARY = "Primary";
    private static final String CHILD = "Child";
    private final Map<String, BlockingBinaryStream> streams = new HashMap<>();
    private final TransportClientSession clientSession;
    private final boolean sendCpuInfoToServer;
    private final long maxSubsequentPingFailedCount;
    private IPingListener pingListener;

    public HeartbeatProcessor(final TransportClientSession clientSession) {

        this.clientSession = clientSession;
        this.sendCpuInfoToServer = clientSession.isSendCpuInfoToServer();
        this.pingListener = clientSession.getPingListener();
        this.maxSubsequentPingFailedCount = clientSession.getMaxSubsequentPingFailedCount();
    }

    private static boolean needToPing(final long now, final ChannelAttachment attachment, final long pingInterval) {

        return !attachment.hasLastPingRequestTime() || now - attachment.getLastPingRequestTime() > pingInterval;

    }

    private static boolean isIoProcessing(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        if (attachment == null) {
            return false;
        } else {
            final long lastIoTime = attachment.getLastIoTime();

            return lastIoTime > 0L && now - lastIoTime < pingTimeout;

        }
    }

    private Channel getChannel(boolean isPrimary) {

        return isPrimary
               ? this.clientSession.getClientConnector().getPrimaryChannel()
               : this.clientSession.getClientConnector().getChildChannel();
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
                generalStats = this.clientSession.getPingManager(attachment.isPrimaryConnection()).getGeneralStats();
                if (generalStats != null) {
                    okResponseMessage.setSocketWriteInterval(generalStats.getInitiatorSocketWriteInterval()
                                                                         .getRoundedLast());
                }
            }

            if (!this.clientSession.isTerminating()) {
                clientSession.getProtocolHandler().writeMessage(ctx.channel(), okResponseMessage).subscribe();
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
                clientSession.getProtocolHandler().writeMessage(ctx.channel(), errorMessage).subscribe();
            }
        }
    }

    public void sendPing(final Boolean isPrimary) {

        sendPingIfRequired(isPrimary);
    }

    private void sendPingIfRequired(final Boolean isPrimary) {


        final Channel channel = getChannel(isPrimary);
        final long connectionPingInterval = isPrimary
                                            ? this.clientSession.getPrimaryConnectionPingInterval()
                                            : this.clientSession.getChildConnectionPingInterval();

        final long connectionPingTimeout = isPrimary
                                           ? this.clientSession.getPrimaryConnectionPingTimeout()
                                           : this.clientSession.getChildConnectionPingTimeout();
        if (channel == null || !channel.isActive()) {
            return;
        }

        if (connectionPingInterval > 0L && connectionPingTimeout > 0L) {
            final ChannelAttachment attachment = getChannelAttachment(isPrimary);
            final boolean needToPing = needToPing(System.currentTimeMillis(), attachment, connectionPingInterval);
            if (needToPing && this.isOnline()) {
                this.sendPingRequest(channel, attachment, connectionPingTimeout, isPrimary);
            }
        }
    }

    private boolean isOnline() {

        return clientSession.getClientConnector().isOnline();
    }

    private ChannelAttachment getChannelAttachment(final boolean isPrimary) {

        return isPrimary ? getPrimaryChannelAttachment() : getChildChannelAttachment();
    }

    private ChannelAttachment getPrimaryChannelAttachment() {

        return getChannel(Boolean.TRUE).attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();
    }

    private ChannelAttachment getChildChannelAttachment() {

        return getChannel(Boolean.FALSE).attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY).get();

    }

    private void sendPingRequest(final Channel channel,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final boolean isPrimary) {

        final HeartbeatRequestMessage pingRequestMessage = new HeartbeatRequestMessage();
        final long startTime = System.currentTimeMillis();
        attachment.setLastPingRequestTime(startTime);
        pingRequestMessage.setRequestTime(startTime);
        final PingManager pingManager = this.clientSession.getPingManager(isPrimary);
        final AtomicLong pingSocketWriteInterval = new AtomicLong(Long.MAX_VALUE);

        final MessageSentListener messageSentListener = new MessageSentListener() {
            @Override
            public void messageSent(final ProtocolMessage message) {

                LOGGER.debug("[{}] Ping sent in {} channel.",
                             clientSession.getTransportName(),
                             (isPrimary ? PRIMARY : CHILD).toUpperCase());
                pingSocketWriteInterval.set(System.currentTimeMillis() - startTime);
            }
        };

        Observable<ProtocolMessage> future = this.clientSession.sendRequestAsync(pingRequestMessage,
                                                                                 channel,
                                                                                 pingTimeout,
                                                                                 TimeUnit.MILLISECONDS);

        LOGGER.debug("[{}] Sending {} connection ping request: {}",
                     this.clientSession.getTransportName(),
                     (isPrimary ? PRIMARY : CHILD).toLowerCase(),
                     pingRequestMessage);
        future.doOnNext(new Consumer<ProtocolMessage>() {
            @Override
            public void accept(final ProtocolMessage protocolMessage) throws Exception {

                messageSentListener.messageSent(protocolMessage);
            }
        });


        future.observeOn(Schedulers.from(MoreExecutors.newDirectExecutorService()))
              .subscribe(new Observer<ProtocolMessage>() {
                  @Override
                  public void onSubscribe(final Disposable d) {

                  }

                  @Override
                  public void onNext(final ProtocolMessage protocolMessage) {

                      final long now = System.currentTimeMillis();

                      try {

                          if (protocolMessage instanceof HeartbeatOkResponseMessage) {
                              final HeartbeatOkResponseMessage message = (HeartbeatOkResponseMessage) protocolMessage;
                              final long turnOverTime = now - startTime;

                              attachment.pingSuccessfull();

                              final Double systemCpuLoad = sendCpuInfoToServer ? pingManager.getSystemCpuLoad() : null;
                              final Double processCpuLoad = sendCpuInfoToServer
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


                              LOGGER.debug("{} synchronization ping response received. time: {}ms message {}, ",
                                           isPrimary ? PRIMARY : CHILD,
                                           turnOverTime,
                                           protocolMessage);

                          } else {
                              attachment.pingFailed();
                              pingManager.pingFailed();
                              safeNotifyPingFailed();

                              LOGGER.debug("Server has returned unknown response type for ping request! Time - {}ms",
                                           (now - startTime));

                          }
                      } finally {
                          checkPingFailed(isPrimary, attachment, pingTimeout, now);
                      }

                  }

                  @Override
                  public void onError(final Throwable e) {

                  }

                  @Override
                  public void onComplete() {

                  }
              });


    }

  /*  private void sendPingRequestOld(final Channel channel,
                                    final ChannelAttachment attachment,
                                    final long pingTimeout,
                                    final boolean isPrimary) {

        final HeartbeatRequestMessage pingRequestMessage = new HeartbeatRequestMessage();
        final long startTime = System.currentTimeMillis();
        attachment.setLastPingRequestTime(startTime);
        pingRequestMessage.setRequestTime(startTime);
        final PingManager pingManager = this.clientSession.getPingManager(isPrimary);
        final AtomicLong pingSocketWriteInterval = new AtomicLong(Long.MAX_VALUE);

        final MessageSentListener messageSentListener = new MessageSentListener() {
            @Override
            public void messageSent(final ProtocolMessage message) {

                LOGGER.debug("[{}] Ping sent in {} channel.",
                             clientSession.getTransportName(),
                             (isPrimary ? PRIMARY : CHILD).toUpperCase());
                pingSocketWriteInterval.set(System.currentTimeMillis() - startTime);
            }
        };

        final RequestListenableFuture future = this.clientSession.sendRequestAsync(pingRequestMessage,
                                                                                   channel,
                                                                                   pingTimeout,
                                                                                   Boolean.TRUE,
                                                                                   messageSentListener);

        LOGGER.debug("[{}] Sending {} connection ping request: {}",
                     this.clientSession.getTransportName(),
                     (isPrimary ? PRIMARY : CHILD).toLowerCase(),
                     pingRequestMessage);


        final Runnable runnable = new Runnable() {
            @Override
            public void run() {


                final long now = System.currentTimeMillis();

                try {

                    final ProtocolMessage protocolMessage = future.get();

                    if (protocolMessage instanceof HeartbeatOkResponseMessage) {
                        final HeartbeatOkResponseMessage message = (HeartbeatOkResponseMessage) protocolMessage;
                        final long turnOverTime = now - startTime;

                        attachment.pingSuccessfull();

                        final Double systemCpuLoad = sendCpuInfoToServer ? pingManager.getSystemCpuLoad() : null;
                        final Double processCpuLoad = sendCpuInfoToServer ? pingManager.getProcessCpuLoad() : null;
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


                        LOGGER.debug("{} synchronization ping response received. time: {}ms message {}, ",
                                     isPrimary ? PRIMARY : CHILD,
                                     turnOverTime,
                                     protocolMessage);

                    } else {
                        attachment.pingFailed();
                        pingManager.pingFailed();
                        safeNotifyPingFailed();

                        LOGGER.debug("Server has returned unknown response type for ping request! Time - {}ms",
                                     (now - startTime));

                    }
                } catch (final InterruptedException | ExecutionException e) {

                    if (isOnline()) {
                        attachment.pingFailed();
                        pingManager.pingFailed();
                        safeNotifyPingFailed();
                        LOGGER.error("{} session ping failed: {}, timeout: {}ms, syncRequestId: {}",
                                     isPrimary ? PRIMARY : CHILD,
                                     e.getMessage(),
                                     pingTimeout,
                                     pingRequestMessage.getSynchRequestId());
                    }
                } finally {
                    checkPingFailed(isPrimary, attachment, pingTimeout, now);
                }

            }
        };

        future.addListener(runnable, MoreExecutors.newDirectExecutorService());

    }
*/
    private void checkPingFailed(final boolean isPrimary,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final long now) {

        if (!this.isOnline() || !this.isPingFailed(attachment, pingTimeout, now)) {
            return;
        }

        final Channel channel = getChannel(isPrimary);
        if (channel != null) {
            channel.disconnect();
            channel.close();
        }

        LOGGER.warn("[{}] {} session ping timeout {}ms. Disconnecting session...",
                    this.clientSession.getTransportName(),
                    isPrimary ? PRIMARY : CHILD,
                    isPrimary
                    ? this.clientSession.getPrimaryConnectionPingTimeout()
                    : this.clientSession.getChildConnectionPingTimeout());


        if (isPrimary) {
            this.clientSession.getClientConnector()
                              .disconnect(new ClientDisconnectReason(DisconnectReason.CONNECTION_PROBLEM,
                                                                     "Primary session ping timeout"));
        }

    }

    private boolean isPingFailed(final ChannelAttachment attachment, final long pingTimeout, final long now) {

        final long failedPingCount = attachment.getLastSubsequentFailedPingCount();
        final boolean ioProcessing = isIoProcessing(attachment, pingTimeout, now);
        return failedPingCount >= this.maxSubsequentPingFailedCount && !ioProcessing;

    }

    private void safeNotifyPingFailed() {

        if (this.pingListener != null) {
            this.pingListener.pingFailed();
        }
    }

}
