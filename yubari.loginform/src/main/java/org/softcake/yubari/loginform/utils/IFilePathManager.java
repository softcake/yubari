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

package org.softcake.yubari.loginform.utils;

import java.io.File;

public interface IFilePathManager {
    String getSystemSettingsFilePath();

    String getSystemSettingsFolderPath();

    void setSystemSettingsFolderPath(String var1);

    void setCacheFolderPath(String var1);

    void setCacheTempFolderPath(String var1);

    boolean isFoldersAccessible();

    void setFoldersAccessible(boolean var1);

    String getWorkspacesFolderPath();

    void setWorkspacesFolderPath(String var1);

    String getStrategiesFolderPath();

    void setStrategiesFolderPath(String var1);

    String getTemplatesFolderPath();

    void setTemplatesFolderPath(String var1);

    String getIndicatorsFolderPath();

    void setIndicatorsFolderPath(String var1);

    String getPathSeparator();

    char getPathSeparatorChar();

    String getDefaultUserDocumentsFolderPath();

    String getDefaultTempFolderPath();

    String addPathSeparatorIfNeeded(String var1);

    String getDefaultJForexFolderPath();

    String getDefaultWorkspaceFolderPath(String var1, String var2);

    String getDefaultStrategiesFolderPath();

    String getDefaultVJFStrategiesFolderPath();

    String getDefaultIndicatorsFolderPath();

    String getDefaultTemplatesFolderPath();

    String getDefaultCacheFolderPath();

    String getDefaultSystemSettingsFolderPath();

    String getDefaultSystemSettingsFilePath();

    String getDefaultWorkspaceSettingsFilePath(String var1, String var2);

    String getAlternativeDefaultWorkspaceSettingsFilePath(String var1, String var2);

    String getAlternativeDefaultWorkspacesFolderPath(String var1, String var2);

    String getAlternativeDefaultTemplatesFolderPath();

    String getAlternativeDefaultIndicatorsFolderPath();

    String getAlternativeStrategiesFolderPath();

    String getAlternativeVJFStrategiesFolderPath();

    String getAlternativeSystemSettingsFolderPath();

    String getAlternativeSystemSettingsFilePath();

    String getAlternativeCacheFolderPath();

    boolean isFileFolderAccessible(String var1);

    boolean isFolderAccessible(String var1);

    void checkFolderStructure(String var1);

    File checkAndGetFolder(String var1);

    File getIndicatorsFolder();

    File getStrategiesFolder();

    File getFilesForStrategiesDir();

    File getWorkspacesFolder();

    File getTemplatesFolder();

    String getCacheDirectory();

    String getCacheMetadataDirectory();

    String getCacheMetadataFirstTimesDirectory();

    String getCacheTempDirectory();

    String getWorkspacesFileName();

    void setWorkspacesFileName(String var1);

    String getDefaultPluginsFolderPath();

    void setPluginsFolderPath(String var1);

    String getPluginsFolderPath();

    String getAlternativeDefaultPluginsFolderPath();
}
