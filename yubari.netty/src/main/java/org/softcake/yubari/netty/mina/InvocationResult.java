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

import java.io.Serializable;

public class InvocationResult implements Serializable {
    private static final long serialVersionUID = 200706151042L;
    private String requestId;
    private Serializable result;
    public static final int STATE_OK = 0;
    public static final int STATE_ERROR = 1;
    public static final int STATE_PROCESSING = 2;
    private int state;
    private Throwable throwable;
    private Long receivedTime;

    public InvocationResult(Serializable serializable, String requestId) {

        this.result = serializable;
        this.requestId = requestId;
    }

    public Serializable getResult() {

        return this.result;
    }

    public void setResult(Serializable result) {

        this.result = result;
    }

    public String getRequestId() {

        return this.requestId;
    }

    public void setRequestId(String requestId) {

        this.requestId = requestId;
    }

    public int getState() {

        return this.state;
    }

    public void setState(int state) {

        this.state = state;
    }

    public Throwable getThrowable() {

        return this.throwable;
    }

    public void setThrowable(Throwable throwable) {

        this.throwable = throwable;
    }

    public Long getReceivedTime() {

        return this.receivedTime;
    }

    public void setReceivedTime(Long receivedTime) {

        this.receivedTime = receivedTime;
    }
}
