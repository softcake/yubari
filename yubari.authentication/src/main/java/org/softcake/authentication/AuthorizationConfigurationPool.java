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

package org.softcake.authentication;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;


public class AuthorizationConfigurationPool {
    private LinkedList<URL> authServerList = new LinkedList();

    public AuthorizationConfigurationPool() {
    }

    public int size() {
        return this.authServerList.size();
    }

    public void add(String link) throws MalformedURLException {
        this.authServerList.addLast(new URL(link));
    }

    public URL get() {
        return this.authServerList.getFirst();
    }

    public void markLastUsedAsBad() {
        this.authServerList.addLast(this.authServerList.pop());
    }

    public void clear() {
        this.authServerList.clear();
    }
}
