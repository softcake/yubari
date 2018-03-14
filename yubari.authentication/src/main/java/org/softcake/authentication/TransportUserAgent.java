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

import org.json.JSONObject;

public class TransportUserAgent {
    private String clientType;
    private String clientVersion;
    private String javaVersion;
    private String jvmVersion;
    private String osName;
    private String ip;
    private String userName;

    public TransportUserAgent() {
    }

    public String getClientVersion() {
        return this.clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getJavaVersion() {
        return this.javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getJvmVersion() {
        return this.jvmVersion;
    }

    public void setJvmVersion(String jvmVersion) {
        this.jvmVersion = jvmVersion;
    }

    public String getOsName() {
        return this.osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getIp() {
        return this.ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getClientType() {
        return this.clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String toJsonString() {
        JSONObject json = new JSONObject(false);
        json.put("clientVersion", this.clientVersion);
        json.put("javaVersion", this.javaVersion);
        json.put("jvmVersion", this.jvmVersion);
        json.put("osName", this.osName);
        json.put("ip", this.ip);
        json.put("userName", this.userName);
        json.put("clientType", this.clientType);
        return json.toString();
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("TransportUserAgent [clientType=");
        builder.append(this.clientType);
        builder.append(", clientVersion=");
        builder.append(this.clientVersion);
        builder.append(", javaVersion=");
        builder.append(this.javaVersion);
        builder.append(", jvmVersion=");
        builder.append(this.jvmVersion);
        builder.append(", osName=");
        builder.append(this.osName);
        builder.append(", ip=");
        builder.append(this.ip);
        builder.append(", userName=");
        builder.append(this.userName);
        builder.append("]");
        return builder.toString();
    }
}
