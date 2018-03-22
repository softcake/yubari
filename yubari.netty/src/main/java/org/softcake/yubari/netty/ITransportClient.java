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


import org.softcake.yubari.netty.mina.ClientListener;
import org.softcake.yubari.netty.mina.MessageSentListener;
import org.softcake.yubari.netty.mina.RequestListenableFuture;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ITransportClient  {
    /**
     * @deprecated
     */
    @Deprecated
    void controlRequest(ProtocolMessage var1, MessageSentListener var2) throws IOException;

    /**
     * @deprecated
     */
    @Deprecated
    ProtocolMessage controlBlockingRequest(ProtocolMessage var1, Long var2)
        throws InterruptedException, IOException, TimeoutException, ExecutionException;

    /**
     * @deprecated
     */
    @Deprecated
    ProtocolMessage controlRequest(ProtocolMessage var1);

    /**
     * @deprecated
     */
    @Deprecated
    ProtocolMessage controlSynchRequest(ProtocolMessage var1, Long var2) throws TimeoutException;

    boolean sendMessageNaive(ProtocolMessage var1);

    <V> ListenableFuture<V> sendMessageAsync(ProtocolMessage var1);

    <V> void sendMessageAsync(ProtocolMessage var1, FutureCallback<V> var2);

    <V> void sendMessageAsync(ProtocolMessage var1, FutureCallback<V> var2, Executor var3);

    ProtocolMessage sendRequest(ProtocolMessage var1, long var2, TimeUnit var4)
        throws InterruptedException, TimeoutException, ConnectException, ExecutionException;

    RequestListenableFuture sendRequestAsync(ProtocolMessage var1);

    void exportInterfaceForRemoteCalls(Class<?> var1, Object var2);

    <T> T getExportedForRemoteCallsImplementation(Class<T> var1);

    <T> T getRemoteInterface(Class<T> var1, long var2, TimeUnit var4);


    List<ClientListener> getListeners();
}
