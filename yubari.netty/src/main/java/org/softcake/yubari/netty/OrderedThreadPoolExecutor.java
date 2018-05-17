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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.ToIntFunction;

public class OrderedThreadPoolExecutor extends ThreadPoolExecutor {
    public static final long DEFAULT_AUTO_CLEANUP_INTERVAL = 10000L;
    private static final long DEFAULT_KEEP_ALIVE_TIME = 30L;
    private static final AtomicReference<Timer> cleanUpTimer = new AtomicReference<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderedThreadPoolExecutor.class);
    private final ReentrantReadWriteLock childExecutorsLock;
    private final ConcurrentMap<Object, OrderedThreadPoolExecutor.ChildExecutor> childExecutors;
    private RejectedExecutionHandler childRejectedExecutionHandler;

    public OrderedThreadPoolExecutor(final int corePoolSize) {

        this(corePoolSize, corePoolSize, DEFAULT_KEEP_ALIVE_TIME, TimeUnit.SECONDS, Executors.defaultThreadFactory());
    }

    public OrderedThreadPoolExecutor(final int corePoolSize, final long keepAliveTime, final TimeUnit unit) {

        this(corePoolSize, corePoolSize, keepAliveTime, unit, Executors.defaultThreadFactory());
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit,
                                     final ThreadFactory threadFactory) {

        this(corePoolSize, corePoolSize, keepAliveTime, unit, threadFactory);
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final int maximumPoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit) {

        this(corePoolSize,
             maximumPoolSize,
             keepAliveTime,
             unit,
             new LinkedBlockingQueue<>(),
             Executors.defaultThreadFactory(),
             0L);
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final int maximumPoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit,
                                     final ThreadFactory threadFactory) {

        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), threadFactory, 0L);
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final int maximumPoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit,
                                     final ThreadFactory threadFactory,
                                     final long autoCleanUpInterval) {

        this(corePoolSize,
             maximumPoolSize,
             keepAliveTime,
             unit,
             new LinkedBlockingQueue<>(),
             threadFactory,
             autoCleanUpInterval);
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final int maximumPoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit,
                                     final BlockingQueue<Runnable> workQueue,
                                     final ThreadFactory threadFactory) {

        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, 0L);
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final int maximumPoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit,
                                     final BlockingQueue<Runnable> workQueue,
                                     final ThreadFactory threadFactory,
                                     final long autoCleanUpInterval) {

        this(corePoolSize,
             maximumPoolSize,
             keepAliveTime,
             unit,
             workQueue,
             threadFactory,
             new OrderedThreadPoolExecutor.NewThreadRunsPolicy(),
             autoCleanUpInterval);
    }

    public OrderedThreadPoolExecutor(final int corePoolSize,
                                     final int maximumPoolSize,
                                     final long keepAliveTime,
                                     final TimeUnit unit,
                                     final BlockingQueue<Runnable> workQueue,
                                     final ThreadFactory threadFactory,
                                     final RejectedExecutionHandler rejectedExecutionHandler,
                                     final long autoCleanUpInerval) {

        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, rejectedExecutionHandler);
        this.childExecutorsLock = new ReentrantReadWriteLock();
        this.childExecutors = this.newChildExecutorMap();
        this.childRejectedExecutionHandler = new AbortPolicy();
        if (autoCleanUpInerval > 0L) {
            Timer timer = cleanUpTimer.get();
            if (timer == null) {
                timer = new Timer("OrderedThreadPoolExecutor CleanUp", true);
                if (!cleanUpTimer.compareAndSet(null, timer)) {
                    timer.cancel();
                    timer = cleanUpTimer.get();
                }
            }

            timer.schedule(new TimerTask() {
                @Override
                public void run() {

                    OrderedThreadPoolExecutor.this.cleanUp();
                }
            }, autoCleanUpInerval, autoCleanUpInerval);
        }

    }

    protected ConcurrentMap<Object, OrderedThreadPoolExecutor.ChildExecutor> newChildExecutorMap() {

        return new ConcurrentHashMap<>();
    }

    protected BlockingQueue<Runnable> newChildExecutorWorkQueue() {

        return new LinkedBlockingQueue<>();
    }

    protected Object getChildExecutorKey(final OrderedThreadPoolExecutor.OrderedRunnable e) {

        return e.getOrderKey();
    }

    protected Set<Object> getChildExecutorKeySet() {

        return this.childExecutors.keySet();
    }

    public RejectedExecutionHandler getChildRejectedExecutionHandler() {

        return this.childRejectedExecutionHandler;
    }

    public void setChildRejectedExecutionHandler(final RejectedExecutionHandler childRejectedExecutionHandler) {

        if (childRejectedExecutionHandler == null) {
            throw new NullPointerException();
        } else {
            this.childRejectedExecutionHandler = childRejectedExecutionHandler;
        }
    }

    public boolean removeChildExecutor(final Object key) {

        final WriteLock writeLock = this.childExecutorsLock.writeLock();
        writeLock.lock();

        boolean var4;
        try {
            final OrderedThreadPoolExecutor.ChildExecutor executor = this.childExecutors.get(key);
            var4 = executor != null && executor.isIdle() && this.childExecutors.remove(key) != null;
        } finally {
            writeLock.unlock();
        }

        return var4;
    }

    public void cleanUp() {

        final WriteLock writeLock = this.childExecutorsLock.writeLock();
        writeLock.lock();

        try {
            final Iterator iterator = this.childExecutors.entrySet().iterator();

            while (iterator.hasNext()) {
                final Entry<Object, OrderedThreadPoolExecutor.ChildExecutor> entry = (Entry) iterator.next();
                final OrderedThreadPoolExecutor.ChildExecutor executor = entry.getValue();
                if (executor != null && executor.isIdle()) {
                    iterator.remove();
                }
            }
        } finally {
            writeLock.unlock();
        }

    }

    @Override
    public void execute(final Runnable task) {

        if (!(task instanceof OrderedThreadPoolExecutor.OrderedRunnable)) {
            this.doUnorderedExecute(task);
        } else {
            final OrderedThreadPoolExecutor.OrderedRunnable r = (OrderedThreadPoolExecutor.OrderedRunnable) task;
            final ReadLock readLock = this.childExecutorsLock.readLock();
            readLock.lock();

            try {
                final OrderedThreadPoolExecutor.ChildExecutor childExecutor = this.getChildExecutor(r);
                if (childExecutor != null) {
                    childExecutor.execute(task);
                } else {
                    this.doUnorderedExecute(task);
                }
            } finally {
                readLock.unlock();
            }
        }

    }

    public int getScheduledTasksCount() {

        int size = 0;
        final int sum = this.childExecutors.values().stream().mapToInt(value -> value.isRunning() ? 1 : 0).sum();
        OrderedThreadPoolExecutor.ChildExecutor childExecutor;
        for (final Iterator i$ = this.childExecutors.values().iterator(); i$.hasNext(); size += childExecutor.size()) {
            childExecutor = (OrderedThreadPoolExecutor.ChildExecutor) i$.next();
        }

        size += this.getQueue().size();
        return size;
    }

    public int getScheduledTasksCountForKey(final Object key) {

        final ReadLock readLock = this.childExecutorsLock.readLock();
        readLock.lock();
        try {
            final OrderedThreadPoolExecutor.ChildExecutor childExecutor = this.getChildExecutor(key);
            return childExecutor == null ? 0 : childExecutor.size();
        } finally {
            readLock.unlock();
        }
    }

    public int getBlockedTasksCount() {

        int size = 0;


        final int sum = this.childExecutors.values().stream().mapToInt(new ToIntFunction<ChildExecutor>() {
            @Override
            public int applyAsInt(final ChildExecutor value) {

                return value.isRunning() ? value.size() : 0;
            }
        }).sum();


        final Iterator i$ = this.childExecutors.values().iterator();

        while (i$.hasNext()) {
            final OrderedThreadPoolExecutor.ChildExecutor
                childExecutor
                = (OrderedThreadPoolExecutor.ChildExecutor) i$.next();
            if (childExecutor.isRunning()) {
                size += childExecutor.size();
            }
        }
        LOGGER.info("getBlockedTasksCount aold:{} new: {}", size, sum);


        final ReadLock readLock = this.childExecutorsLock.readLock();
        readLock.lock();

        try {
            int corePoolSubtActiveCount = Integer.MAX_VALUE;
            for (final Runnable runnable : this.getQueue()) {
                if (runnable instanceof OrderedThreadPoolExecutor.OrderedRunnable) {
                    final OrderedThreadPoolExecutor.ChildExecutor
                        childExecutor
                        = this.getChildExecutor((OrderedThreadPoolExecutor.OrderedRunnable) runnable);
                    if (childExecutor != null) {
                        if (childExecutor.isRunning()) {
                            ++size;
                        }
                    } else {
                        if (corePoolSubtActiveCount == Integer.MAX_VALUE) {
                            corePoolSubtActiveCount = this.getCorePoolSize() - this.getActiveCount();
                        }

                        if (corePoolSubtActiveCount <= 0) {
                            ++size;
                        }
                    }
                } else {
                    if (corePoolSubtActiveCount == Integer.MAX_VALUE) {
                        corePoolSubtActiveCount = this.getCorePoolSize() - this.getActiveCount();
                    }

                    if (corePoolSubtActiveCount <= 0) {
                        ++size;
                    }
                }
            }


        } finally {
            readLock.unlock();
        }

        size += this.getQueue().size();
        return size;
    }

    protected final void doUnorderedExecute(final Runnable task) {

        super.execute(task);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(final Runnable runnable, final T value) {

        return (RunnableFuture) (runnable instanceof OrderedThreadPoolExecutor.OrderedRunnable
                                 ? new OrderedThreadPoolExecutor.OrderedFutureTask((OrderedThreadPoolExecutor
            .OrderedRunnable) runnable,
                                                                                   value)
                                 : super.newTaskFor(runnable, value));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(final Callable<T> callable) {

        return (RunnableFuture) (callable instanceof OrderedThreadPoolExecutor.OrderedCallable
                                 ? new OrderedThreadPoolExecutor.OrderedFutureTask((OrderedThreadPoolExecutor
            .OrderedCallable) callable)
                                 : super.newTaskFor(callable));
    }

    protected OrderedThreadPoolExecutor.ChildExecutor getChildExecutor(final OrderedThreadPoolExecutor
        .OrderedRunnable e) {

        final Object key = this.getChildExecutorKey(e);
        return key == null ? null : this.getChildExecutor(key);
    }

    protected OrderedThreadPoolExecutor.ChildExecutor getChildExecutor(final Object key) {

        if (key == null) {
            return null;
        } else {
            OrderedThreadPoolExecutor.ChildExecutor executor = this.childExecutors.get(key);
            if (executor == null) {
                executor = new OrderedThreadPoolExecutor.ChildExecutor();
                final OrderedThreadPoolExecutor.ChildExecutor oldExecutor = this.childExecutors.putIfAbsent(key,
                                                                                                            executor);
                if (oldExecutor != null) {
                    executor = oldExecutor;
                }
            }

            return executor;
        }
    }

    void onAfterExecute(final Runnable r, final Throwable t) {

        this.afterExecute(r, t);
    }

    public interface OrderedTask {
        Object getOrderKey();
    }

    public interface MergeableTask extends OrderedThreadPoolExecutor.OrderedTask {
        Object getMergeKey();

        void setTasksBefore(Collection<OrderedThreadPoolExecutor.MergeableTask> var1);
    }

    public interface MergeableRunnable
        extends OrderedThreadPoolExecutor.OrderedRunnable, OrderedThreadPoolExecutor.MergeableTask {}

    public interface MergeableCallable<V>
        extends OrderedThreadPoolExecutor.OrderedCallable<V>, OrderedThreadPoolExecutor.MergeableTask {}

    public interface OrderedRunnable extends Runnable, OrderedThreadPoolExecutor.OrderedTask {}

    public interface OrderedCallable<V> extends Callable<V>, OrderedThreadPoolExecutor.OrderedTask {}

    protected static class OrderedFutureTask<T> extends FutureTask<T>
        implements OrderedThreadPoolExecutor.OrderedRunnable {
        private OrderedThreadPoolExecutor.OrderedTask task;

        protected OrderedFutureTask(final OrderedThreadPoolExecutor.OrderedCallable<T> tCallable) {

            super(tCallable);
            this.task = tCallable;
        }

        protected OrderedFutureTask(final OrderedThreadPoolExecutor.OrderedRunnable runnable, final T result) {

            super(runnable, result);
            this.task = runnable;
        }

        @Override
        public Object getOrderKey() {

            return this.task.getOrderKey();
        }
    }

    private static final class NewThreadRunsPolicy implements RejectedExecutionHandler {
        private NewThreadRunsPolicy() {

        }

        @Override
        public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {

            try {
                final Thread t = new Thread(r, "Temporary task executor");
                t.start();
            } catch (final Throwable var4) {
                throw new RejectedExecutionException("Failed to start a new thread", var4);
            }
        }
    }

    protected final class ChildExecutor implements Executor, Runnable {
        private final Queue<Runnable> tasks = OrderedThreadPoolExecutor.this.newChildExecutorWorkQueue();
        private final AtomicBoolean isRunning = new AtomicBoolean();

        protected ChildExecutor() {

        }

        @Override
        public void execute(final Runnable command) {

            if (!this.tasks.offer(command)) {
                OrderedThreadPoolExecutor.this.getChildRejectedExecutionHandler().rejectedExecution(command,
                                                                                                    OrderedThreadPoolExecutor.this);
            } else {
                if (!this.isRunning.get()) {
                    OrderedThreadPoolExecutor.this.doUnorderedExecute(this);
                }

            }
        }

        public int size() {

            return this.tasks.size();
        }

        protected boolean isIdle() {

            return this.tasks.isEmpty() && !this.isRunning.get();
        }

        public boolean isRunning() {

            return this.isRunning.get();
        }

        @Override
        public void run() {

            if (this.isRunning.compareAndSet(false, true)) {
                try {
                    final Thread thread = Thread.currentThread();

                    while (true) {
                        Runnable task = this.tasks.poll();
                        if (task == null) {
                            break;
                        }

                        if (task instanceof OrderedThreadPoolExecutor.MergeableTask) {
                            ArrayList mergeableTasks;
                            Runnable nextTask;
                            for (mergeableTasks = null; (nextTask = this.tasks.peek()) != null
                                                        && nextTask instanceof OrderedThreadPoolExecutor.MergeableTask
                                                        && ((OrderedThreadPoolExecutor.MergeableTask) task)
                                                            .getMergeKey()
                                                                                                           .equals((
                                                                                                               (OrderedThreadPoolExecutor.MergeableTask) nextTask)
                                                                                                                       .getMergeKey());
                                 task = nextTask) {
                                nextTask = this.tasks.poll();
                                if (nextTask == null) {
                                    break;
                                }

                                if (mergeableTasks == null) {
                                    mergeableTasks = new ArrayList();
                                }

                                mergeableTasks.add(task);
                            }

                            if (mergeableTasks != null) {
                                ((OrderedThreadPoolExecutor.MergeableTask) task).setTasksBefore(mergeableTasks);
                            }
                        }

                        boolean ran = false;
                        OrderedThreadPoolExecutor.this.beforeExecute(thread, task);

                        try {
                            task.run();
                            ran = true;
                            OrderedThreadPoolExecutor.this.onAfterExecute(task, null);
                        } catch (final RuntimeException var8) {
                            if (!ran) {
                                OrderedThreadPoolExecutor.this.onAfterExecute(task, var8);
                            }

                            throw var8;
                        }
                    }
                } finally {
                    this.isRunning.set(false);
                }

                if (!this.isRunning.get() && this.tasks.peek() != null) {
                    OrderedThreadPoolExecutor.this.doUnorderedExecute(this);
                }
            }

        }
    }
}
