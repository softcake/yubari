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

import org.softcake.yubari.loginform.utils.ObjectUtils;

import java.net.URL;
import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebStartResourcesUrlProvider extends AbstractResourcesProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebStartResourcesUrlProvider.class);

    public WebStartResourcesUrlProvider() {
    }

    public String getPlatformLogoUrl() throws Exception {
        String platformLogoUrlAsString = null;

        try {
            String codeBase = this.getCodeBase();
            String relativePlatformLogoUrl = this.getRelativePlatformLogoUrl();
            platformLogoUrlAsString = this.getURL(codeBase, relativePlatformLogoUrl);
            LOGGER.debug("platformLogoUrlAsString=" + platformLogoUrlAsString);
            return platformLogoUrlAsString;
        } catch (UnavailableServiceException var4) {
            throw new UnavailableWSServiceException();
        }
    }

    public String getCompanyLogoUrl() throws Exception {
        String logoUrlAsString = null;

        try {
            String codeBase = this.getCodeBase();
            String relativeCompanyLogoUrl = this.getRelativeCompanyLogoUrl();
            logoUrlAsString = this.getURL(codeBase, relativeCompanyLogoUrl);
            LOGGER.debug("logoUrl=" + logoUrlAsString);
            return logoUrlAsString;
        } catch (UnavailableServiceException var4) {
            throw new UnavailableWSServiceException();
        }
    }

    private String getCodeBase() throws Exception {
        BasicService basicService = (BasicService)ServiceManager.lookup("javax.jnlp.BasicService");
        URL codeBaseUrl = basicService.getCodeBase();
        String codeBase = codeBaseUrl.toString();
        LOGGER.debug("codeBase=" + codeBase);
        return codeBase;
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
        String relativePlatformlogoUrl = System.getProperty("jnlp.platform.logo.url");
        if (relativePlatformlogoUrl != null) {
            relativePlatformlogoUrl = relativePlatformlogoUrl.trim();
        }

        if (ObjectUtils.isNullOrEmpty(relativePlatformlogoUrl)) {
            throw new IllegalArgumentException("The jnlp.platform.logo.url is absent.");
        } else {
            LOGGER.debug("relativePlatformlogoUrl=" + relativePlatformlogoUrl);
            return relativePlatformlogoUrl;
        }
    }

    public ResourcesUrlProviderType getResourcesUrlProviderType() {
        return ResourcesUrlProviderType.WEBSTART;
    }
}
