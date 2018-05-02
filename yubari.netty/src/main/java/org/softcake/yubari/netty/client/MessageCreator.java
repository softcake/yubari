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

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author René Neubert
 */
public class MessageCreator implements Runnable {
    RXJavaExample example;
    int count = 0;

    public MessageCreator(final RXJavaExample example) {

        this.example = example;
    }

    @SuppressWarnings("squid:S2189")
    @Override
    public void run() {

        while (true) {
            String msg = "message-" + count;
            try {
                this.example.messageReceived(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            count++;
            if (count == Integer.MAX_VALUE) {
                count = 0;
            }
            final long nextLong = ThreadLocalRandom.current().nextLong(10, 100);
            try {
                Thread.sleep(nextLong);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

    }
}
