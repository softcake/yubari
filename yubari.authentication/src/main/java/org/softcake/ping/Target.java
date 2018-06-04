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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

public class Target {

    private static final Logger LOGGER = LoggerFactory.getLogger(Target.class);
    private InetSocketAddress address;

        public void setChannel(final SocketChannel channel) {

            this.channel = channel;
        }

        private SocketChannel channel;

        public Throwable getFailure() {

            return failure;
        }

        public void setFailure(final Throwable failure) {

            this.failure = failure;
        }

        private Throwable failure;

    public long getConnectStart() {

        return connectStart;
    }

    public long getConnectFinish() {

        return connectFinish;
    }

    public void setConnectStart(final long connectStart) {

        this.connectStart = connectStart;
    }

    public void setConnectFinish(final long connectFinish) {

        this.connectFinish = connectFinish;
    }

    private long connectStart ;
        private long connectFinish = Long.MIN_VALUE;
        private boolean shown = false;

        public Target(final String host, final int port) {

            try {
                this.address = new InetSocketAddress(InetAddress.getByName(host), port);
            } catch (final IOException e) {
                this.failure = e;
            }

        }

        public Target(final InetSocketAddress address) {

            this.address = address;
        }

        public InetSocketAddress getAddress() {

            return address;
        }

        public SocketChannel getChannel() {

            return channel;
        }


        public long getPingTime() {


                return connectFinish - connectStart;

        }

        public void show() {

            final String result;
            if (this.connectFinish != Long.MIN_VALUE) {
                result = Long.toString(this.connectFinish - this.connectStart) + "ms";
            } else if (this.failure != null) {
                result = this.failure.toString();
            } else {
                result = "Timed out";
            }

            System.out.println(this.address + " : " + result);
            this.shown = true;
        }
    }