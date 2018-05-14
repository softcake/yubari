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
 * Event that is fired once the protocol version negotiation is complete, which may be because it was successful or
 * there was an error.
 */
public final class AuthorizationCompletionEvent extends AbstractConnectingCompletionEvent {

    private AuthorizationCompletionEvent() {

    }

    private AuthorizationCompletionEvent(Throwable cause) {

        super(cause);
    }

    /**
     * Creates a new event that indicates a successful ssl handshake.
     */
    public static AuthorizationCompletionEvent success() {

        return new AuthorizationCompletionEvent();
    }

    /**
     * Creates a new event that indicates an unsuccessful ssl handshake.
     * Use {@link #success()} to indicate a successful handshake.
     *
     * @param cause the cause
     * @return a new event
     */
    public static AuthorizationCompletionEvent failed(Throwable cause) {

        return new AuthorizationCompletionEvent(cause);
    }
}
