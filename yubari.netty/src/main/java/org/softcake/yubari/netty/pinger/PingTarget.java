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

package org.softcake.yubari.netty.pinger;


import org.softcake.yubari.netty.client.TransportClientBuilder;

import com.google.common.base.Objects;

import java.net.InetSocketAddress;
import java.util.Set;

public class PingTarget {
    private final InetSocketAddress address;
    private final boolean useSsl;
    private final Set<String> enabledSslProtocols;

    public PingTarget(final InetSocketAddress address, final boolean useSsl) {
        this(address, useSsl, TransportClientBuilder.DEFAULT_SSL_PROTOCOLS);
    }

    public PingTarget(final InetSocketAddress address, final boolean useSsl, final Set<String> enabledSslProtocols) {
        this.address = address;
        this.useSsl = useSsl;
        this.enabledSslProtocols = enabledSslProtocols;
    }

    public InetSocketAddress getAddress() {
        return this.address;
    }

    public boolean isUseSsl() {
        return this.useSsl;
    }

    public Set<String> getEnabledSslProtocols() {
        return this.enabledSslProtocols;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        final PingTarget that = (PingTarget) o;
        return useSsl == that.useSsl &&
               Objects.equal(address, that.address) &&
               Objects.equal(enabledSslProtocols, that.enabledSslProtocols);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(address, useSsl, enabledSslProtocols);
    }

    @Override
    public String toString() {

        return "PingTarget{" +
               "address=" + address +
               ", useSsl=" + useSsl +
               ", enabledSslProtocols=" + enabledSslProtocols +
               '}';
    }
}
