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

import org.softcake.yubari.netty.client.TransportClientSession;
import org.softcake.yubari.netty.stream.BlockingBinaryStream;
import org.softcake.yubari.netty.stream.StreamListener;

import com.dukascopy.dds4.transport.common.protocol.binary.BinaryProtocolMessage;
import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.StreamHeaderMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.dukascopy.dds4.transport.msg.types.StreamState;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.channel.ChannelHandlerContext;
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
    private ListeningExecutorService streamProcessingExecutor;


    public StreamProcessor(final TransportClientSession clientSession,
                           final ListeningExecutorService streamProcessingExecutor) {

        this.clientSession = clientSession;
        this.streamProcessingExecutor = streamProcessingExecutor;

    }

    private void createStream(final ChannelHandlerContext ctx,
                             final StreamHeaderMessage stream) {

        this.streamProcessingExecutor.submit(() -> {

            final StreamListener streamListener = clientSession.getStreamListener();
            if (streamListener != null) {
                final BlockingBinaryStream bbs = new BlockingBinaryStream(stream.getStreamId(),
                                                                          ctx,
                                                                          clientSession.getStreamBufferSize(),
                                                                          clientSession
                                                                              .getStreamChunkProcessingTimeout());
                synchronized (streams) {
                    streams.put(stream.getStreamId(), bbs);
                }

                streamListener.handleStream(bbs);
            }
        });
    }

    private void streamPartReceived(final ChannelHandlerContext ctx,
                                   final BinaryPartMessage binaryPart) {

        this.streamProcessingExecutor.submit(() -> {

            final BlockingBinaryStream stream = getStream(binaryPart.getStreamId());
            if (stream == null) {
                final StreamingStatus ss = new StreamingStatus();
                ss.setStreamId(binaryPart.getStreamId());
                ss.setState(StreamState.STATE_ERROR);
                clientSession.getProtocolHandler().writeMessage(ctx.channel(), ss).subscribe();
            } else {
                boolean terminated = false;

                try {
                    stream.binaryPartReceived(binaryPart);
                } catch (final IOException var6) {
                    LOGGER.error(var6.getMessage(), var6);

                    terminated = true;
                    stream.ioTerminate(var6.getMessage());
                }

                if (binaryPart.isEof() || terminated) {
                    synchronized (streams) {
                        streams.remove(binaryPart.getStreamId());
                    }
                }
            }

        });
    }

    private void streamStatusReceived(final StreamingStatus status) {

        this.streamProcessingExecutor.submit(() -> {

            final BlockingBinaryStream bbs = getStream(status.getStreamId());
            if (bbs != null) {
                safeTerminate(bbs, "Server error " + status.getState());
                synchronized (streams) {
                    streams.remove(bbs.getStreamId());
                }
            }

        });
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
                    LOGGER.error("Failed to close stream with Id: {}" , bbs.getStreamId(), e);

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
