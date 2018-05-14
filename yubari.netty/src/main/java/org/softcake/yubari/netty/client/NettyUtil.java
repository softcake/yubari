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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ConnectTimeoutException;
import io.reactivex.SingleEmitter;
import io.reactivex.functions.Function;

import java.util.concurrent.CancellationException;

public class NettyUtil {
    public static <R> ChannelFutureListener getDefaultChannelFutureListener(final SingleEmitter<R> e, final Function<ChannelFuture, R> function) {


        return new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture cf) throws Exception {

                if (cf.isSuccess() && cf.isDone()) {
                    // Completed successfully
                    e.onSuccess(function.apply(cf));
                } else if (cf.isCancelled() && cf.isDone()) {
                    // Completed by cancellation
                    e.onError(new CancellationException("cancelled before completed"));
                } else if (cf.isDone() && cf.cause() != null) {
                    // Completed with failure
                    e.onError(cf.cause());
                } else if (!cf.isDone() && !cf.isSuccess() && !cf.isCancelled() && cf.cause() == null) {
                    // Uncompleted
                    e.onError(new ConnectTimeoutException());
                } else {
                    e.onError(new Exception("Unexpected ChannelFuture state"));
                }
            }
        };
    }
}