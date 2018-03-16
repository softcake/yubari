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

import com.dukascopy.dds4.transport.msg.system.ProtocolMessage;

public interface ISessionStats {
    void messageDropped(long var1, long var3, ProtocolMessage var5);

    void messageInExecutionQueue(long var1, ProtocolMessage var3);

    void messageExecutionStarted(long var1, long var3, ProtocolMessage var5);

    void messageExecutionFinished(long var1, long var3, ProtocolMessage var5);

    void messageProcessingPostponeWarning(long var1, long var3, ProtocolMessage var5);

    void messageProcessingPostponeError(long var1, long var3, ProtocolMessage var5);

    void reset();
}
