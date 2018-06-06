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

import com.dukascopy.login.controller.ILoginDialogController;
import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompatibilityUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilityUtil.class);

    public CompatibilityUtil() {
    }

    public static boolean isDialogWithTwoViews(Class<?> clazz) {
        boolean result = false;
        String version = "";

        try {
            Field declaredField = clazz.getDeclaredField("version");
            Object object = declaredField.get((Object)null);
            version = object.toString();
        } catch (NoSuchFieldException var5) {
            ;
        } catch (Throwable var6) {
            LOGGER.error(var6.getMessage(), var6);
        }

        if ("new_captcha_version".equals(version)) {
            result = true;
        }

        return result;
    }

    public static boolean isPlatformCompatibleWithLoginDialog(String fixName) {
        boolean result = false;
        String fieldValue = "";

        try {
            Class<?> clazz = ILoginDialogController.class;
            Field declaredField = clazz.getDeclaredField(fixName);
            Object object = declaredField.get((Object)null);
            fieldValue = object.toString();
        } catch (NoSuchFieldException var6) {
            ;
        } catch (Throwable var7) {
            LOGGER.error(var7.getMessage(), var7);
        }

        if (fieldValue.equals(fixName)) {
            result = true;
        }

        return result;
    }
}
