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

package org.softcake.yubari.loginform.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum PlatformEnvironment {
    TEST("TEST", ""),
    PREDEMO("PRE-DEMO", ""),
    DEMO("DEMO", ""),
    DEMO_3("DEMO_3", ""),
    LIVE("LIVE", ""),
    LIVE_3("LIVE_3", ""),
    UNDEFINED("UNDEFINED", "");

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformEnvironment.class);
    private static final String PLATFORM_VERSION_TEMPLATE = "%s / %s";
    private String environment;
    private String platformVersion;
    private String installationVersion;
    private String jnlpUrl;
    private volatile boolean platformUpdateFailed = false;
    private volatile boolean platformVersionVerifyFailed = false;
    private volatile boolean platformExists = false;
    private volatile boolean platformUpdateRefusedDueMultipleInstances = false;
    private volatile boolean webStartMode = false;
    private volatile boolean failurePlatformResources = false;

    private PlatformEnvironment(String environment, String platformVersion) {
        this.environment = environment;
        this.platformVersion = platformVersion;
    }

    public String getEnvironment() {
        return this.environment;
    }

    public boolean islocalJnlpUrl() {
        boolean localJnlpUrl = false;

        try {
            if (this.jnlpUrl != null && this.jnlpUrl.startsWith("file:///")) {
                localJnlpUrl = true;
            }
        } catch (Throwable var3) {
            LOGGER.error(var3.getMessage(), var3);
        }

        return localJnlpUrl;
    }

    public Path getlocalJnlpPath() {
        Path localJnlpPath = null;

        try {
            if (this.islocalJnlpUrl()) {
                String temp = this.jnlpUrl.replace("file:///", "");
                Path path = Paths.get(temp);
                path = path.resolveSibling("");
                localJnlpPath = path;
            }
        } catch (Throwable var4) {
            LOGGER.error(var4.getMessage(), var4);
        }

        return localJnlpPath;
    }

    public String getPlatformVersion() {
        return this.platformVersion;
    }

    public void setPlatformVersion(String platformVersion) {
        if (platformVersion != null) {
            platformVersion = platformVersion.trim();
        }

        this.platformVersion = platformVersion;
    }

    public String getInstallationVersion() {
        return this.installationVersion;
    }

    public void setInstallationVersion(String installationVersion) {
        if (installationVersion != null) {
            installationVersion = installationVersion.trim();
        }

        this.installationVersion = installationVersion;
    }

    public String getVersion() {
        if (this.webStartMode) {
            return this.platformVersion;
        } else {
            String plVersion = "-";
            String instVersion = "-";
            if (this.platformVersion != null && !this.platformVersion.isEmpty()) {
                plVersion = this.platformVersion;
            }

            if (this.installationVersion != null && !this.installationVersion.isEmpty()) {
                instVersion = this.installationVersion;
            }

            return String.format("%s / %s", plVersion, instVersion);
        }
    }

    public static PlatformEnvironment fromValue(String environment) {
        PlatformEnvironment[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            PlatformEnvironment enumer = var1[var3];
            if (enumer.environment.equalsIgnoreCase(environment)) {
                return enumer;
            }
        }

        if ("predemo".equalsIgnoreCase(environment)) {
            return PREDEMO;
        } else {
            throw new IllegalArgumentException("Invalid param: " + environment);
        }
    }

    public String getJnlpUrl() {
        return this.jnlpUrl;
    }

    public void setJnlpUrl(String jnlpUrl) {
        this.jnlpUrl = jnlpUrl;
    }

    public boolean isPlatformUpdateFailed() {
        return this.platformUpdateFailed;
    }

    public void setPlatformUpdateFailed(boolean platformUpdateFailed) {
        this.platformUpdateFailed = platformUpdateFailed;
    }

    public boolean isPlatformVersionVerifyFailed() {
        return this.platformVersionVerifyFailed;
    }

    public void setPlatformVersionVerifyFailed(boolean platformVersionVerifyFailed) {
        this.platformVersionVerifyFailed = platformVersionVerifyFailed;
    }

    public boolean isPlatformExists() {
        return this.platformExists;
    }

    public void setPlatformExists(boolean platformExists) {
        this.platformExists = platformExists;
    }

    public boolean platformMustBeUpdated() {
        return !this.platformExists && (this.platformUpdateFailed || this.platformVersionVerifyFailed) || this.platformUpdateRefusedDueMultipleInstances || this.failurePlatformResources;
    }

    public boolean isPlatformUpdateRefusedDueMultipleInstances() {
        return this.platformUpdateRefusedDueMultipleInstances;
    }

    public void setPlatformUpdateRefusedDueMultipleInstances(boolean platformUpdateRefusedDueMultipleInstances) {
        this.platformUpdateRefusedDueMultipleInstances = platformUpdateRefusedDueMultipleInstances;
    }

    public boolean isWebStartMode() {
        return this.webStartMode;
    }

    public void setWebStartMode(boolean webStartMode) {
        this.webStartMode = webStartMode;
    }

    public boolean isFailurePlatformResources() {
        return this.failurePlatformResources;
    }

    public void setFailurePlatformResources(boolean failurePlatformResources) {
        this.failurePlatformResources = failurePlatformResources;
    }

    public String toString() {
        return this.environment;
    }
}
