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

package org.softcake.yubari.netty.map;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public class MapHelper {
    public MapHelper() {
    }

    public static <K, V> HashMap<K, V> create() {
        return new HashMap<>();
    }

    public static <K, V> HashMap<K, V> create(K k1, V v1) {
        HashMap<K, V> map = new HashMap<>();
        map.put(k1, v1);
        return map;
    }

    public static <K, V> HashMap<K, V> create(K k1, V v1, K k2, V v2) {
        HashMap<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    public static <K, V> HashMap<K, V> create(K k1, V v1, K k2, V v2, K k3, V v3) {
        HashMap<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    public static <K, V> HashMap<K, V> create(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        HashMap<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }

    public static <K, V> HashMap<K, V> create(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        HashMap<K, V> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        map.put(k5, v5);
        return map;
    }

    public static <K, V> MapHelper.Builder<K, V> builder() {
        return new MapHelper.Builder<>();
    }

    public static <K, V> V getAndPutIfAbsent(Map<K, V> map, K key, MapHelper.IValueCreator<V> creator) {
        V value = map.get(key);
        if (value == null) {
            value = creator.create();
            map.put(key, value);
        }

        return value;
    }

    public interface IValueCreator<V> {
        V create();
    }

    public static class Builder<K, V> {
        final HashMap<K, V> map = new HashMap<>();

        public Builder() {
        }

        public MapHelper.Builder<K, V> put(K key, V value) {
            this.map.put(key, value);
            return this;
        }

        public MapHelper.Builder<K, V> put(Entry<? extends K, ? extends V> entry) {
            this.map.put(entry.getKey(), entry.getValue());
            return this;
        }

        public MapHelper.Builder<K, V> putAll(Map<? extends K, ? extends V> map) {

            final Iterator<? extends Entry<? extends K, ? extends V>> var2 = map.entrySet()
                                                                                    .iterator();

            while(var2.hasNext()) {
                Entry<? extends K, ? extends V> entry = var2.next();
                this.put(entry.getKey(), entry.getValue());
            }

            return this;
        }

        public HashMap<K, V> build() {
            return this.map;
        }
    }
}
