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

package org.softcake.yubari.loginform.ui.laf;

import java.util.HashMap;
import java.util.Map;

public class Style {
    private String id;
    private Map<String, Object> propertyMap = new HashMap();

    public Style() {
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getPropertyMap() {
        return this.propertyMap;
    }

    public void setPropertyMap(Map<String, Object> colorsMap) {
        this.propertyMap = colorsMap;
    }
}
