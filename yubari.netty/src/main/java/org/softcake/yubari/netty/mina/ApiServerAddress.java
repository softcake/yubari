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

/**
 * @author The softcake authors
 */
public class ApiServerAddress extends ServerAddress {
    private final String ticket;
    public ApiServerAddress(final String host, final int port, final String ticket) {
        super(host, port);
        this.ticket = ticket;
    }
    public ApiServerAddress(final ServerAddress serverAddress, final String ticket) {
        super(serverAddress.getHost(), serverAddress.getPort());
        this.ticket = ticket;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) { return true; }
        if (!(o instanceof ApiServerAddress)) { return false; }
        if (!super.equals(o)) { return false; }
        final ApiServerAddress that = (ApiServerAddress) o;
        return Objects.equal(ticket, that.ticket);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(super.hashCode(), ticket);
    }

    @Override
    public String toString() {

        return MoreObjects.toStringHelper(this).add("ticket", ticket).toString();
    }

    public String getTicket() {

        return ticket;
    }
}
