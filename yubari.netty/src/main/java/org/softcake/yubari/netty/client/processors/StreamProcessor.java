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

import org.softcake.yubari.netty.DefaultChannelWriter;
import org.softcake.yubari.netty.client.TransportClientSession;
import org.softcake.yubari.netty.stream.BlockingBinaryStream;
import org.softcake.yubari.netty.stream.StreamListener;

import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.StreamHeaderMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.dukascopy.dds4.transport.msg.types.StreamState;
import io.netty.channel.ChannelHandlerContext;
import io.reactivex.Completable;
import io.reactivex.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author The softcake authors
 */
public class StreamProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamProcessor.class);
    private final Map<String, BlockingBinaryStream> streams = new HashMap<>();
    private final TransportClientSession clientSession;
    private final Scheduler streamProcessingExecutor;


    public StreamProcessor(final TransportClientSession clientSession,
                           final Scheduler streamProcessingExecutor) {

        this.clientSession = clientSession;
        this.streamProcessingExecutor = streamProcessingExecutor;

    }

    private void createStream(final ChannelHandlerContext ctx,
                              final StreamHeaderMessage stream) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final StreamListener streamListener = clientSession.getStreamListener();
                if (streamListener != null) {
                    final BlockingBinaryStream bbs = new BlockingBinaryStream(stream.getStreamId(),
                                                                              ctx,
                                                                              clientSession.getStreamBufferSize(),
                                                                              clientSession.getStreamChunkProcessingTimeout());
                    synchronized (streams) {
                        streams.put(stream.getStreamId(), bbs);
                    }

                    streamListener.handleStream(bbs);
                }
            }
        };
        Completable.fromRunnable(runnable).subscribe();
    }

    private void streamPartReceived(final ChannelHandlerContext ctx,
                                    final BinaryPartMessage binaryPart) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                final BlockingBinaryStream stream = getStream(binaryPart.getStreamId());
                if (stream == null) {
                    final StreamingStatus streamingStatus = new StreamingStatus();
                    streamingStatus.setStreamId(binaryPart.getStreamId());
                    streamingStatus.setState(StreamState.STATE_ERROR);
                    DefaultChannelWriter.writeMessage(clientSession, ctx.channel(), streamingStatus)
                                        .subscribe();
                } else {
                    boolean terminated = false;

                    try {
                        stream.binaryPartReceived(binaryPart);
                    } catch (final IOException e) {
                        LOGGER.error("[{}] ", clientSession.getTransportName(), e);

                        terminated = true;
                        stream.ioTerminate(e.getMessage());
                    }

                    if (binaryPart.isEof() || terminated) {
                        synchronized (streams) {
                            streams.remove(binaryPart.getStreamId());
                        }
                    }
                }
            }
        };
        Completable.fromRunnable(runnable).subscribe();

    }

    private void streamStatusReceived(final StreamingStatus status) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {

                final BlockingBinaryStream bbs = getStream(status.getStreamId());
                if (bbs != null) {
                    safeTerminate(bbs, "Server error " + status.getState());
                    synchronized (streams) {
                        streams.remove(bbs.getStreamId());
                    }
                }
            }
        };
        Completable.fromRunnable(runnable).subscribe();
    }

    public void process(final ChannelHandlerContext ctx,
                        final BinaryProtocolMessage msg) {

        if (msg instanceof BinaryPartMessage) {
            this.streamPartReceived(ctx, (BinaryPartMessage) msg);
        } else if (msg instanceof StreamHeaderMessage) {
            this.createStream(ctx, (StreamHeaderMessage) msg);
        } else if (msg instanceof StreamingStatus) {
            this.streamStatusReceived((StreamingStatus) msg);
        }
    }

    public synchronized void terminateStreams() {

        final List<String> st = new ArrayList<>(this.streams.keySet());
        st.forEach(s -> {
            final BlockingBinaryStream bbs = streams.remove(s);
            safeTerminate(bbs, "Connection error");
        });
    }

    private void safeTerminate(final BlockingBinaryStream bbs, final String reason) {

        if (bbs != null) {
            try {
                bbs.ioTerminate(reason);
            } finally {
                try {
                    bbs.close();
                } catch (final IOException e) {
                    LOGGER.error("Failed to close stream with Id: {}", bbs.getStreamId(), e);

                }

            }

        }
    }

    private BlockingBinaryStream getStream(final String streamId) {

        synchronized (this.streams) {
            return this.streams.get(streamId);

        }
    }
}
