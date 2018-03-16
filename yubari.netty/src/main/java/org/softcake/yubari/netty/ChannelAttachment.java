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


import io.netty.util.AttributeKey;

public class ChannelAttachment extends AbstractChannelAttachment {
    public static final AttributeKey<ChannelAttachment> CHANNEL_ATTACHMENT_ATTRIBUTE_KEY = AttributeKey.valueOf(
        "channel_attachment");
    private final boolean isPrimaryConnection;
    private long lastConnectAttemptTime = Long.MIN_VALUE;
    private int reconnectAttempt = -1;

    public ChannelAttachment(final boolean primaryConnection) {

        this.isPrimaryConnection = primaryConnection;
    }

    public boolean isPrimaryConnection() {

        return this.isPrimaryConnection;
    }

    public long getLastConnectAttemptTime() {

        return this.lastConnectAttemptTime;
    }

    public void setLastConnectAttemptTime(final long lastConnectAttemptTime) {

        this.lastConnectAttemptTime = lastConnectAttemptTime;
    }

    public int getReconnectAttempt() {

        return this.reconnectAttempt;
    }

    public void setReconnectAttempt(final int reconnectAttempt) {

        this.reconnectAttempt = reconnectAttempt;
    }
}
