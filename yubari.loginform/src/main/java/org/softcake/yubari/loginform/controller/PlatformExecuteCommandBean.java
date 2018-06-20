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

import java.util.ArrayList;
import java.util.Arrays;

public class PlatformExecuteCommandBean {
    private String executableFile;
    private ArrayList<String> startPlatformCommand;

    public PlatformExecuteCommandBean() {
    }

    public PlatformExecuteCommandBean(String executableFile, String startPlatformCommand) {
        this(executableFile, new String[]{startPlatformCommand});
    }

    public PlatformExecuteCommandBean(String executableFile, String[] startPlatformCommand) {
        this.executableFile = executableFile;
        this.startPlatformCommand = this.getArrayList(startPlatformCommand);
    }

    public String getExecutableFile() {
        return this.executableFile;
    }

    public void setExecutableFile(String executableFile) {
        this.executableFile = executableFile;
    }

    public String[] getStartPlatformCommand() {
        return this.getArray(this.startPlatformCommand);
    }

    public void setStartPlatformCommand(String command) {
        this.startPlatformCommand = this.getArrayList(new String[]{command});
    }

    public void setStartPlatformCommand(String[] command) {
        this.startPlatformCommand = this.getArrayList(command);
    }

    public void addArgument(String... argumentParts) {
        StringBuilder buf = new StringBuilder(128);
        String[] var3 = argumentParts;
        int var4 = argumentParts.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            String argumentPart = var3[var5];
            buf.append(argumentPart);
        }

        this.startPlatformCommand.add(buf.toString());
    }

    private <T> ArrayList<T> getArrayList(T[] strings) {
        return new ArrayList(Arrays.asList(strings));
    }

    private String[] getArray(ArrayList<String> arrayList) {
        return (String[])arrayList.toArray(new String[arrayList.size()]);
    }
}
