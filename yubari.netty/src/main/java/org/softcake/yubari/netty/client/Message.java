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

package org.softcake.yubari.netty.client;

/**
 * @author René Neubert
 */
public class Message {

    public String getMessage() {

        return name + "-" + String.valueOf(count);
    }

    public String getName() {

        return name;
    }

    private String name;

    public Message(final String name, final int count) {

        this.name = name;
        this.count = count;
        this.creationTime = System.currentTimeMillis();
    }

    public long getCreationTime() {

        return creationTime;
    }

    public int getCount() {

        return count;
    }

    private long creationTime;
    private int count;
}
