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

package org.softcake.yubari.netty.mina;

import com.dukascopy.dds4.transport.msg.system.InvocationRequest;

import java.lang.reflect.Proxy;


public class ProxyInterfaceFactory extends AbstractRemoteInterfaceFactory {
    private Long requestId = 0L;

    private Long getNextId() {

        synchronized (this.requestId) {
            this.requestId++;
            return this.requestId;
        }
    }

    public <T> ProxyInterfaceContext<T> buildRemoteInterface(IoSessionWrapper session,
                                                             Class<T> interfaceClass,
                                                             long requestTimeout) {

        AbstractProxyInvocationHandler h = new AbstractProxyInvocationHandler(interfaceClass, session, requestTimeout) {
            public InvocationResponseReceiveFuture getInvocationFuture(InvocationRequest invocationRequest) {

                return ProxyInterfaceFactory.this.getRemoteCallSupport().addPendingRequest(invocationRequest);
            }

            public Long getNextRequestId() {

                return ProxyInterfaceFactory.this.getNextId();
            }

            public InvocationResponseReceiveFuture removeInvocationFuture(InvocationRequest invocationRequest) {

                return ProxyInterfaceFactory.this.getRemoteCallSupport().removePendingRequest(invocationRequest);
            }
        };
        T p = (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class<?>[]{interfaceClass}, h);
        return new ProxyInterfaceContext<>(p, h);
    }
}
