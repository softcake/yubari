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

import org.softcake.yubari.loginform.ui.helper.PlatformSpecific;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import javax.swing.filechooser.FileSystemView;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilePathManager implements IFilePathManager {
    private static Logger LOGGER = LoggerFactory.getLogger(FilePathManager.class);
    private static IFilePathManager instance;
    public static String WORKSPACE_LOCK_FILE_NAME = "workspace.lck";
    public static String DEFAULT_WORKSPACE_FILE_NAME = "Workspace.xml";
    public static String SYSTEM_SETTING_FILE_NAME = "system_settings.xml";
    private static String LOCAL_SETTINGS_FOLDER_NAME = "Local Settings";
    private static String MAC_OS_APP_SUPPORT_REL_PATH = "Library/Application Support/";
    public static String JFOREX_FOLDER_NAME = "JForex";
    public static String DEFAULT_WORKSPACES_FOLDER_NAME = "Workspaces";
    public static String DEFAULT_STRATEGIES_FOLDER_NAME = "Strategies";
    public static String DEFAULT_VJF_STRATEGIES_FOLDER_NAME = "VJFStrategies";
    public static String DEFAULT_TEMPLATES_FOLDER_NAME = "Templates";
    public static String DEFAULT_INDICATORS_FOLDER_NAME = "Indicators";
    public static String DEFAULT_PLUGINS_FOLDER_NAME = "Plugins";
    public static String DEFAULT_CACHE_FOLDER_NAME = ".cache";
    public static String DEFAULT_SYSTEM_SETTINGS_FOLDER_NAME = ".settings";
    public static String FILES = "files";
    private static String TEMP_FOLDER_NAME = "temp";
    private static String METADATA_DIR_NAME = "metadata";
    private static String HISTORY_START_DIR_NAME = "historystart";
    private static String WINDOWS_SYS_ENV_APPDATA = "APPDATA";
    private static String WINDOWS_SYS_ENV_LOCALAPPDATA = "LOCALAPPDATA";
    private static String WINDOWS_SYS_ENV_USERPROFILE = "USERPROFILE";
    private static String WINDOWS_SYS_LOCALAPPDATA_REL_PATH = "AppData\\Local";
    private static String WINDOWS_SYS_APPDATA_REL_PATH = "AppData\\Roaming";
    private String systemSettingsFolderPath;
    private String workspacesFolderPath;
    private String workspacesFileName;
    private String strategiesFolderPath;
    private String templatesFolderPath;
    private String indicatorsFolderPath;
    private String pluginsFolderPath;
    private String cacheFolderPath;
    private String cacheTempFolderPath;
    private String cachedCacheDirectory;
    private boolean foldersAccessible;
    private String defaultAppFolder;

    private FilePathManager() {
    }

    public static IFilePathManager getInstance() {
        if (instance == null) {
            reset();
        }

        return instance;
    }

    public String getSystemSettingsFilePath() {
        return this.getSystemSettingsFolderPath() == null ? this.getDefaultSystemSettingsFilePath() : this.getSystemSettingsFolderPath() + this.getPathSeparator() + SYSTEM_SETTING_FILE_NAME;
    }

    public String getSystemSettingsFolderPath() {
        return this.systemSettingsFolderPath;
    }

    public void setSystemSettingsFolderPath(String systemSettingsFolderPath) {
        this.systemSettingsFolderPath = systemSettingsFolderPath;
    }

    private String getCacheFolderPath() {
        return this.cacheFolderPath;
    }

    public void setCacheFolderPath(String cacheFolderPath) {
        this.cachedCacheDirectory = null;
        this.cacheFolderPath = cacheFolderPath;
    }

    public void setCacheTempFolderPath(String cacheTempFolderPath) {
        this.cacheTempFolderPath = cacheTempFolderPath;
    }

    public boolean isFoldersAccessible() {
        return this.foldersAccessible;
    }

    public void setFoldersAccessible(boolean foldersAccessible) {
        this.foldersAccessible = foldersAccessible;
    }

    public String getWorkspacesFolderPath() {
        return this.workspacesFolderPath;
    }

    public void setWorkspacesFolderPath(String workspacesFolderPath) {
        this.workspacesFolderPath = workspacesFolderPath;
    }

    public String getStrategiesFolderPath() {
        return this.strategiesFolderPath;
    }

    public void setStrategiesFolderPath(String strategiesFolderPath) {
        this.strategiesFolderPath = strategiesFolderPath;
    }

    public String getTemplatesFolderPath() {
        return this.templatesFolderPath;
    }

    public void setTemplatesFolderPath(String templatesFolderPath) {
        this.templatesFolderPath = templatesFolderPath;
    }

    public String getIndicatorsFolderPath() {
        return this.indicatorsFolderPath;
    }

    public void setIndicatorsFolderPath(String indicatorsFolderPath) {
        this.indicatorsFolderPath = indicatorsFolderPath;
    }

    public String getPathSeparator() {
        return File.separator;
    }

    public char getPathSeparatorChar() {
        return File.separatorChar;
    }

    public synchronized String getDefaultUserDocumentsFolderPath() {
        return FileSystemView.getFileSystemView().getDefaultDirectory().getPath();
    }

    public synchronized String getDefaultTempFolderPath() {
        if (this.defaultAppFolder == null || this.defaultAppFolder.trim().isEmpty()) {
            String defaultTempFolderPath = this.getDefaultAppFolder();
            defaultTempFolderPath = this.validateDefaultFolder(defaultTempFolderPath, this.getOldDefaultTempFolderPath());
            this.defaultAppFolder = defaultTempFolderPath;
        }

        return this.defaultAppFolder;
    }

    private String getDefaultAppFolder() {
        String defaultTempAppFolder = SystemUtils.USER_HOME;
        if (SystemUtils.IS_OS_MAC) {
            defaultTempAppFolder = this.addPathSeparatorIfNeeded(defaultTempAppFolder);
            defaultTempAppFolder = defaultTempAppFolder + MAC_OS_APP_SUPPORT_REL_PATH;
        } else if (SystemUtils.IS_OS_WINDOWS) {
            String windowsVersion = SystemUtils.OS_VERSION;
            String majorVersion = windowsVersion.split("\\.")[0];
            int version = Integer.parseInt(majorVersion);
            String windowsDefaultAppFolder;
            if (version >= 6) {
                windowsDefaultAppFolder = System.getenv(WINDOWS_SYS_ENV_LOCALAPPDATA);
            } else {
                windowsDefaultAppFolder = System.getenv(WINDOWS_SYS_ENV_APPDATA);
            }

            if (windowsDefaultAppFolder == null) {
                windowsDefaultAppFolder = System.getenv(WINDOWS_SYS_ENV_USERPROFILE);
                if (windowsDefaultAppFolder != null) {
                    windowsDefaultAppFolder = this.addPathSeparatorIfNeeded(windowsDefaultAppFolder);
                    if (version >= 6) {
                        windowsDefaultAppFolder = windowsDefaultAppFolder + WINDOWS_SYS_LOCALAPPDATA_REL_PATH;
                    } else {
                        windowsDefaultAppFolder = windowsDefaultAppFolder + WINDOWS_SYS_APPDATA_REL_PATH;
                    }
                }
            }

            if (windowsDefaultAppFolder != null) {
                defaultTempAppFolder = windowsDefaultAppFolder;
            }
        }

        defaultTempAppFolder = this.addPathSeparatorIfNeeded(defaultTempAppFolder);
        defaultTempAppFolder = defaultTempAppFolder + this.getPlatformsFolderName();
        LOGGER.info("Login data folder: " + defaultTempAppFolder);
        return defaultTempAppFolder;
    }

    private String validateDefaultFolder(String defaultFolder, String oldDefaultFolder) {
        if (!ObjectUtils.isEqual(defaultFolder, oldDefaultFolder)) {
            Path defaultFolderPath = Paths.get(defaultFolder);
            if (!Files.isDirectory(defaultFolderPath, new LinkOption[0])) {
                ;
            }

            if (!this.isFolderAccessible(defaultFolder)) {
                defaultFolder = oldDefaultFolder;
            }
        }

        return defaultFolder;
    }

    private String getOldDefaultTempFolderPath() {
        String defaultTempFolderPath = SystemUtils.USER_HOME;
        if (PlatformSpecific.WINDOWS) {
            defaultTempFolderPath = this.addPathSeparatorIfNeeded(defaultTempFolderPath);
            defaultTempFolderPath = defaultTempFolderPath + LOCAL_SETTINGS_FOLDER_NAME;
        }

        defaultTempFolderPath = this.addPathSeparatorIfNeeded(defaultTempFolderPath);
        defaultTempFolderPath = defaultTempFolderPath + this.getPlatformsFolderName();
        return defaultTempFolderPath;
    }

    public String addPathSeparatorIfNeeded(String path) {
        if (path == null) {
            return null;
        } else if (path.isEmpty()) {
            return this.getPathSeparator();
        } else {
            return path.charAt(path.length() - 1) != this.getPathSeparatorChar() ? path + this.getPathSeparatorChar() : path;
        }
    }

    public String getDefaultJForexFolderPath() {
        String defaultJForexFolder = this.defaultAppFolder;
        if (this.defaultAppFolder == null || this.defaultAppFolder.trim().isEmpty()) {
            defaultJForexFolder = this.getDefaultAppFolder();
            defaultJForexFolder = this.validateDefaultFolder(defaultJForexFolder, this.getOldDefaultJForexFolderPath());
            this.defaultAppFolder = defaultJForexFolder;
        }

        return this.defaultAppFolder;
    }

    private String getOldDefaultJForexFolderPath() {
        return this.getDefaultUserDocumentsFolderPath() + this.getPathSeparator() + this.getPlatformsFolderName();
    }

    private String getDefaultFolder(String clientMode, String userAccountId, String folder) {
        return this.getDefaultJForexFolderPath() + this.getPathSeparator() + folder + this.getPathSeparator() + clientMode + this.getPathSeparator() + userAccountId;
    }

    private String getDefaultFolder(String folder) {
        return this.getDefaultJForexFolderPath() + this.getPathSeparator() + folder;
    }

    public String getDefaultWorkspaceFolderPath(String clientMode, String userAccountId) {
        return this.getDefaultFolder(clientMode, userAccountId, DEFAULT_WORKSPACES_FOLDER_NAME);
    }

    public String getDefaultStrategiesFolderPath() {
        return this.getDefaultFolder(DEFAULT_STRATEGIES_FOLDER_NAME);
    }

    public String getDefaultVJFStrategiesFolderPath() {
        return this.getDefaultFolder(DEFAULT_VJF_STRATEGIES_FOLDER_NAME);
    }

    public String getDefaultIndicatorsFolderPath() {
        return this.getDefaultFolder(DEFAULT_INDICATORS_FOLDER_NAME);
    }

    public String getDefaultTemplatesFolderPath() {
        return this.getDefaultFolder(DEFAULT_TEMPLATES_FOLDER_NAME);
    }

    public String getDefaultCacheFolderPath() {
        return this.getDefaultTempFolderPath() + this.getPathSeparator() + DEFAULT_CACHE_FOLDER_NAME;
    }

    public String getDefaultSystemSettingsFolderPath() {
        return this.getDefaultTempFolderPath() + this.getPathSeparator() + DEFAULT_SYSTEM_SETTINGS_FOLDER_NAME;
    }

    public String getDefaultSystemSettingsFilePath() {
        return this.getDefaultSystemSettingsFolderPath() + this.getPathSeparator() + SYSTEM_SETTING_FILE_NAME;
    }

    public String getDefaultWorkspaceSettingsFilePath(String clientMode, String userAccountId) {
        return this.getDefaultWorkspaceFolderPath(clientMode, userAccountId) + this.getPathSeparator() + DEFAULT_WORKSPACE_FILE_NAME;
    }

    public String getAlternativeDefaultWorkspaceSettingsFilePath(String clientMode, String userAccountId) {
        return this.getAlternativeDefaultWorkspacesFolderPath(clientMode, userAccountId) + this.getPathSeparator() + DEFAULT_WORKSPACE_FILE_NAME;
    }

    private String getRootForAlternativePathes() {
        return "";
    }

    private String getAlternativeUserDocumentsFolderPath() {
        return this.getRootForAlternativePathes();
    }

    private String getAlternativeTempFolderPath() {
        return this.getRootForAlternativePathes() + this.getPathSeparator() + TEMP_FOLDER_NAME;
    }

    public String getAlternativeJForexFolderPath() {
        return this.getAlternativeUserDocumentsFolderPath() + this.getPathSeparator() + this.getPlatformsFolderName();
    }

    private String getPlatformsFolderName() {
        return JFOREX_FOLDER_NAME;
    }

    private String getAlternateDefaultFolder(String clientMode, String userAccountId, String folder) {
        return this.getAlternativeJForexFolderPath() + this.getPathSeparator() + folder + this.getPathSeparator() + clientMode + this.getPathSeparator() + userAccountId;
    }

    private String getAlternateDefaultFolder(String folder) {
        return this.getAlternativeJForexFolderPath() + this.getPathSeparator() + folder;
    }

    public String getAlternativeDefaultWorkspacesFolderPath(String clientMode, String userAccountId) {
        return this.getAlternateDefaultFolder(clientMode, userAccountId, DEFAULT_WORKSPACES_FOLDER_NAME);
    }

    public String getAlternativeDefaultTemplatesFolderPath() {
        return this.getAlternateDefaultFolder(DEFAULT_TEMPLATES_FOLDER_NAME);
    }

    public String getAlternativeDefaultIndicatorsFolderPath() {
        return this.getAlternativeJForexFolderPath();
    }

    public String getAlternativeStrategiesFolderPath() {
        return this.getAlternativeJForexFolderPath();
    }

    public String getAlternativeVJFStrategiesFolderPath() {
        return this.getAlternativeJForexFolderPath();
    }

    public String getAlternativeSystemSettingsFolderPath() {
        return this.getAlternativeTempFolderPath() + this.getPathSeparator() + DEFAULT_SYSTEM_SETTINGS_FOLDER_NAME;
    }

    public String getAlternativeSystemSettingsFilePath() {
        return this.getAlternativeSystemSettingsFolderPath() + this.getPathSeparator() + SYSTEM_SETTING_FILE_NAME;
    }

    public String getAlternativeCacheFolderPath() {
        return this.getAlternativeTempFolderPath() + this.getPathSeparator() + DEFAULT_CACHE_FOLDER_NAME;
    }

    public boolean isFileFolderAccessible(String filePath) {
        File file = new File(filePath);
        return this.isFolderAccessible(file.getParent());
    }

    public boolean isFolderAccessible(String folderPath) {
        try {
            File folder = this.checkAndGetFolder(folderPath);
            if (folder == null) {
                return false;
            } else {
                Random r = new Random();
                File file = null;

                for(String fileName = folder.getAbsolutePath() + this.getPathSeparator(); file == null || file.exists(); file = new File(fileName)) {
                    fileName = fileName + String.valueOf(Math.abs(r.nextInt()));
                }

                if (file.createNewFile()) {
                    file.delete();
                    return true;
                } else {
                    file.delete();
                    return false;
                }
            }
        } catch (Throwable var6) {
            return false;
        }
    }

    public void checkFolderStructure(String path) {
        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }

    }

    public File checkAndGetFolder(String path) {
        if (path == null) {
            return null;
        } else {
            File file = new File(path);
            if (!file.exists()) {
                file.mkdirs();
            }

            return file;
        }
    }

    public File getIndicatorsFolder() {
        return this.checkAndGetFolder(this.getIndicatorsFolderPath());
    }

    public File getStrategiesFolder() {
        return this.checkAndGetFolder(this.getStrategiesFolderPath());
    }

    public File getFilesForStrategiesDir() {
        File folder = this.getStrategiesFolder();
        return folder == null ? null : this.checkAndGetFolder(folder.getAbsolutePath() + this.getPathSeparator() + FILES);
    }

    public File getWorkspacesFolder() {
        return this.checkAndGetFolder(this.getWorkspacesFolderPath());
    }

    public File getTemplatesFolder() {
        return this.checkAndGetFolder(this.getTemplatesFolderPath());
    }

    public String getCacheDirectory() {
        if (this.cachedCacheDirectory == null) {
            String cacheDirectory = this.getCacheFolderPath();
            if (cacheDirectory == null) {
                cacheDirectory = this.getDefaultCacheFolderPath();
            }

            if (!this.foldersAccessible && !this.isFolderAccessible(cacheDirectory)) {
                cacheDirectory = this.getAlternativeCacheFolderPath();
            }

            String result = cacheDirectory;

            try {
                result = (new File(cacheDirectory)).getCanonicalPath();
                result = this.addPathSeparatorIfNeeded(result);
            } catch (Throwable var4) {
                ;
            }

            this.cachedCacheDirectory = result;
            return result;
        } else {
            return this.cachedCacheDirectory;
        }
    }

    public String getCacheMetadataDirectory() {
        StringBuilder fileName = new StringBuilder(this.getCacheDirectory());
        fileName.append(File.separatorChar);
        fileName.append(METADATA_DIR_NAME);
        return fileName.toString();
    }

    public String getCacheMetadataFirstTimesDirectory() {
        StringBuilder fileName = new StringBuilder(this.getCacheMetadataDirectory());
        fileName.append(File.separatorChar);
        fileName.append(HISTORY_START_DIR_NAME);
        String directoryName = fileName.toString();
        File directory = new File(directoryName);
        directory.mkdirs();
        return directoryName;
    }

    public String getCacheTempDirectory() {
        return this.cacheTempFolderPath == null ? this.getCacheDirectory() + File.separatorChar + "jftemp" : this.cacheTempFolderPath;
    }

    public static void main(String[] str) {
        String temp = "c:\\My Documents\\App data\\JForex\\";
        getInstance().setCacheFolderPath(temp + ".cache");
        getInstance().setIndicatorsFolderPath(temp + "Indicators");
        getInstance().setStrategiesFolderPath(temp + "Strategies");
        getInstance().setSystemSettingsFolderPath(temp + ".forex");
        getInstance().setTemplatesFolderPath(temp + "Templates");
        getInstance().setWorkspacesFolderPath(temp + "Workspaces");
        Method[] var2 = FilePathManager.class.getMethods();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Method m = var2[var4];
            if (m.getName().startsWith("get")) {
                try {
                    System.out.println(m.getName() + " - " + m.invoke(getInstance(), (Object[])null));
                } catch (IllegalArgumentException var7) {
                    var7.printStackTrace();
                } catch (IllegalAccessException var8) {
                    var8.printStackTrace();
                } catch (InvocationTargetException var9) {
                    var9.printStackTrace();
                }
            }
        }

    }

    public String getWorkspacesFileName() {
        return this.workspacesFileName;
    }

    public void setWorkspacesFileName(String workspacesFileName) {
        this.workspacesFileName = workspacesFileName;
    }

    public static void reset() {
        instance = new FilePathManager();
    }

    public String getDefaultPluginsFolderPath() {
        return this.getDefaultFolder(DEFAULT_PLUGINS_FOLDER_NAME);
    }

    public void setPluginsFolderPath(String myPluginsPath) {
        this.pluginsFolderPath = myPluginsPath;
    }

    public String getPluginsFolderPath() {
        return this.pluginsFolderPath;
    }

    public String getAlternativeDefaultPluginsFolderPath() {
        return this.getAlternativeJForexFolderPath();
    }
}
