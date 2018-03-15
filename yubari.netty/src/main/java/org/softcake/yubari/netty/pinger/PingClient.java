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

package org.softcake.yubari.netty.pinger;


import org.softcake.yubari.netty.ProtocolVersionClientNegotiatorHandler;
import org.softcake.yubari.netty.TransportClientBuilder;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.common.mina.ssl.ClientSSLContextListener;
import com.dukascopy.dds4.transport.common.mina.ssl.SSLContextFactory;
import com.dukascopy.dds4.transport.common.protocol.binary.AbstractStaticSessionDictionary;
import com.dukascopy.dds4.transport.msg.system.PingRequestMessage;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.dukascopy.dds4.transport.netty.ProtocolEncoderDecoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.ssl.SslHandler;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLEngine;

public class PingClient {
    private final PingTarget pingTarget;
    private final ProtocolEncoderDecoder protocolEncoderDecoder;
    private final int maxMessageSizeBytes;
    private final ProtocolVersionClientNegotiatorHandler protocolVersionClientNegotiatorHandler;
    private Channel channel;
    private Bootstrap channelBootstrap;
    private final Map<String, ProtocolMessage> syncRequestsMap = new ConcurrentHashMap<>();
    private final Map<String, ProtocolMessage> syncResponsesMap = new ConcurrentHashMap<>();
    private final AtomicBoolean online = new AtomicBoolean(false);
    private final IPingClientListener clientListener;

    public PingClient(PingTarget pingTarget, AbstractStaticSessionDictionary staticSessionDictionary, IPingClientListener clientListener, int maxMessageSizeBytes) {
        this.pingTarget = pingTarget;
        this.protocolEncoderDecoder = new ProtocolEncoderDecoder("Pinger", maxMessageSizeBytes, staticSessionDictionary);
        this.maxMessageSizeBytes = maxMessageSizeBytes;
        this.clientListener = clientListener;
        this.protocolVersionClientNegotiatorHandler = new ProtocolVersionClientNegotiatorHandler();
    }

    public void ping(long timeout, byte[] pingData) throws InterruptedException, TimeoutException {
        long before = System.currentTimeMillis();
        this.connect(timeout);
        long after = System.currentTimeMillis();
        long pingTimeout = timeout - (after - before);
        if (pingTimeout > 0L) {
            PingRequestMessage pingRequestMessage = new PingRequestMessage();
            pingRequestMessage.setPingData(pingData);
            ProtocolMessage response = this.sendSynched(pingRequestMessage, pingTimeout);
            if (response == null) {
                throw new TimeoutException("No ping response from server during " + pingTimeout + " ms, for ping request " + pingRequestMessage.toString(100));
            }
        } else {
            throw new TimeoutException("Connect timed out to " + this.pingTarget + " within specified timeout " + timeout);
        }
    }

    public void connect(long connectionTimeout) throws InterruptedException, TimeoutException {
        if (this.online.get()) {
            throw new IllegalStateException("Transport is already online " + this.pingTarget);
        } else {
            NioEventLoopGroup nettyEventLoopGroup = new NioEventLoopGroup(1);
            this.channelBootstrap = new Bootstrap();
            this.channelBootstrap.group(nettyEventLoopGroup);
            this.channelBootstrap.channel(NioSocketChannel.class);
            this.channelBootstrap.handler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    if (PingClient.this.pingTarget.isUseSsl()) {
                        SSLEngine engine = SSLContextFactory.getInstance(false, new ClientSSLContextListener() {
                            public void securityException(X509Certificate[] chain, String authType, CertificateException certificateException) {
                                PingClient.this.disconnect(DisconnectReason.CERTIFICATE_EXCEPTION, certificateException);
                            }
                        }, PingClient.this.pingTarget.getAddress().getHostName()).createSSLEngine();
                        Set<String> sslProtocols = PingClient.this.pingTarget.getEnabledSslProtocols().isEmpty() ? TransportClientBuilder.DEFAULT_SSL_PROTOCOLS : PingClient.this.pingTarget.getEnabledSslProtocols();
                        engine.setUseClientMode(true);
                        engine.setEnabledProtocols((String[])sslProtocols.toArray(new String[sslProtocols.size()]));
                        List<String> enabledCipherSuites = new ArrayList(Arrays.asList(engine.getSupportedCipherSuites()));
                        Iterator iterator = enabledCipherSuites.iterator();

                        label36:
                        while(true) {
                            String cipher;
                            do {
                                if (!iterator.hasNext()) {
                                    engine.setEnabledCipherSuites((String[])enabledCipherSuites.toArray(new String[enabledCipherSuites.size()]));
                                    pipeline.addLast("ssl", new SslHandler(engine));
                                    break label36;
                                }

                                cipher = (String)iterator.next();
                            } while(!cipher.toUpperCase().contains("EXPORT") && !cipher.toUpperCase().contains("NULL") && !cipher.toUpperCase().contains("ANON") && !cipher.toUpperCase().contains("_DES_") && !cipher.toUpperCase().contains("MD5"));

                            iterator.remove();
                        }
                    }

                    pipeline.addLast("protocol_version_negotiator", PingClient.this.protocolVersionClientNegotiatorHandler);
                    pipeline.addLast("frame_handler", new LengthFieldBasedFrameDecoder(PingClient.this.maxMessageSizeBytes, 0, 4, 0, 4, true));
                    pipeline.addLast("frame_encoder", new LengthFieldPrepender(4, false));
                    pipeline.addLast("protocol_encoder_decoder", PingClient.this.protocolEncoderDecoder);
                    pipeline.addLast("handler", new ChannelInboundHandlerAdapter() {
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof ProtocolMessage) {
                                ProtocolMessage protocolMsg = (ProtocolMessage)msg;
                                String requestId = protocolMsg.getRequestId();
                                if (requestId != null) {
                                    requestId = requestId.intern();
                                    PingClient.this.syncRequestsMap.remove(requestId);
                                    PingClient.this.syncResponsesMap.put(requestId, protocolMsg);
                                    synchronized(requestId) {
                                        requestId.notifyAll();
                                    }
                                }

                            } else {
                                throw new IllegalArgumentException("Unsupported message type " + msg);
                            }
                        }

                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            PingClient.this.disconnect(DisconnectReason.CLIENT_APP_REQUEST, (Throwable)null);
                        }

                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            ctx.close();
                            PingClient.this.disconnect(DisconnectReason.EXCEPTION_CAUGHT, cause);
                        }
                    });
                }
            });
            ChannelFuture connectFuture = this.channelBootstrap.connect(this.pingTarget.getAddress());
            boolean connected = connectFuture.await(connectionTimeout);
            if (connected) {
                this.channel = connectFuture.sync().channel();
                this.online.set(true);
            } else {
                throw new TimeoutException("Connect timed out to " + this.pingTarget + " within specified timeout " + connectionTimeout);
            }
        }
    }

    public void disconnect() {
        this.disconnect(DisconnectReason.CLIENT_APP_REQUEST, (Throwable)null);
    }

    private void disconnect(DisconnectReason reason, Throwable e) {
        if (this.channel != null) {
            Channel ch = this.channel;
            this.channel = null;
            ch.close();
        }

        if (this.channelBootstrap != null) {
            Bootstrap bootstrap = this.channelBootstrap;
            this.channelBootstrap = null;
            EventLoopGroup group = bootstrap.group();
            group.shutdownGracefully();
        }

        if (this.online.compareAndSet(true, false)) {
            this.releaseAllWaiters();
            this.clientListener.disconnected(e, reason);
        }

    }

    public PingTarget getPingTarget() {
        return this.pingTarget;
    }

    public ProtocolMessage sendSynched(ProtocolMessage msg, long timeout) throws InterruptedException, TimeoutException {
        if (!this.online.get()) {
            throw new IllegalArgumentException("Transport is offline " + this.pingTarget + ", failed to send " + msg.toString(100));
        } else {
            String requestId = UUID.randomUUID().toString().intern();
            msg.setRequestId(requestId);
            this.syncRequestsMap.put(requestId, msg);
            this.channel.writeAndFlush(msg);
            synchronized(requestId) {
                requestId.wait(timeout);
            }

            ProtocolMessage response = (ProtocolMessage)this.syncResponsesMap.remove(requestId);
            if (response != null) {
                response.setRequestId((String)null);
                return response;
            } else if (!this.online.get()) {
                throw new IllegalStateException("Transport " + this.pingTarget + " is offline, failed to receive answer for " + msg.toString(100));
            } else {
                throw new TimeoutException("Response from server " + this.pingTarget + " timed out within " + timeout + " ms, on " + msg.toString(100));
            }
        }
    }

    private void releaseAllWaiters() {
        Iterator i$ = this.syncRequestsMap.keySet().iterator();

        while(i$.hasNext()) {
            String requestId = (String)i$.next();
            synchronized(requestId) {
                requestId.notifyAll();
            }
        }

        this.syncRequestsMap.clear();
    }
}
