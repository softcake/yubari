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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Users {
    private static Logger LOGGER = LoggerFactory.getLogger(Users.class);
    private LastLoginBean lastLoginBean;
    private Map<PlatformEnvironment, List<UserBean>> users = new HashMap();
    private DeviceIDBean deviceIDBean = null;
    private DisclaimerBean disclaimerBean = new DisclaimerBean();

    public Users() {
    }

    public LastLoginBean getLastLoginBean() {
        return this.lastLoginBean;
    }

    public Map<PlatformEnvironment, List<UserBean>> getUsers() {
        return this.users;
    }

    public void setLastLoginBean(LastLoginBean lastLoginBean) {
        this.lastLoginBean = lastLoginBean;
    }

    public void addUser(UserBean userBean) {
        PlatformEnvironment platformEnvironment = userBean.getPlatformEnvironment();
        List<UserBean> listOfUsers = (List)this.users.get(platformEnvironment);
        if (listOfUsers == null) {
            listOfUsers = new ArrayList();
            this.users.put(platformEnvironment, listOfUsers);
        }

        ((List)listOfUsers).add(userBean);
    }

    public UserBean getUser(String platformEnvironmentAsString, String userName) {
        PlatformEnvironment platformEnvironment = PlatformEnvironment.fromValue(platformEnvironmentAsString);
        return this.getUser(platformEnvironment, userName);
    }

    public UserBean getUser(PlatformEnvironment platformEnvironment, String userName) {
        if (platformEnvironment == null) {
            throw new IllegalArgumentException("The platform environment cannot be null");
        } else if (userName != null && !userName.isEmpty()) {
            List<UserBean> listOfUsers = (List)this.users.get(platformEnvironment);
            if (listOfUsers != null) {
                Iterator var4 = listOfUsers.iterator();

                while(var4.hasNext()) {
                    UserBean userBean = (UserBean)var4.next();
                    if (userName.equals(userBean.getUserName())) {
                        return userBean;
                    }
                }
            }

            return null;
        } else {
            throw new IllegalArgumentException("The user name cannot be null or empty");
        }
    }

    public List<UserBean> getUsersWithCompatibleEnvironments(PlatformEnvironment platformEnvironment, String userName) {
        ArrayList foundUsers = new ArrayList();

        try {
            if (platformEnvironment == null) {
                throw new IllegalArgumentException("The platform environment cannot be null");
            }

            if (userName == null || userName.isEmpty()) {
                throw new IllegalArgumentException("The user name cannot be null or empty");
            }

            UserBean userBean;
            if (platformEnvironment != PlatformEnvironment.DEMO && platformEnvironment != PlatformEnvironment.DEMO_3) {
                if (platformEnvironment != PlatformEnvironment.LIVE && platformEnvironment != PlatformEnvironment.LIVE_3) {
                    userBean = this.getUser(platformEnvironment, userName);
                    if (userBean != null) {
                        foundUsers.add(userBean);
                    }
                } else {
                    userBean = null;
                    userBean = this.getUser(PlatformEnvironment.LIVE, userName);
                    if (userBean != null) {
                        foundUsers.add(userBean);
                    }

                    userBean = this.getUser(PlatformEnvironment.LIVE_3, userName);
                    if (userBean != null) {
                        foundUsers.add(userBean);
                    }
                }
            } else {
                userBean = null;
                userBean = this.getUser(PlatformEnvironment.DEMO, userName);
                if (userBean != null) {
                    foundUsers.add(userBean);
                }

                userBean = this.getUser(PlatformEnvironment.DEMO_3, userName);
                if (userBean != null) {
                    foundUsers.add(userBean);
                }
            }
        } catch (Throwable var5) {
            LOGGER.error(var5.getMessage(), var5);
        }

        return foundUsers;
    }

    public DeviceIDBean getDeviceIDBean() {
        return this.deviceIDBean;
    }

    public void setDeviceIDBean(DeviceIDBean deviceIDBean) {
        this.deviceIDBean = deviceIDBean;
    }

    public DisclaimerBean getDisclaimerBean() {
        return this.disclaimerBean;
    }

    public void setDisclaimerBean(DisclaimerBean disclaimerBean) {
        this.disclaimerBean = disclaimerBean;
    }

    public boolean isDeviceIdExists() {
        return this.deviceIDBean != null && !this.deviceIDBean.isEmpty() && this.deviceIDBean.isDeviceNameAttribute();
    }
}
