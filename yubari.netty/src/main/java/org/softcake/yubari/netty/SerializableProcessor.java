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

import org.softcake.yubari.netty.mina.ClientDisconnectReason;
import org.softcake.yubari.netty.mina.InvocationResult;
import org.softcake.yubari.netty.mina.TransportHelper;
import org.softcake.yubari.netty.stream.BlockingBinaryStream;

import com.dukascopy.dds4.transport.common.mina.DisconnectReason;
import com.dukascopy.dds4.transport.msg.system.InvocationRequest;
import com.dukascopy.dds4.transport.msg.system.JSonSerializableWrapper;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author The softcake authors
 */
public class SerializableProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SerializableProcessor.class);
    private final Map<String, BlockingBinaryStream> streams = new HashMap<>();
    private final TransportClientSession clientSession;
    private ListeningExecutorService eventExecutor;


    public SerializableProcessor(final TransportClientSession clientSession,
                                 final ListeningExecutorService eventExecutor) {

        this.clientSession = clientSession;
        this.eventExecutor = eventExecutor;

    }

    public void process(final ChannelHandlerContext ctx,
                        final JSonSerializableWrapper jSonSerializableWrapper) {

        final Serializable data = jSonSerializableWrapper.getData();
        if (data == null) {return;}

        if (data instanceof InvocationResult) {
            final InvocationResult invocationResult = (InvocationResult) data;
            this.clientSession.getRemoteCallSupport().invocationResultReceived(invocationResult);
        }

        if (data instanceof InvocationRequest) {
            final InvocationRequest invocationRequest = (InvocationRequest) data;

            final AbstractEventExecutorChannelTask task = new AbstractEventExecutorChannelTask(ctx,
                                                                                               this.clientSession,
                                                                                               invocationRequest,
                                                                                               0L) {
                @Override
                public void run() {

                    try {
                        final JSonSerializableWrapper responseMessage = new JSonSerializableWrapper();

                        InvocationResult result = getInvocationResult(invocationRequest);
                        responseMessage.setData(result);
                        clientSession.getProtocolHandler().writeMessage(ctx.channel(), responseMessage);
                    } catch (final Exception e) {

                        LOGGER.error("[{}] ", clientSession.getTransportName(), e);
                        clientSession.getClientConnector().disconnect(new ClientDisconnectReason(DisconnectReason.EXCEPTION_CAUGHT,
                                                                              String.format(
                                                                                  "Exception caught during "
                                                                                  + "processing serialized "
                                                                                  + "request %s",
                                                                                  e.getMessage()),
                                                                              e));
                    }

                }

                @Override
                public Object getOrderKey() {

                    return clientSession.getConcurrencyPolicy().getConcurrentKey(jSonSerializableWrapper);
                }
            };
            task.executeInExecutor(this.eventExecutor, null);
        }

    }

    private InvocationResult getInvocationResult(final InvocationRequest invocationRequest) {

        final Object impl = clientSession.getRemoteCallSupport().getInterfaceImplementation(
            invocationRequest.getInterfaceClass());
        InvocationResult result;
        if (impl != null) {


            final Serializable
                invocationResult;
            try {
                invocationResult = (Serializable) TransportHelper.invokeRemoteRequest
                    (invocationRequest, impl);
                result = new InvocationResult(invocationResult, invocationRequest.getRequestId());
                result.setState(InvocationResult.STATE_OK);
            } catch (InvocationTargetException e) {
                LOGGER.error("Error occurred...", e);
                result = new InvocationResult(null, invocationRequest.getRequestId());
                result.setState(InvocationResult.STATE_ERROR);
                result.setThrowable(e.getCause());
            } catch (IllegalAccessException | NoSuchMethodException e) {
                LOGGER.error("Error occurred...", e);
                result = new InvocationResult(null, invocationRequest.getRequestId());
                result.setState(InvocationResult.STATE_ERROR);
                result.setThrowable(e);
            }


        } else {
            result = new InvocationResult(null, invocationRequest.getRequestId());
            result.setState(InvocationResult.STATE_ERROR);
            result.setThrowable(new Exception("Client does not provide interface: "
                                              + invocationRequest.getInterfaceClass()));

        }
        return result;
    }

}
