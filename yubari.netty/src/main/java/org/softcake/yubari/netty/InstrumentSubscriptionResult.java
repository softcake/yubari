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


import org.softcake.yubari.netty.mina.EnumConverter;

import com.dukascopy.dds3.transport.msg.ddsApi.SubscribeResult;

public enum InstrumentSubscriptionResult {
    SUBSCRIBED_FULL,
    SUBSCRIBED,
    UNSUBSCRIBED_FULL,
    UNSUBSCRIBED,
    IGNORED_SUBSCRIBE,
    IGNORED_SUBSCRIBE_FULL,
    IGNORED_UNSUBSCRIBE,
    IGNORED_UNSUBSCRIBE_FULL,
    NOT_ALLOWED;

   InstrumentSubscriptionResult() {

    }

    public static InstrumentSubscriptionResult fromSubscribeResult(SubscribeResult subscribeResult) {

        return EnumConverter.convert(subscribeResult, InstrumentSubscriptionResult.class);
    }

    public SubscribeResult toSubscribeResult() {

        return EnumConverter.convert(this, SubscribeResult.class);
    }

    public boolean isActionPerformed() {

        return this != IGNORED_SUBSCRIBE
               && this != IGNORED_SUBSCRIBE_FULL
               && this != IGNORED_UNSUBSCRIBE
               && this != IGNORED_UNSUBSCRIBE_FULL
               && this != NOT_ALLOWED;

    }

    public boolean isSubscribed() {

        return this == SUBSCRIBED
                         || this == SUBSCRIBED_FULL
                         || this == IGNORED_SUBSCRIBE
                         || this == IGNORED_SUBSCRIBE_FULL
                         || this == UNSUBSCRIBED_FULL;

    }

    public boolean isFullDepthSubscribed() {

        return this == SUBSCRIBED_FULL || this == IGNORED_SUBSCRIBE_FULL;

    }
}
