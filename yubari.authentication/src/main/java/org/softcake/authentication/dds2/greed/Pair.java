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

package org.softcake.authentication.dds2.greed;

public class Pair<U, V> {
    private U first;
    private V second;

    public Pair() {
    }

    public Pair(U first, V second) {
        this.first = first;
        this.second = second;
    }

    public U getFirst() {
        return this.first;
    }

    public V getSecond() {
        return this.second;
    }

    public void setFirst(U first) {
        this.first = first;
    }

    public void setSecond(V second) {
        this.second = second;
    }

    public boolean isEmpty() {
        return this.first == null && this.second == null;
    }

    public boolean bothValuesArePresent() {
        return this.first != null && this.second != null;
    }
}
