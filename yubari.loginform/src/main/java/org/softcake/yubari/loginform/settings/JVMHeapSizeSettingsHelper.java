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

package org.softcake.yubari.loginform.settings;

import org.softcake.yubari.loginform.controller.ILoginDialogController;
import org.softcake.yubari.loginform.controller.LoginDialogBean;
import org.softcake.yubari.loginform.utils.ObjectUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JVMHeapSizeSettingsHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(JVMHeapSizeSettingsHelper.class);
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String MAX_HEAP_PATTERN = "^-Xmx[0-9]+[mgMG]$";
    public static final String JVM_OPTIONS_FILE_NAME = "JForex.vmoptions";
    private static final String jvmOptionsFileName;

    public JVMHeapSizeSettingsHelper() {
    }

    public static boolean isJVMMaxHeapSizeExist() {
        boolean exist = false;

        try {
            List<String> jvmOptionsContent = getJVMOptionsContent(jvmOptionsFileName);
            Iterator var2 = jvmOptionsContent.iterator();

            while(var2.hasNext()) {
                String text = (String)var2.next();
                text = text.trim();
                boolean matches = matches(text, "^-Xmx[0-9]+[mgMG]$");
                if (matches) {
                    exist = true;
                    break;
                }
            }
        } catch (Throwable var5) {
            LOGGER.error(var5.getMessage(), var5);
        }

        return exist;
    }

    public static String getJVMMaxHeapSize() {
        String jvmMaxHeapSize = null;

        try {
            List<String> jvmOptionsContent = getJVMOptionsContent(jvmOptionsFileName);
            Iterator var2 = jvmOptionsContent.iterator();

            while(var2.hasNext()) {
                String text = (String)var2.next();
                text = text.trim();
                boolean matches = matches(text, "^-Xmx[0-9]+[mgMG]$");
                if (matches) {
                    jvmMaxHeapSize = text;
                    break;
                }
            }
        } catch (Exception var5) {
            LOGGER.error(var5.getMessage(), var5);
        }

        return jvmMaxHeapSize;
    }

    public static void saveJVMMaxHeapSize(String newMaxHeapSize) {
        if (newMaxHeapSize != null) {
            newMaxHeapSize = newMaxHeapSize.trim();
        }

        if (ObjectUtils.isNullOrEmpty(newMaxHeapSize)) {
            deleteJVMMaxHeapSize();
            return;
        }
        boolean heapSizeChanged = false;
        File outputFile = new File(jvmOptionsFileName);
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<String> jvmOptionsContent = getJVMOptionsContent(jvmOptionsFileName);



        try( BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));) {







            for(int i = 0; i < jvmOptionsContent.size(); ++i) {
                String currentLine = (String)jvmOptionsContent.get(i);
                String temp = currentLine.trim();
                if (matches(temp, "^-Xmx[0-9]+[mgMG]$")) {
                    currentLine = newMaxHeapSize;
                    heapSizeChanged = true;
                }

                bw.write(currentLine);
                if (i < jvmOptionsContent.size() - 1) {
                    bw.write(LINE_SEPARATOR);
                }
            }

            if (!heapSizeChanged) {
                if (jvmOptionsContent.size() > 0) {
                    bw.write(LINE_SEPARATOR);
                }

                bw.write(newMaxHeapSize);
            }


        } catch (Throwable var9) {
            LOGGER.error(var9.getMessage(), var9);
        }

    }

    public static void deleteJVMMaxHeapSize() {

        File outputFile = new File(jvmOptionsFileName);
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<String> jvmOptionsContent = getJVMOptionsContent(jvmOptionsFileName);

        try(BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {






            for(int i = 0; i < jvmOptionsContent.size(); ++i) {
                String currentLine = (String)jvmOptionsContent.get(i);
                String temp = currentLine.trim();
                if (!matches(temp, "^-Xmx[0-9]+[mgMG]$")) {
                    bw.write(currentLine);
                    if (i < jvmOptionsContent.size() - 1) {
                        bw.write(LINE_SEPARATOR);
                    }
                }
            }


        } catch (Throwable var7) {
            LOGGER.error(var7.getMessage(), var7);
        }

    }

    private static boolean matches(String text, String pattern) {
        boolean matches = false;

        try {
            matches = text.matches(pattern);
        } catch (Throwable var4) {
            LOGGER.error(var4.getMessage(), var4);
        }

        return matches;
    }

    public static void checkAndSaveMaxMemoryHeap(ILoginDialogController loginDialogController) {
        try {
            String maxMemoryHeapJVMParameter = null;
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            List<String> inputArguments = bean.getInputArguments();
            Iterator var4 = inputArguments.iterator();

            while(var4.hasNext()) {
                String inputArgument = (String)var4.next();
                boolean matches = matches(inputArgument, "^-Xmx[0-9]+[mgMG]$");
                if (matches) {
                    maxMemoryHeapJVMParameter = inputArgument;
                    break;
                }
            }

            LoginDialogBean loginDialogBean = loginDialogController.getLoginDialogBean();
            if (ObjectUtils.isNullOrEmpty(maxMemoryHeapJVMParameter)) {
                if (!isJVMMaxHeapSizeExist()) {
                    if (!loginDialogBean.isUseSystemMaxHeapSize()) {
                        loginDialogBean.setUseSystemMaxHeapSize(true);
                    }
                } else {
                    loginDialogBean.setUseSystemMaxHeapSize(false);
                }
            } else {
                saveJVMMaxHeapSize(maxMemoryHeapJVMParameter);
                loginDialogBean.setMaxHeapSize(maxMemoryHeapJVMParameter);
                loginDialogBean.setUseSystemMaxHeapSize(false);
            }
        } catch (Exception var7) {
            LOGGER.error(var7.getMessage(), var7);
        }

    }

    private static List<String> getJVMOptionsContent(String fileName) {
        final List<String> lines = new ArrayList<>();
        BufferedReader br = null;
        String line = null;


        try {
            File jvmOptionsFile = new File(fileName);
            if (jvmOptionsFile.exists()) {
                br = new BufferedReader(new FileReader(fileName));

                while((line = br.readLine()) != null) {
                    lines.add(line);
                }

                return lines;
            }

        } catch (Throwable var16) {
            LOGGER.error(var16.getMessage(), var16);
            return lines;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Exception var15) {
                LOGGER.error(var15.getMessage(), var15);
            }

        }

        return new ArrayList<>();
    }

    static {
        Path currentRelativePath = Paths.get("");
        String currentAbsolutePath = currentRelativePath.toAbsolutePath().toString();
        jvmOptionsFileName = currentAbsolutePath + File.separator + "JForex.vmoptions";
    }
}
