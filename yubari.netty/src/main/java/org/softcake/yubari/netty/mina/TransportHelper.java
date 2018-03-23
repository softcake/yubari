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

import com.dukascopy.dds4.common.orderedExecutor.ListeningOrderedThreadPoolExecutor;
import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolDecoder;
import com.dukascopy.dds4.transport.common.protocol.binary.SessionProtocolEncoder;
import com.dukascopy.dds4.transport.msg.system.InvocationRequest;
import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TransportHelper {
    private static final int PROTOCOL_VERSION = 4;
    private static final int TIME_MARK_RECORD_LENGTH = 9;
    private static final byte TIME_MARK_CLIENT_TO_API = 0;
    private static final byte TIME_MARK_API_FROM_CLIENT = 1;
    private static final byte TIME_MARK_API_TO_CUST = 2;
    private static final byte TIME_MARK_CUST_FROM_API = 3;
    private static final byte TIME_MARK_CUST_TO_ROUTER = 4;
    private static final byte TIME_MARK_ROUTER_FROM_CUST = 5;
    private static final byte TIME_MARK_ROUTER_TO_CUST = 6;
    private static final byte TIME_MARK_CUST_FROM_ROUTER = 7;
    private static final byte TIME_MARK_CUST_TO_API = 8;
    private static final byte TIME_MARK_API_FROM_CUST = 9;
    private static final byte TIME_MARK_API_TO_CLIENT = 10;
    private static final byte TIME_MARK_CLIENT_FROM_API = 11;

    public TransportHelper() {

    }

    public static Object invokeRemoteRequest(final InvocationRequest request, final Object target)
        throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {


        Class[] paramClasses = new Class[0];
        if (request.getParams() != null) {
            paramClasses = new Class[request.getParams().length];

            for (int i = 0; i < request.getParams().length; ++i) {
                if (request.getParams()[i] != null) {
                    paramClasses[i] = request.getParams()[i].getClass();
                } else {
                    paramClasses[i] = null;
                }
            }
        }


        final List<Method> equalsMethods = new ArrayList<>();
        Method m = null;
        final Method[] methods = target.getClass().getMethods();


        for (int i = 0; i < methods.length; ++i) {
            final Method method = methods[i];
            if (method.getName().equals(request.getMethodName())
                && method.getParameterTypes().length == paramClasses.length) {
                equalsMethods.add(method);
            }
        }

        if (!equalsMethods.isEmpty() && equalsMethods.size() < 2) {
            m = equalsMethods.get(0);
        } else {


            for (final Method method : equalsMethods) {
                final Class<?>[] params = method.getParameterTypes();
                boolean noConflict = true;

                for (int i = 0; i < params.length; ++i) {
                    if (!params[i].isAssignableFrom(paramClasses[i])) {
                        noConflict = false;
                        break;
                    }
                }

                if (noConflict) {
                    m = method;
                    break;
                }
            }

        }

        if (m == null) {
            throw new NoSuchMethodException(request.getMethodName());
        } else {
            return m.invoke(target, request.getParams());
        }
    }

    public static byte[] addFirstTimeMark(final byte[] arr, final byte timeMark, final long time) {

        return doAddTimeMark(arr, timeMark, time, true);
    }

    public static byte[] addTimeMark(final byte[] arr, final byte timeMark, final long time) {

        return doAddTimeMark(arr, timeMark, time, false);
    }

    private static byte[] doAddTimeMark(byte[] arr, final byte timeMark, final long time, final boolean isFirst) {

        if (isFirst) {
            arr = null;
        } else if (arr == null || arr.length <= 0) {
            return arr;
        }

        final byte[] result;
        if (arr != null) {
            result = new byte[arr.length + TIME_MARK_RECORD_LENGTH];
            System.arraycopy(arr, 0, result, 0, arr.length);
        } else {
            result = new byte[9];
        }

        int off = result.length - TIME_MARK_RECORD_LENGTH;
        result[off] = timeMark;
        ++off;
        result[off + 7] = (byte) ((int) (time >>> 0));
        result[off + 6] = (byte) ((int) (time >>> 8));
        result[off + 5] = (byte) ((int) (time >>> 16));
        result[off + 4] = (byte) ((int) (time >>> 24));
        result[off + 3] = (byte) ((int) (time >>> 32));
        result[off + 2] = (byte) ((int) (time >>> 40));
        result[off + 1] = (byte) ((int) (time >>> 48));
        result[off + 0] = (byte) ((int) (time >>> 56));
        return result;
    }

    private static Map<Byte, Long> readTimeMarks(final byte[] arr) {

        final Map<Byte, Long> timeMarks = new LinkedHashMap<>();
        if (arr != null && arr.length > 0 && arr.length % TIME_MARK_RECORD_LENGTH == 0) {

            for (int i = 0; i < arr.length / TIME_MARK_RECORD_LENGTH; ++i) {
                int off = i * TIME_MARK_RECORD_LENGTH;
                final byte timeMark = arr[off];
                ++off;
                final long time = (((long) arr[off + 7] & 255L) << 0)
                                  + (((long) arr[off + 6] & 255L) << 8)
                                  + (((long) arr[off
                                           + 5]
                                & 255L) << 16)
                                  + (((long) arr[off + 4] & 255L) << 24)
                                  + (((long) arr[off + 3] & 255L) << 32)
                                  + (((long) arr[off + 2] & 255L) << 40)
                                  + (((long) arr[off + 1] & 255L) << 48)
                                  + ((long) arr[off + 0] << 56);
                timeMarks.put(timeMark, time);
            }

            return timeMarks;
        } else {
            return timeMarks;
        }
    }

    public static Map<String, Long> readTimeMarksForHuman(final byte[] arr) {

        final Map<Byte, Long> map = readTimeMarks(arr);


        final Map<String, Long> human = new LinkedHashMap<>();

        map.forEach((aByte, aLong) -> {
            final String humanKey = toHuman(aByte);
            human.put(humanKey, aLong);
        });


        return human;

    }

    private static String toHuman(final byte key) {

        switch (key) {
            case TIME_MARK_CLIENT_TO_API:
                return "Client to API";
            case TIME_MARK_API_FROM_CLIENT:
                return "API from Client";
            case TIME_MARK_API_TO_CUST:
                return "API to CUST";
            case TIME_MARK_CUST_FROM_API:
                return "CUST from API";
            case TIME_MARK_CUST_TO_ROUTER:
                return "CUST to Router";
            case TIME_MARK_ROUTER_FROM_CUST:
                return "Router from CUST";
            case TIME_MARK_ROUTER_TO_CUST:
                return "Router to CUST";
            case TIME_MARK_CUST_FROM_ROUTER:
                return "CUST from Router";
            case TIME_MARK_CUST_TO_API:
                return "CUST to API";
            case TIME_MARK_API_FROM_CUST:
                return "API from CUST";
            case TIME_MARK_API_TO_CLIENT:
                return "API to Client";
            case TIME_MARK_CLIENT_FROM_API:
                return "Client from API";
            default:
                return "Unknown";
        }
    }

    public static ListeningExecutorService createExecutor(final int poolSize,
                                                          final long autoCleanUpInerval,
                                                          final int criticalQueueSize,
                                                          final String threadNamePrefix,
                                                          final String threadBasicName,
                                                          final boolean prestartAllCoreThreads) {

        return createExecutor(poolSize,
                              autoCleanUpInerval,
                              criticalQueueSize,
                              threadNamePrefix,
                              null,
                              threadBasicName,
                              prestartAllCoreThreads);
    }

    public static ListeningExecutorService createExecutor(final int poolSize,
                                                          final long autoCleanUpInerval,
                                                          final int criticalQueueSize,
                                                          final String threadNamePrefix,
                                                          final List<Thread> createdThreads,
                                                          final String threadBasicName,
                                                          final boolean prestartAllCoreThreads) {

        return createExecutor(poolSize,
                              poolSize,
                              autoCleanUpInerval,
                              criticalQueueSize,
                              threadNamePrefix,
                              null,
                              threadBasicName,
                              prestartAllCoreThreads);
    }

    private static ListeningExecutorService createExecutor(final int poolSize,
                                                           final int maxPoolSize,
                                                           final long autoCleanUpInerval,
                                                           final int criticalQueueSize,
                                                           final String threadNamePrefix,
                                                           final List<Thread> createdThreads,
                                                           final String threadBasicName,
                                                           final boolean prestartAllCoreThreads) {

        if (poolSize <= 0) {
            return MoreExecutors.newDirectExecutorService();
        } else {
            final ThreadFactory threadFactory = new ThreadFactory() {
                private AtomicInteger counter = new AtomicInteger(0);

                public Thread newThread(final Runnable r) {

                    final Thread thread = new Thread(r, (threadBasicName != null ? "(" + threadBasicName + ") " : "")
                                                        + threadNamePrefix
                                                        + " - "
                                                        + this.counter.getAndIncrement());
                    if (createdThreads != null) {
                        createdThreads.add(thread);
                    }

                    return thread;
                }
            };

            final ListeningOrderedThreadPoolExecutor executor = new ListeningOrderedThreadPoolExecutor(poolSize,
                                                                                                       maxPoolSize,
                                                                                                       1L,
                                                                                                       TimeUnit.MINUTES, threadFactory ,
                                                                                                       autoCleanUpInerval) {
                protected BlockingQueue<Runnable> newChildExecutorWorkQueue() {

                   return new ArrayBlockingQueue<>(criticalQueueSize);

                }
            }; if (prestartAllCoreThreads) {
                executor.prestartAllCoreThreads();
            }

            return executor;
        }
    }

    public static ListeningExecutorService createListeningExecutor(final int poolSize,
                                                                   final long autoCleanUpInerval,
                                                                   final int criticalQueueSize,
                                                                   final String transportName,
                                                                   final String threadNamePrefix) {

        return createListeningExecutor(poolSize,
                                       poolSize,
                                       autoCleanUpInerval,
                                       criticalQueueSize,
                                       transportName,
                                       threadNamePrefix);
    }

    private static ListeningExecutorService createListeningExecutor(final int corePoolSize,
                                                                    final int maxPoolSize,
                                                                    final long autoCleanUpInerval,
                                                                    final int criticalQueueSize,
                                                                    final String transportName,
                                                                    final String threadNamePrefix) {

        return createExecutor(corePoolSize,
                              maxPoolSize,
                              autoCleanUpInerval,
                              criticalQueueSize,
                              threadNamePrefix,
                              null,
                              transportName,
                              true);
    }

    public static void safeShutdown(final int timeout, final TimeUnit unit, final ListeningExecutorService... executors) {

        if (executors != null) {
            for (int i = 0; i < executors.length; ++i) {
                final ListeningExecutorService executor = executors[i];
                safeShutdown(executor, timeout, unit);
            }

        }
    }

    private static void safeShutdown(final ListeningExecutorService executor, final int timeout, final TimeUnit unit) {

        executor.shutdown();

        try {
            executor.awaitTermination((long) timeout, unit);
        } catch (final InterruptedException var4) {

        }

    }

    public static byte[] encode(final ProtocolMessage message, final SessionProtocolEncoder sessionProtocolEncoder)
        throws Exception {

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(out);
        dos.writeByte(4);
        sessionProtocolEncoder.encodeMessage(PROTOCOL_VERSION, dos, message);
        return out.toByteArray();

    }

    public static ProtocolMessage decode(final byte[] bytes, final SessionProtocolDecoder decoder) throws Exception {

        final ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        final DataInputStream dis = new DataInputStream(in);
        final int messageProtocolVersion = dis.readByte();
        return (ProtocolMessage) decoder.decodeMessageUnsafe(messageProtocolVersion, dis);

    }
}
