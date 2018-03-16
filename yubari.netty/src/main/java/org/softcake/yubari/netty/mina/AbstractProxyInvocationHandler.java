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
import com.dukascopy.dds4.transport.msg.system.JSonSerializableWrapper;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.rmi.RemoteException;

public abstract class AbstractProxyInvocationHandler implements InvocationHandler {
    private Class interfaceClass;
    private volatile IoSessionWrapper session;
    private long requestTimeout;

    public AbstractProxyInvocationHandler(Class interfaceClass, IoSessionWrapper session, long requestTimeout) {

        this.interfaceClass = interfaceClass;
        this.session = session;
        this.requestTimeout = requestTimeout;
    }

    public IoSessionWrapper getSession() {

        return this.session;
    }

    public void setSession(IoSessionWrapper session) {

        this.session = session;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        IoSessionWrapper sessionLocal = this.session;
        if (sessionLocal == null) {
            throw new IOException("Client session NULL");
        }

        if (!sessionLocal.isConnected()) {

            throw new IOException("Client session disconnected");
        }

        InvocationRequest request = new InvocationRequest();
        request.setInterfaceClass(this.interfaceClass.getCanonicalName());
        request.setParams(args);
        request.setMethodName(method.getName());
        request.setRequestId(this.getNextRequestId() + "");
        InvocationResponseReceiveFuture future = this.getInvocationFuture(request);
        JSonSerializableWrapper wrapper = new JSonSerializableWrapper();
        wrapper.setData(request);
        sessionLocal.write(wrapper);
        long start = System.currentTimeMillis();

        while (future.getResponse() == null && start + this.requestTimeout > System.currentTimeMillis()) {
            long timeToWait = this.requestTimeout - (System.currentTimeMillis() - start);
            if (timeToWait > 0L) {
                synchronized (future) {
                    if (future.getResponse() == null) {
                        future.wait(timeToWait);
                    }
                }
            }
        }

        this.removeInvocationFuture(request);

        if (future.getResponse() == null) {
            throw new IOException("No response from remote service");
        }

        if (future.getResponse().getState() == 1) {
            Throwable throwable = future.getResponse().getThrowable();
            if (throwable != null) {
                throw new RemoteException(throwable.getMessage(), throwable);
            } else {
                throw new RemoteException("Received error response without exception that was the cause for this "
                                          + "error");
            }
        }

        return future.getResponse().getState() == 0 ? future.getResponse().getResult() : null;


    }

    public abstract Long getNextRequestId();

    public abstract InvocationResponseReceiveFuture getInvocationFuture(InvocationRequest invocationRequest);

    public abstract InvocationResponseReceiveFuture removeInvocationFuture(InvocationRequest invocationRequest);
}
