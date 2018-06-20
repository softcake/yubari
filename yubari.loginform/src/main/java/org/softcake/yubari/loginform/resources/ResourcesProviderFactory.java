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

package org.softcake.yubari.loginform.resources;

import org.softcake.yubari.loginform.utils.JNLPObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourcesProviderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesProviderFactory.class);

    public ResourcesProviderFactory() {
    }

    public static IResourcesProvider createWebStartResourcesProvider() {
        ResourcesProvider resourcesProvider = null;

        try {
            WebStartResourcesUrlProvider webStartResourcesUrlProvider = new WebStartResourcesUrlProvider();
            resourcesProvider = new ResourcesProvider(webStartResourcesUrlProvider);
        } catch (Throwable var2) {
            LOGGER.error(var2.getMessage(), var2);
        }

        return resourcesProvider;
    }

    public static IResourcesProvider createStandAloneResourcesProvider(JNLPObject jnlpObject) {
        ResourcesProvider resourcesProvider = null;

        try {
            StandAloneResourcesUrlProvider standAloneResourcesUrlProvider = new StandAloneResourcesUrlProvider(jnlpObject);
            resourcesProvider = new ResourcesProvider(standAloneResourcesUrlProvider);
        } catch (Throwable var3) {
            LOGGER.error(var3.getMessage(), var3);
        }

        return resourcesProvider;
    }
}
