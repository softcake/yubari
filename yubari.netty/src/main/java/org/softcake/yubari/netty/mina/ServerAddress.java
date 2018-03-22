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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.net.InetSocketAddress;

public class ServerAddress {
    private final String host;
    private final int port;
    private final String addressStr;
    private Long pingTime = Long.MAX_VALUE;

    public ServerAddress(String host, int port) {

        this.host = host;
        this.port = port;
        this.addressStr = host + ":" + port;
    }

    public String getHost() {

        return this.host;
    }

    public int getPort() {

        return this.port;
    }

    public String getAddressStr() {

        return this.addressStr;
    }

    public InetSocketAddress toInetSocketAddress() {

        return new InetSocketAddress(this.host, this.port);
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final ServerAddress that = (ServerAddress) o;
        return port == that.port && Objects.equal(host, that.host) && Objects.equal(pingTime, that.pingTime);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(host, port, pingTime);
    }

    @Override
    public String toString() {

        return MoreObjects.toStringHelper(this)
                          .add("host", host)
                          .add("port", port)
                          .add("pingTime", pingTime)
                          .toString();
    }

    public Long getPingTime() {

        return pingTime;
    }

    public void setPingTime(final Long pingTime) {

        this.pingTime = pingTime < 0L ? Long.MAX_VALUE:pingTime;
    }
}
