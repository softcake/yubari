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

import com.google.common.util.concurrent.AbstractFuture;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.GenericFutureListener;

public class MessageListenableFuture<V> extends AbstractFuture<V> {
    private ChannelFuture channelFuture;

    public MessageListenableFuture(final ChannelFuture channelFuture) {
        this.channelFuture = channelFuture;
        this.addChannelListener();
    }

    protected boolean set(final V value) {
        final boolean result = super.set(value);
        this.cleanup(false);
        return result;
    }

    protected boolean setException(final Throwable throwable) {
        final boolean result = super.setException(throwable);
        this.cleanup(false);
        return result;
    }

    public boolean cancel(final boolean mayInterruptIfRunning) {
        final boolean result = super.cancel(mayInterruptIfRunning);
        this.cleanup(mayInterruptIfRunning);
        return result;
    }

    private void cleanup(final boolean mayInterruptIfRunning) {
        final ChannelFuture localChannelFuture = this.channelFuture;
        if (localChannelFuture != null) {
            if (!localChannelFuture.isDone()) {
                localChannelFuture.cancel(mayInterruptIfRunning);
            }

            this.channelFuture = null;
        }

    }

    private void addChannelListener() {
        this.channelFuture.addListener(new GenericFutureListener<ChannelFuture>() {
            public void operationComplete(final ChannelFuture future) throws Exception {
                MessageListenableFuture.this.channelFuture = null;
                if (future.isSuccess()) {
                    MessageListenableFuture.this.set(null);
                } else if (future.isCancelled()) {
                    if (!MessageListenableFuture.this.isCancelled()) {
                        MessageListenableFuture.this.cancel(false);
                    }
                } else if (future.isDone() && future.cause() != null) {
                    MessageListenableFuture.this.setException(future.cause());
                } else {
                    MessageListenableFuture.this.setException(new Exception("Unexpected future state"));
                }

            }
        });
    }
}
