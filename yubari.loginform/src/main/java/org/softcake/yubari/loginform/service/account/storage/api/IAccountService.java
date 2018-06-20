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

package org.softcake.yubari.loginform.service.account.storage.api;

import org.softcake.yubari.loginform.controller.PlatformEnvironment;
import org.softcake.yubari.loginform.service.account.DeviceIDBean;
import org.softcake.yubari.loginform.service.account.UserBean;
import org.softcake.yubari.loginform.service.account.Users;


public interface IAccountService {
    void saveUsers(Users var1) throws Exception;

    Users loadUsers();

    UserBean getLastLoggedInUserAsRememberMe(PlatformEnvironment var1);

    UserBean getLastLoggedInUser(PlatformEnvironment var1);

    DeviceIDBean getDeviceIDBean();

    String getId();

    boolean isRememberMeSupported();

    boolean saveAuthorizedUser(PlatformEnvironment var1, String var2);

    boolean saveAuthorizedUser(String var1, String var2);

    boolean saveAuthorizedUser(PlatformEnvironment var1, String var2, boolean var3, String var4, int var5);

    boolean saveAuthorizedUser(String var1, String var2, boolean var3, String var4, int var5);

    boolean deleteUser(PlatformEnvironment var1, String var2);

    boolean deleteUser(String var1, String var2);

    boolean deleteRememberMeToken(PlatformEnvironment var1, String var2);

    boolean deleteRememberMeToken(String var1, String var2);

    boolean isRememberMeAuthorizationPossible(String var1, String var2);

    boolean isRememberMeAuthorizationPossible(String var1, PlatformEnvironment var2);

    UserBean getUserBean(String var1, String var2);

    UserBean getUserBean(String var1, PlatformEnvironment var2);

    boolean encodeUserNames();
}
