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

package org.softcake.yubari.loginform.service.account;

import org.softcake.yubari.loginform.controller.PlatformEnvironment;

import java.util.HashMap;
import java.util.Map;

public class LastLoginBean {
    private PlatformEnvironment platformEnvironment;
    private String userName;
    private Map<PlatformEnvironment, String> environments = new HashMap();

    public LastLoginBean() {
    }

    public LastLoginBean(PlatformEnvironment platformEnvironment, String userName) {
        this.platformEnvironment = platformEnvironment;
        this.userName = userName;
        this.add(platformEnvironment, userName);
    }

    public void add(PlatformEnvironment platformEnvironment, String userName) {
        this.environments.put(platformEnvironment, userName);
    }

    public Map<PlatformEnvironment, String> getEnvironments() {
        return this.environments;
    }

    public boolean containsUser(PlatformEnvironment platformEnvironment) {
        String userName = (String)this.environments.get(platformEnvironment);
        return userName != null && !userName.isEmpty();
    }

    public String getUserName(PlatformEnvironment platformEnvironment) {
        String userName = null;
        if (platformEnvironment == PlatformEnvironment.DEMO) {
            userName = (String)this.environments.get(platformEnvironment);
            if (userName == null || userName.isEmpty()) {
                userName = (String)this.environments.get(PlatformEnvironment.DEMO_3);
            }
        } else if (platformEnvironment == PlatformEnvironment.DEMO_3) {
            userName = (String)this.environments.get(platformEnvironment);
            if (userName == null || userName.isEmpty()) {
                userName = (String)this.environments.get(PlatformEnvironment.DEMO);
            }
        } else if (platformEnvironment == PlatformEnvironment.LIVE) {
            userName = (String)this.environments.get(platformEnvironment);
            if (userName == null || userName.isEmpty()) {
                userName = (String)this.environments.get(PlatformEnvironment.LIVE_3);
            }
        } else if (platformEnvironment == PlatformEnvironment.LIVE_3) {
            userName = (String)this.environments.get(platformEnvironment);
            if (userName == null || userName.isEmpty()) {
                userName = (String)this.environments.get(PlatformEnvironment.LIVE);
            }
        } else {
            userName = (String)this.environments.get(platformEnvironment);
        }

        return userName;
    }

    public void deleteAuthorizedUser(PlatformEnvironment platformEnvironment) {
        this.environments.remove(platformEnvironment);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder(64);
        buf.append("LastLoginBean:").append("\n").append("   ").append("Environment:").append(this.platformEnvironment).append("\n").append("   ").append("UserName:").append(this.userName);
        return buf.toString();
    }
}
