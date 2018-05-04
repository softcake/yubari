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


import org.softcake.yubari.netty.mina.ClientListener;

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import io.reactivex.Observable;
import io.reactivex.Single;

import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ITransportClient  {


    boolean sendMessageNaive(ProtocolMessage var1);

   Single<Boolean> sendMessageAsync(ProtocolMessage var1);

  //  <V> void sendMessageAsync(ProtocolMessage var1, FutureCallback<V> var2);

 //   <V> void sendMessageAsync(ProtocolMessage var1, FutureCallback<V> var2, Executor var3);

    ProtocolMessage sendRequest(ProtocolMessage var1, long var2, TimeUnit var4)
        throws InterruptedException, TimeoutException, ConnectException, ExecutionException;

    //RequestListenableFuture sendRequestAsync(ProtocolMessage var1);
    Observable<ProtocolMessage> sendRequestAsync(ProtocolMessage var1);



    List<ClientListener> getListeners();
}
