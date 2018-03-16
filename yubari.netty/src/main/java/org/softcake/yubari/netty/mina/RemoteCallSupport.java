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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RemoteCallSupport {
    private Map<String, Object> exportedInterfaces = new ConcurrentHashMap<>();
    private Map<Class<?>, ProxyInterfaceContext<?>> interfaceCache = new HashMap<>();
    private AbstractRemoteInterfaceFactory proxyFactory;
    private Map<String, InvocationResponseReceiveFuture> pendingInvocationRequest = new ConcurrentHashMap<>();

    public RemoteCallSupport(AbstractRemoteInterfaceFactory proxyFactory) {

        this.proxyFactory = proxyFactory;
        this.proxyFactory.setRemoteCallSupport(this);
    }

    public void exportInterface(Class interfaceClass, Object interfaceImpl) {

        this.exportedInterfaces.put(interfaceClass.getCanonicalName(), interfaceImpl);
    }

    public Object getInterfaceImplementation(String className) {

        return this.exportedInterfaces.get(className);
    }

    public <T> T getInterfaceImplementation(Class<T> interfaceClass) {

        return (T) this.exportedInterfaces.get(interfaceClass.getCanonicalName());
    }

    public void setSession(IoSessionWrapper session) {

        synchronized (this.interfaceCache) {
            Iterator i$ = this.interfaceCache.values().iterator();

            while (i$.hasNext()) {
                ProxyInterfaceContext pic = (ProxyInterfaceContext) i$.next();
                pic.getHandler().setSession(session);
            }

        }
    }

    public <T> T getRemoteInterface(Class<T> remoteInterfaceClass,
                                    IoSessionWrapper session,
                                    long requestTimeout,
                                    TimeUnit requestTimeoutTimeUnit) throws IllegalArgumentException {


        synchronized (this.interfaceCache) {
            ProxyInterfaceContext<T> pi = (ProxyInterfaceContext) this.interfaceCache.get(remoteInterfaceClass);
            if (pi == null) {
                if (session == null) {
                    throw new IllegalArgumentException("Session is null");
                }

                pi = this.proxyFactory.buildRemoteInterface(session,
                                                            remoteInterfaceClass,
                                                            requestTimeoutTimeUnit.toMillis(requestTimeout));
                this.interfaceCache.put(remoteInterfaceClass, pi);
            }

            return pi.getProxyImplementation();

        }
    }

    public <T> T getRemoteInterfaceNoCache(Class<T> remoteInterfaceClass,
                                           IoSessionWrapper session,
                                           long requestTimeout,
                                           TimeUnit requestTimeoutTimeUnit) throws IllegalArgumentException {

        if (session == null) {
            throw new IllegalArgumentException("Session is null");
        } else {
            ProxyInterfaceContext<T> proxy = null;
            proxy = this.proxyFactory.buildRemoteInterface(session,
                                                           remoteInterfaceClass,
                                                           requestTimeoutTimeUnit.toMillis(requestTimeout));
            return proxy.getProxyImplementation();
        }
    }

    public InvocationResponseReceiveFuture addPendingRequest(InvocationRequest request) {

        InvocationResponseReceiveFuture irrf = new InvocationResponseReceiveFuture();
        this.pendingInvocationRequest.put(request.getRequestId(), irrf);
        return irrf;
    }

    public InvocationResponseReceiveFuture removePendingRequest(InvocationRequest request) {

        return this.pendingInvocationRequest.get(request.getRequestId());
    }

    public void invocationResultReceived(InvocationResult result) {

        InvocationResponseReceiveFuture irrf = this.pendingInvocationRequest.get(
            result.getRequestId());
        if (irrf != null) {
            synchronized (irrf) {
                irrf.setResponse(result);
                irrf.notifyAll();
            }
        }

    }
}
