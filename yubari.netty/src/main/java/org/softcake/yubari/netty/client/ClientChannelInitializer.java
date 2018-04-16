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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;

import java.util.Arrays;
import java.util.Set;

public class ClientChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final Set<String> enabledSslProtocols;
    private boolean useSSL;

    public ClientChannelInitializer(final Set<String> enabledSslProtocols, final boolean useSSL) {
        this.enabledSslProtocols = enabledSslProtocols;
        this.useSSL = useSSL;
    }

    @Override
    protected void initChannel(final SocketChannel ch) throws Exception {
        final ChannelPipeline pipeline = ch.pipeline();
        if (useSSL) {
            final Set<String> sslProtocols = enabledSslProtocols == null || enabledSslProtocols.isEmpty()
                                             ? TransportClientBuilder.DEFAULT_SSL_PROTOCOLS
                                             : enabledSslProtocols;
          /*  final SSLEngine engine = SSLContextFactory.getInstance(false,
                                                                   TransportClientSession.this,
                                                                   address.getHostName()).createSSLEngine();
                   *//* SSLContext sslcontext = SSLContext.getInstance("TLS");
                    JdkSslContext jdkSslContext = new JdkSslContext(sslcontext,false, null);*//*

            engine.setUseClientMode(true);
            engine.setEnabledProtocols(sslProtocols.toArray(new String[0]));
            engine.setEnabledCipherSuites(cleanUpCipherSuites(engine.getSupportedCipherSuites()));
            pipeline.addLast("ssl", new SslHandler(engine));*/


        }

       /* pipeline.addLast("protocol_version_negotiator", protocolVersionClientNegotiatorHandler);
        pipeline.addLast("frame_handler",
                         new LengthFieldBasedFrameDecoder(maxMessageSizeBytes, 0, 4, 0, 4, true));
        pipeline.addLast("frame_encoder", new LengthFieldPrepender(4, false));
        pipeline.addLast("protocol_encoder_decoder", protocolEncoderDecoder);
        pipeline.addLast("traffic_blocker", channelTrafficBlocker);
        pipeline.addLast("handler", protocolHandler);*/
    }
    private String[] cleanUpCipherSuites(final String[] enabledCipherSuites) {

        return Arrays.stream(enabledCipherSuites).filter(cipher -> !cipher.toUpperCase()
                                                                          .contains("EXPORT")
                                                                   && !cipher.toUpperCase()
                                                                             .contains("NULL")
                                                                   && !cipher.toUpperCase()
                                                                             .contains("ANON")
                                                                   && !cipher.toUpperCase()
                                                                             .contains("_DES_")
                                                                   && !cipher.toUpperCase()
                                                                             .contains("MD5")).toArray(String[]::new);


    }
}
