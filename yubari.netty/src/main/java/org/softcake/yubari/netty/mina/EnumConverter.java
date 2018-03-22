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

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class EnumConverter {
    public EnumConverter() {

    }

    public static <X extends Enum<X>, Y extends Enum<Y>> Y convert(X x, Class<Y> yClass) {

        return x != null ? Enum.valueOf(yClass, x.name()) : null;
    }

    public static <X extends Enum<X>, Y extends Enum<Y>> Set<Y> convert(Set<X> xSet, Class<Y> yClass) {

        Set<Y> enumSet = EnumSet.noneOf(yClass);
        if (xSet != null) {
            xSet.forEach(x -> enumSet.add(Enum.valueOf(yClass, x.name())));



        }

        return enumSet;
    }

    public static <X extends Enum<X>> Set<String> convert(Set<X> xSet) {

        if (xSet == null) {
            return Collections.emptySet();
        } else {
            Set<String> set = new HashSet<>();
            for (final X x : xSet) {
                set.add(x.toString());
            }

            return set;
        }
    }

    public static <X extends Enum<X>> Set<String> convertByName(Set<X> xSet) {

        if (xSet == null) {
            return Collections.emptySet();
        } else {



            Set<String> set = new HashSet<>();
            for (final X x : xSet) {
                set.add(x.name());
            }

            return set;
        }
    }
}
