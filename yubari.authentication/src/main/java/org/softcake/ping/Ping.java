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

package org.softcake.ping;


import org.softcake.yubari.netty.mina.ServerAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@SuppressWarnings("squid:S2236")
public final class Ping {


    private static final Logger LOGGER = LoggerFactory.getLogger(Ping.class);

    private static final int TIMEOUT = 2000;
    private static final int ATTEMPTS = 3;

    private Ping() {

    }


    public static ServerAddress ping(final ServerAddress address) {

        LOGGER.debug("Initiating pinging connect to {}", address.toInetSocketAddress());

        final Target[] targets = createPingTargets(address);
        try {
            final Connector connector = new Connector();
            connector.start();


            connector.addAll(targets);
            synchronized (connector) {

                final long startTime = System.currentTimeMillis();

                while (true) {
                    boolean isReady = true;
                    for (final Target target : targets) {
                        if (target.getFailure() == null && target.getConnectFinish() == Long.MIN_VALUE) {
                            isReady = false;
                            break;
                        }
                    }

                    if (!isReady) {
                        final long timeOut = startTime + TIMEOUT - System.currentTimeMillis();
                        if (timeOut > 0L) {
                            connector.wait(timeOut);
                        }
                    } else {
                        break;
                    }
                }
            }


            //Thread.sleep(100L);
            connector.shutdown();
            connector.join();

        } catch (InterruptedException e) {
            LOGGER.error("Exception while ping target", e);
        }


        final Long[] pingTime = getPingTimes(targets);


        address.setPingTime(getAverageTime(pingTime));
        LOGGER.debug("Ping to [{}] finished with average {}ms", address.toInetSocketAddress(), address.getPingTime());

        return address;
    }


    private static Target[] createPingTargets(final ServerAddress address) {

        final Target[] targets = new Target[ATTEMPTS];
        for (int i = 0; i < ATTEMPTS; i++) {
            final Target target = new Target(address.toInetSocketAddress());

            targets[i] = target;

        }
        return targets;
    }


    private static Long[] getPingTimes(final Target[] targets) {

        final Long[] pingTime = new Long[targets.length];

        for (int i = 0; i < targets.length; i++) {

            final Target target = targets[i];

            if (target.getFailure() != null) {
                LOGGER.error("Ping to [{}] failed: {}",
                             target.getAddress(),
                             target.getFailure()
                                   .getMessage(),
                             target.getFailure());
                pingTime[i] = Long.MIN_VALUE;

            } else if (target.getPingTime() != Long.MIN_VALUE) {
                pingTime[i] = target.getPingTime();

            } else {
                LOGGER.debug("Ping to [{}] timed out.", target.getAddress());
                pingTime[i] = Long.MIN_VALUE;
            }
        }
        return pingTime;
    }

    /**
     * @param pingTimes array to calculate the average ping time.
     * @return the average time or {@code Long.MAX_VALUE} if one time invalid.
     */
    private static Long getAverageTime(final Long[] pingTimes) {

        Long bestTime = 0L;
        for (final Long time : pingTimes) {
            if (time < 0 || time == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            } else {
                bestTime += time;
            }
        }
        return bestTime / pingTimes.length;
    }


    public static void main(final String[] args) throws InterruptedException, IOException {

        ServerAddress serverAddress = new ServerAddress("sonar.aldeso.com", 80);
        ServerAddress ping = ping(serverAddress);

        LOGGER.info("PingTime: {}", ping.getPingTime());


    }


}
