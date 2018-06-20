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
import org.softcake.yubari.loginform.utils.ObjectUtils;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandAloneResourcesUrlProvider extends AbstractResourcesProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(StandAloneResourcesUrlProvider.class);
    private JNLPObject jnlpObject;

    public StandAloneResourcesUrlProvider(JNLPObject jnlpObject) {
        this.jnlpObject = jnlpObject;
    }

    public String getPlatformLogoUrl() throws Exception {
        String relativePlatformLogoUrl = this.getRelativePlatformLogoUrl();
        String codeBase = this.getCodeBase();
        String logoUrlAsString = this.getURL(codeBase, relativePlatformLogoUrl);
        LOGGER.debug("logoUrl=" + logoUrlAsString);
        return logoUrlAsString;
    }

    public String getCompanyLogoUrl() throws Exception {
        String relativeCompanyLogoUrl = this.getRelativeCompanyLogoUrl();
        String codeBase = this.getCodeBase();
        String logoUrlAsString = this.getURL(codeBase, relativeCompanyLogoUrl);
        LOGGER.debug("logoUrl=" + logoUrlAsString);
        return logoUrlAsString;
    }

    private String getCodeBase() throws Exception {
        if (this.jnlpObject == null) {
            throw new JNLPObjectAbsentException();
        } else {
            String jnlpUrl = this.jnlpObject.getJnlpUrl();
            String codeBase;
            if (jnlpUrl.startsWith("file:///")) {
                codeBase = "";

                try {
                    Path localJnlpPath = Paths.get(URI.create(jnlpUrl));
                    localJnlpPath = localJnlpPath.resolveSibling("");
                    codeBase = "file:///" + localJnlpPath.toString();
                    LOGGER.debug("codeBase=" + codeBase);
                } catch (Throwable var4) {
                    LOGGER.error(var4.getMessage(), var4);
                }

                return codeBase;
            } else {
                codeBase = this.jnlpObject.getJnlpTagAttribute("codebase");
                LOGGER.debug("codeBase=" + codeBase);
                return codeBase;
            }
        }
    }

    private String getRelativeCompanyLogoUrl() {
        String relativelogoUrl = System.getProperty("jnlp.company.logo.url");
        if (relativelogoUrl != null) {
            relativelogoUrl = relativelogoUrl.trim();
        }

        if (ObjectUtils.isNullOrEmpty(relativelogoUrl)) {
            throw new IllegalArgumentException("The jnlp.company.logo.url is absent.");
        } else {
            LOGGER.debug("relativelogoUrl=" + relativelogoUrl);
            return relativelogoUrl;
        }
    }

    private String getRelativePlatformLogoUrl() {
        String relativelogoUrl = System.getProperty("jnlp.platform.logo.url");
        if (relativelogoUrl != null) {
            relativelogoUrl = relativelogoUrl.trim();
        }

        if (ObjectUtils.isNullOrEmpty(relativelogoUrl)) {
            throw new IllegalArgumentException("The jnlp.platform.logo.url is absent.");
        } else {
            LOGGER.debug("relativePlatformlogoUrl=" + relativelogoUrl);
            return relativelogoUrl;
        }
    }

    public ResourcesUrlProviderType getResourcesUrlProviderType() {
        return ResourcesUrlProviderType.STANDALONE;
    }
}
