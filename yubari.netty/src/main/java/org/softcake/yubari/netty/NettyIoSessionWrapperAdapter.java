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


import org.softcake.yubari.netty.mina.IoSessionWrapper;

import io.netty.channel.Channel;

import java.net.SocketAddress;

public abstract class NettyIoSessionWrapperAdapter implements IoSessionWrapper {
    protected volatile Channel channel;

    public NettyIoSessionWrapperAdapter() {

    }

    public NettyIoSessionWrapperAdapter(Channel channel) {

        this.channel = channel;
    }

    public Channel getChannel() {

        return this.channel;
    }

    public void setChannel(Channel channel) {

        this.channel = channel;
    }

    public SocketAddress getRemoteAddress() {

        return this.channel.remoteAddress();
    }

    public void close() {

        this.channel.close();
    }

    public boolean isConnected() {

        return this.channel.isActive();
    }

    public boolean isClosing() {

        return !this.channel.isOpen();
    }

    public long getLastIoTime() {

        throw new UnsupportedOperationException("Do you really need this?");
    }

    public long getLastReadTime() {

        throw new UnsupportedOperationException("Do you really need this?");
    }

    public long getLastWriteTime() {

        throw new UnsupportedOperationException("Do you really need this?");
    }
}
