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

/**
 * Event that is fired once the SSL handshake is complete, which may be because it was successful or there
 * was an error.
 */
public final class ProtocolVersionNegotiationEvent extends AbstractProtocolVersionNegotiationEvent {


    public static final ProtocolVersionNegotiationEvent SUCCESS = new ProtocolVersionNegotiationEvent();

    /**
     * Creates a new event that indicates a successful handshake.
     */
    private ProtocolVersionNegotiationEvent() {

    }
    /**
     * Creates a new event that indicates an unsuccessful handshake.
     * Use {@link #SUCCESS} to indicate a successful handshake.
     */
    public ProtocolVersionNegotiationEvent(Throwable cause) {
        super(cause);
    }
}
