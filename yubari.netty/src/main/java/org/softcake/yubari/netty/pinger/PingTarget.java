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


import org.softcake.yubari.netty.TransportClientBuilder;

import com.google.common.base.Objects;

import java.net.InetSocketAddress;
import java.util.Set;

public class PingTarget {
    private final InetSocketAddress address;
    private final boolean useSsl;
    private final Set<String> enabledSslProtocols;

    public PingTarget(InetSocketAddress address, boolean useSsl) {
        this(address, useSsl, TransportClientBuilder.DEFAULT_SSL_PROTOCOLS);
    }

    public PingTarget(InetSocketAddress address, boolean useSsl, Set<String> enabledSslProtocols) {
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
    public int hashCode() {

        return Objects.hashCode(address, useSsl, enabledSslProtocols);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            PingTarget other = (PingTarget)obj;
            if (this.address == null) {
                if (other.address != null) {
                    return false;
                }
            } else if (!this.address.equals(other.address)) {
                return false;
            }

            if (this.enabledSslProtocols == null) {
                if (other.enabledSslProtocols != null) {
                    return false;
                }
            } else if (!this.enabledSslProtocols.equals(other.enabledSslProtocols)) {
                return false;
            }

            return this.useSsl == other.useSsl;
        }
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("PingTarget [");
        if (this.address != null) {
            builder.append("address=");
            builder.append(this.address);
            builder.append(", ");
        }

        builder.append("useSsl=");
        builder.append(this.useSsl);
        builder.append(", ");
        if (this.enabledSslProtocols != null) {
            builder.append("enabledSslProtocols=");
            builder.append(this.enabledSslProtocols);
        }

        builder.append("]");
        return builder.toString();
    }
}
