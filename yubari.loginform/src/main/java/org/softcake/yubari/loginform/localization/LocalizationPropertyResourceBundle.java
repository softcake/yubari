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

package org.softcake.yubari.loginform.localization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.PropertyResourceBundle;

public class LocalizationPropertyResourceBundle extends PropertyResourceBundle {
    public LocalizationPropertyResourceBundle(InputStream stream) throws IOException {
        super(stream);
    }

    public LocalizationPropertyResourceBundle(InputStreamReader inputStreamReader) throws IOException {
        super(inputStreamReader);
    }

    public Object handleGetObject(String key) {
        Object object = super.handleGetObject(key);
        object = this.checkSymbols(object);
        return object;
    }

    private Object checkSymbols(Object object) {
        if (object instanceof String) {
            String targetString = (String)object;
            if (targetString.contains("{") && targetString.contains("}")) {
                object = targetString.replace("'", "''");
            }
        }

        return object;
    }
}
