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

import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ListeningOrderedThreadPoolExecutor extends OrderedThreadPoolExecutor implements ListeningExecutorService {
    public ListeningOrderedThreadPoolExecutor(final int corePoolSize) {

        super(corePoolSize);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize, final long keepAliveTime, final TimeUnit unit) {

        super(corePoolSize, keepAliveTime, unit);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit,
                                              final ThreadFactory threadFactory) {

        super(corePoolSize, keepAliveTime, unit, threadFactory);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final int maximumPoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final int maximumPoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit,
                                              final ThreadFactory threadFactory) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, threadFactory);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final int maximumPoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit,
                                              final ThreadFactory threadFactory,
                                              final long autoCleanUpInerval) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, threadFactory, autoCleanUpInerval);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final int maximumPoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit,
                                              final BlockingQueue<Runnable> workQueue,
                                              final ThreadFactory threadFactory) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final int maximumPoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit,
                                              final BlockingQueue<Runnable> workQueue,
                                              final ThreadFactory threadFactory,
                                              final long autoCleanUpInerval) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, autoCleanUpInerval);
    }

    public ListeningOrderedThreadPoolExecutor(final int corePoolSize,
                                              final int maximumPoolSize,
                                              final long keepAliveTime,
                                              final TimeUnit unit,
                                              final BlockingQueue<Runnable> workQueue,
                                              final ThreadFactory threadFactory,
                                              final RejectedExecutionHandler rejectedExecutionHandler,
                                              final long autoCleanUpInterval) {

        super(corePoolSize,
              maximumPoolSize,
              keepAliveTime,
              unit,
              workQueue,
              threadFactory,
              rejectedExecutionHandler,
              autoCleanUpInterval);
    }

    @Override
    public ListenableFuture<?> submit(final Runnable task) {

        return (ListenableFuture) super.submit(task);
    }

    @Override
    public <T> ListenableFuture<T> submit(final Runnable task, final T result) {

        return (ListenableFuture) super.submit(task, result);
    }

    @Override
    public <T> ListenableFuture<T> submit(final Callable<T> task) {

        return (ListenableFuture) super.submit(task);
    }

    @Override
    protected final <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {

        return (RunnableFuture) (runnable instanceof OrderedRunnable
                                 ? new ListeningOrderedThreadPoolExecutor.ListenableOrderedFutureTask((OrderedRunnable) runnable,
                                                                                                      value)
                                 : ListenableFutureTask.create(runnable, value));
    }

    @Override
    protected final <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {

        return (RunnableFuture) (callable instanceof OrderedCallable
                                 ? new ListeningOrderedThreadPoolExecutor.ListenableOrderedFutureTask((OrderedCallable) callable)
                                 : ListenableFutureTask.create(callable));
    }

    protected static class ListenableOrderedFutureTask<T> extends OrderedFutureTask<T>
        implements OrderedRunnable, ListenableFuture<T> {
        private final ExecutionList executionList = new ExecutionList();

        protected ListenableOrderedFutureTask(final OrderedCallable<T> tCallable) {

            super(tCallable);
        }

        protected ListenableOrderedFutureTask(final OrderedRunnable runnable, final T result) {

            super(runnable, result);
        }

        @Override
        public void addListener(final Runnable listener, final Executor exec) {

            this.executionList.add(listener, exec);
        }

        @Override
        protected final void done() {

            this.executionList.execute();
        }
    }
}
