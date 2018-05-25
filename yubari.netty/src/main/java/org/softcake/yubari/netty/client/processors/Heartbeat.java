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

import static org.slf4j.LoggerFactory.getLogger;
import static org.softcake.yubari.netty.TransportAttributeKeys.CHANNEL_ATTACHMENT_ATTRIBUTE_KEY;

import org.softcake.yubari.netty.channel.ChannelAttachment;
import org.softcake.yubari.netty.client.TransportClientSession;
import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.stream.BlockingBinaryStream;

import com.dukascopy.dds4.ping.IPingListener;
import com.dukascopy.dds4.ping.PingManager;
import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.HeartbeatOkResponseMessage;
import com.dukascopy.dds4.transport.msg.system.HeartbeatRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelPipeline;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@ChannelHandler.Sharable
public class Heartbeat extends ChannelDuplexHandler {
    private final TransportClientSession clientSession;
    private final boolean sendCpuInfoToServer;
    private final IPingListener pingListener;
    private final long maxSubsequentPingFailedCount;
    private static final Logger LOGGER = getLogger(Heartbeat.class);


    private static final String PRIMARY = "Primary";
    private static final String CHILD = "Child";
    private final Map<String, BlockingBinaryStream> streams = new HashMap<>();
    private final ExecutorService executor;
    private boolean primaryPingStarted = false;
    private boolean childPingStarted = false;
    private Disposable primaryPing;
    private Disposable childPing;


    private final AtomicBoolean isPrimaryActiv = new AtomicBoolean(Boolean.FALSE);
    private final AtomicBoolean isChildActiv = new AtomicBoolean(Boolean.FALSE);

    public Heartbeat(final TransportClientSession clientSession) {

        this.clientSession = clientSession;
        this.sendCpuInfoToServer = clientSession.isSendCpuInfoToServer();
        this.pingListener = clientSession.getPingListener();
        this.maxSubsequentPingFailedCount = clientSession.getMaxSubsequentPingFailedCount();
        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true)
                                                                      .setNameFormat(String.format(
                                                                          "[%s] HeartbeatThread",
                                                                          this.clientSession.getTransportName()))
                                                                      .build();

        this.executor = Executors.newFixedThreadPool(1, threadFactory);
    }


    /**
     * Gets called if the {@link Channel} of the {@link ChannelHandlerContext} is active.
     *
     * @param ctx the {@link ChannelHandlerContext} hoes {@link Channel} is now active
     * @throws Exception thrown if an error occurs
     */

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

        final ChannelAttachment channelAttachment = ctx.channel()
                                                       .attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY)
                                                       .get();
        if (channelAttachment == null) {
            return;
        }
        if (channelAttachment.isPrimaryConnection()) {
            isPrimaryActiv.set(Boolean.TRUE);
            startSendPingPrimary(this.clientSession.getPrimaryConnectionPingInterval(),
                                 ctx.channel(),
                                 channelAttachment);
        } else {
            isChildActiv.set(Boolean.TRUE);
            startSendPingChild(this.clientSession.getChildConnectionPingInterval(), ctx.channel(), channelAttachment);
        }


        LOGGER.info("Channel Primary? {} State: {}",
                    channelAttachment.isPrimaryConnection(),
                    clientSession.getClientConnector()
                                 .getClientState());
        ctx.fireChannelActive();
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelInactive()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {

        final ChannelAttachment channelAttachment = ctx.channel()
                                                       .attr(CHANNEL_ATTACHMENT_ATTRIBUTE_KEY)
                                                       .get();
        if (channelAttachment == null) {
            return;
        }
        if (channelAttachment.isPrimaryConnection()) {
            isPrimaryActiv.set(Boolean.FALSE);
        } else {
            isChildActiv.set(Boolean.FALSE);
        }

        if (!isPrimaryActiv.get() && !isChildActiv.get()) {
            executor.shutdownNow();
        }
        ctx.fireChannelInactive();
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {


        ctx.fireChannelRead(msg);


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

    private Channel getChannel(final boolean isPrimary) {

        return isPrimary
               ? this.clientSession.getClientConnector()
                                   .getPrimaryChannel()
               : this.clientSession.getClientConnector()
                                   .getChildChannel();
    }

    public void stopPrimaryPing() {

        if (this.primaryPing != null && !this.primaryPing.isDisposed()) {
            this.primaryPing.dispose();
        }
    }

    public void stopChildPing() {

        if (this.childPing != null && !this.childPing.isDisposed()) {
            this.childPing.dispose();
        }
    }

    public void startSendPingPrimary(final long pingInterval,
                                     final Channel channel,
                                     final ChannelAttachment attachment) {

        if (this.primaryPing == null || this.primaryPing.isDisposed()) {
            Observable.interval(3333L, pingInterval, TimeUnit.MILLISECONDS, Schedulers.from(executor))
                      .doOnError(throwable -> {

                      })
                      .takeWhile(aLong -> channel.isActive())
                      .filter(aLong -> channel.isWritable())
                      .subscribe(new Observer<Long>() {
                          @Override
                          public void onSubscribe(final Disposable d) {

                              primaryPing = d;
                          }

                          @Override
                          public void onNext(final Long aLong) {

                              sendPingIfRequired(channel, attachment);
                          }

                          @Override
                          public void onError(final Throwable e) {

                          }

                          @Override
                          public void onComplete() {

                          }
                      });
        }


    }

    private void startSendPingChild(final long pingInterval,
                                    final Channel channel,
                                    final ChannelAttachment attachment) {

        if (this.childPing == null || this.childPing.isDisposed()) {
            Observable.interval(3333L, pingInterval, TimeUnit.MILLISECONDS, Schedulers.from(executor))
                      .doOnError(throwable -> {

                      })
                      .takeWhile(aLong -> channel.isActive())
                      .filter(aLong -> channel.isWritable())
                      .subscribe(new Observer<Long>() {
                          @Override
                          public void onSubscribe(final Disposable d) {

                              childPing = d;
                          }

                          @Override
                          public void onNext(final Long aLong) {

                              sendPingIfRequired(channel, attachment);
                          }

                          @Override
                          public void onError(final Throwable e) {

                          }

                          @Override
                          public void onComplete() {

                          }
                      });
        }
    }


    private void sendPingIfRequired(final Channel channel, final ChannelAttachment attachment) {

        final Boolean isPrimary = attachment.isPrimaryConnection();

        final long connectionPingInterval = isPrimary
                                            ? this.clientSession.getPrimaryConnectionPingInterval()
                                            : this.clientSession.getChildConnectionPingInterval();

        final long connectionPingTimeout = getConnectionPingTimeout(isPrimary);

        if (connectionPingInterval > 0L && connectionPingTimeout > 0L) {
            final boolean needToPing = needToPing(System.currentTimeMillis(), attachment, connectionPingInterval);
            if (needToPing && this.isOnline()) {
                this.sendPingRequest(connectionPingTimeout, channel, attachment);
            }
        }
    }

    private long getConnectionPingTimeout(final Boolean isPrimary) {

        return isPrimary
               ? this.clientSession.getPrimaryConnectionPingTimeout()
               : this.clientSession.getChildConnectionPingTimeout();
    }

    private boolean isOnline() {

        return clientSession.getClientConnector()
                            .isOnline();
    }


    private void sendPingRequest(final long pingTimeout, final Channel channel,
                                 final ChannelAttachment attachment
    ) {

        //final boolean isPrimary = attachment.isPrimaryConnection();
        final long startTime = System.currentTimeMillis();
        final HeartbeatRequestMessage pingRequestMessage = new HeartbeatRequestMessage();
        pingRequestMessage.setRequestTime(startTime);
        attachment.setLastPingRequestTime(startTime);


        final AtomicLong pingSocketWriteInterval = new AtomicLong(Long.MAX_VALUE);

        final Observable<Long> requestObservable = Single.just(startTime)
                                                  .doOnSuccess(aLong -> {

                                                      LOGGER.debug("[{}] Ping sent in {} channel.",
                                                                   clientSession.getTransportName(),
                                                                   (getChannelStringForLogging(attachment))
                                                                       .toUpperCase());
                                                      pingSocketWriteInterval.set(System.currentTimeMillis() - aLong);

                                                  })
                                                  .subscribeOn(Schedulers.from(executor))
                                                  .toObservable();

        this.clientSession.sendRequestAsync(pingRequestMessage, channel, pingTimeout, Boolean.TRUE, requestObservable)
                          .observeOn(Schedulers.from(executor))
                          .filter(protocolMessage -> isResponseValid(protocolMessage, attachment, startTime))
                          .cast(HeartbeatOkResponseMessage.class)
                          .doOnSubscribe(disposable -> LOGGER.debug("[{}] Sending {} connection ping request: {}",
                                                                    clientSession.getTransportName(),
                                                                    (getChannelStringForLogging(attachment))
                                                                        .toLowerCase(),
                                                                    pingRequestMessage))
                          .subscribe(message -> processResponse(channel, attachment, message, startTime,
                                                                pingTimeout, pingSocketWriteInterval.get()),
                                     e -> LOGGER.error("Error while waiting for response for message: {}",
                                                       pingRequestMessage, e));


    }

    private void processResponse(final Channel channel,
                                 final ChannelAttachment attachment,
                                 final HeartbeatOkResponseMessage message,
                                 final long startTime,
                                 final long pingTimeout,
                                 final long pingSocketWriteInterval) {

        final boolean isPrimary = attachment.isPrimaryConnection();

        final long now = System.currentTimeMillis();
        final PingManager pingManager = this.clientSession.getPingManager(isPrimary);
        try {

            final long turnOverTime = now - startTime;

            attachment.pingSuccessful();

            final Double systemCpuLoad = sendCpuInfoToServer ? pingManager.getSystemCpuLoad() : null;
            final Double processCpuLoad = sendCpuInfoToServer ? pingManager.getProcessCpuLoad() : null;
            pingManager.addPing(turnOverTime,
                                pingSocketWriteInterval,
                                systemCpuLoad,
                                processCpuLoad,
                                message.getSocketWriteInterval(),
                                message.getSystemCpuLoad(),
                                message.getProcessCpuLoad());


            if (pingListener != null) {
                pingListener.pingSucceded(turnOverTime,
                                          pingSocketWriteInterval,
                                          systemCpuLoad,
                                          processCpuLoad,
                                          message.getSocketWriteInterval(),
                                          message.getSystemCpuLoad(),
                                          message.getProcessCpuLoad());
            }


            LOGGER.debug("[{}] {} synchronization ping response received. time: {}ms message {}, ",this.clientSession.getTransportName(),
                         getChannelStringForLogging(attachment),
                         turnOverTime,
                         message);


        } finally {
            Heartbeat.this.checkPingFailed(channel, attachment, pingTimeout, now);
        }
    }


    private String getChannelStringForLogging(final ChannelAttachment attachment) {

        return attachment.isPrimaryConnection() ? PRIMARY : CHILD;
    }

    private boolean isResponseValid(final ProtocolMessage message, final ChannelAttachment attachment,
                                    final long startTime) {

        if (message instanceof HeartbeatOkResponseMessage) {
            return true;
        }
        final PingManager pingManager = this.clientSession.getPingManager(attachment.isPrimaryConnection());
        attachment.pingFailed();
        pingManager.pingFailed();
        safeNotifyPingFailed();
        LOGGER.debug("[{}] Server has returned unknown response type for ping request! Time - {}ms",this.clientSession.getTransportName(),
                     (System.currentTimeMillis() - startTime));
        return false;

    }

    private void checkPingFailed(final Channel channel,
                                 final ChannelAttachment attachment,
                                 final long pingTimeout,
                                 final long now) {

        final boolean isPrimary = attachment.isPrimaryConnection();

        if (!this.isOnline() || !this.isPingFailed(attachment, pingTimeout, now)) {
            return;
        }

      /*  channel.disconnect();
        channel.close();*/

        LOGGER.warn("[{}] {} session ping timeout {}ms. Disconnecting session...",
                    this.clientSession.getTransportName(),
                    getChannelStringForLogging(attachment),
                    getConnectionPingTimeout(isPrimary));


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
