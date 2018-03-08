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

package org.softcake.authentication;

public enum RemoteLogMessageType {
    PING_API("ping_api_log", false),
    PING_AUTH("ping_auth_log", false),
    ACTIVITY_LOG("activity_log", false),
    TRADE_LOG("trade_log", false),
    STRATEGY("strategy_log", false),
    DISCLAIMER("disclaimer_log", false),
    CONNECTION("connection_log", false),
    WARNING_NO_RESPONSE("warning_no_response_log", true),
    TRANSPORT_QUEUE("transport_queue_log", false),
    IP_INFO("ip_info_log", false),
    TIME_CHANGE_LOG("time_change_log", false),
    TRANSPORT_SESSION_STATISTICS_LOG("transport_session_statistics_log", false),
    PLATFORM_INFO_LOG("platform_info_log", false),
    NOT_REGISTERED("not_registered_log", false);

    private String messageType;
    private boolean sendToServerActivity = false;

    private RemoteLogMessageType(String messageType, boolean sendToServerActivity) {
        this.messageType = messageType;
        this.sendToServerActivity = sendToServerActivity;
    }

    public String getMessageType() {
        return this.messageType;
    }

    public boolean isSendToServerActivity() {
        return this.sendToServerActivity;
    }

    public String toString() {
        return this.getMessageType();
    }
}
