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

package org.softcake.yubari.loginform.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ObjectUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectUtils.class);
    public static final String MD5 = "MD5";
    public static final String EXTENSION_CLASS = ".class";
    public static final String EXTENSION_JFX = ".jfx";

    public ObjectUtils() {
    }

    public static boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null) {
            return obj2 == null;
        } else {
            return obj2 != null && obj1.equals(obj2);
        }
    }

    public static int getHash(Object obj) {
        return obj == null ? 0 : obj.hashCode();
    }

    public static boolean isNullOrEmpty(Object o) {
        if (o == null) {
            return true;
        } else if (o instanceof String) {
            return ((String)o).isEmpty();
        } else if (o instanceof Collection) {
            return ((Collection)o).isEmpty();
        } else {
            return o instanceof Map && ((Map)o).isEmpty();
        }
    }

    public static <T> int compare(Comparable<T> comp1, T comp2) {
        if (comp1 == null) {
            return comp2 == null ? 0 : -1;
        } else {
            return comp2 == null ? 1 : comp1.compareTo(comp2);
        }
    }

    public static <T> int compare(T o1, T o2, Comparator<T> comparator) {
        if (o1 == null) {
            return o2 == null ? 0 : -1;
        } else {
            return o2 == null ? 1 : comparator.compare(o1, o2);
        }
    }

    public static String toString(Object value, String defaultValue) {
        return value != null ? value.toString() : defaultValue;
    }

    public static <T extends Comparable<? super T>> List<T> getSortedCopyList(Collection<T> collection) {
        List<T> copy = new ArrayList(collection);
        Collections.sort(copy);
        return copy;
    }

    public static <T> T cast(Object o) {
        return (T) o;
    }

    public static <T> boolean equals(Collection<T> collection1, Collection<T> collection2) {
        if (collection1 == null && collection2 == null) {
            return true;
        } else if (collection1 != null && collection2 != null) {
            if (collection1.size() != collection2.size()) {
                return false;
            } else {
                Iterator var2 = collection1.iterator();

                Object t;
                do {
                    if (!var2.hasNext()) {
                        return true;
                    }

                    t = var2.next();
                } while(collection2.contains(t));

                return false;
            }
        } else {
            return false;
        }
    }

    public static <T> ObjectUtils.Holder<T> getHolder(T variable) {
        return new ObjectUtils.Holder(variable);
    }

    public static <T> ObjectUtils.Holder<T> getHolder(Class<T> clazz) {
        return new ObjectUtils.Holder();
    }

    public static class Holder<T> {
        private T value;

        public Holder() {
        }

        public Holder(T value) {
            this.setValue(value);
        }

        T getValue() {
            return this.value;
        }

        void setValue(T value) {
            this.value = value;
        }
    }
}
