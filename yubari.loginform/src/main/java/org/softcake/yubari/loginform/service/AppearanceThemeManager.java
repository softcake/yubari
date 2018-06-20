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

package org.softcake.yubari.loginform.service;

import org.softcake.yubari.loginform.controller.Language;
import org.softcake.yubari.loginform.localization.LocalizationManager;
import org.softcake.yubari.loginform.ui.components.IAppearanceThemeObservable;
import org.softcake.yubari.loginform.ui.laf.CustomUI;
import org.softcake.yubari.loginform.ui.laf.Style;
import org.softcake.yubari.loginform.ui.laf.Theme;
import org.softcake.yubari.loginform.ui.laf.ThemeLoader;
import org.softcake.yubari.loginform.util.OperatingSystemType;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.UIManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppearanceThemeManager implements IAppearanceThemeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppearanceThemeManager.class);
    private final List<WeakReference<IAppearanceThemeObservable>> customListeners = new LinkedList();
    private IAppearanceThemesSettingsStorage settingsStorage;
    private ZoomMode currentZoomMode;
    private AppearanceTheme currentTheme;
    private Map<String, Font> fontCache;

    public AppearanceThemeManager() {
        this.currentZoomMode = ZoomMode.STANDARD;
        this.currentTheme = AppearanceTheme.DEFAULT;
        this.fontCache = new HashMap();
    }

    public void loadInitialLookAndFeel(IAppearanceThemesSettingsStorage settingsStorage) {
        this.settingsStorage = settingsStorage;
        this.currentZoomMode = settingsStorage.loadZoomMode();

        try {
            String nameOrPath = settingsStorage.loadAppearanceThemeNameOrPath();
            String filePath;
            if (nameOrPath == null) {
                this.currentTheme = AppearanceTheme.DEFAULT;
                filePath = "loginform/res/themes/" + AppearanceTheme.DEFAULT.getFileName();
            } else {
                try {
                    this.currentTheme = AppearanceTheme.valueOf(nameOrPath);
                    filePath = "loginform/res/themes/" + this.currentTheme.getFileName();
                } catch (IllegalArgumentException var6) {
                    this.currentTheme = AppearanceTheme.CUSTOM;
                    filePath = nameOrPath;
                }
            }

            try {
                UIManager.setLookAndFeel(OperatingSystemType.LINUX ? UIManager.getCrossPlatformLookAndFeelClassName() : UIManager.getSystemLookAndFeelClassName());
            } catch (Exception var5) {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            }

            this.loadThemeFile(filePath);
            UIManager.put("ScrollBar.width", 15);
            UIManager.put("Tree.drawDashedFocusIndicator", false);
            UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
            UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
            UIManager.put("ProgressBar.repaintInterval", 50);
            UIManager.put("ProgressBar.cycleTime", 5000);
            UIManager.put("ComboBox.squareButton", false);
            UIManager.put("Table.dropCellForeground", (Object)null);
            UIManager.put("Table.dropCellBackground", (Object)null);
            UIManager.put("Table.dropLineColor", new Color(0, 0, 0, 0));
            UIManager.put("Table.dropLineShortColor", new Color(0, 0, 0, 0));
            UIManager.put("MenuItem.acceleratorDelimiter", "+");
            Toolkit.getDefaultToolkit().setDynamicLayout(true);
        } catch (Exception var7) {
            LOGGER.error("Failed to initialize UI Look and Feel", var7);
        }

    }

    private void loadThemeFile(String filePath) {
        ClassLoader contextClassLoader = this.getClass().getClassLoader();
        ThemeLoader themeLoader = new ThemeLoader();
        List<Theme> themes = null;
        Throwable var70;
        if (this.currentTheme == AppearanceTheme.CUSTOM) {
            try {
                InputStream themeFileStream = new FileInputStream(filePath);
                var70 = null;

                try {
                    themes = themeLoader.loadThemes(themeFileStream);
                } catch (Throwable var61) {
                    var70 = var61;
                    throw var61;
                } finally {
                    if (themeFileStream != null) {
                        if (var70 != null) {
                            try {
                                themeFileStream.close();
                            } catch (Throwable var58) {
                                var70.addSuppressed(var58);
                            }
                        } else {
                            themeFileStream.close();
                        }
                    }

                }
            } catch (IOException var65) {
                this.currentTheme = AppearanceTheme.DEFAULT;

                try {
                    InputStream themeFileStream = contextClassLoader.getResourceAsStream("loginform/res/themes/" + AppearanceTheme.DEFAULT.getFileName());
                    Throwable var7 = null;

                    try {
                        themes = themeLoader.loadThemes(themeFileStream);
                    } catch (Throwable var57) {
                        var7 = var57;
                        throw var57;
                    } finally {
                        if (themeFileStream != null) {
                            if (var7 != null) {
                                try {
                                    themeFileStream.close();
                                } catch (Throwable var56) {
                                    var7.addSuppressed(var56);
                                }
                            } else {
                                themeFileStream.close();
                            }
                        }

                    }
                } catch (IOException var63) {
                    ;
                }

                this.settingsStorage.putAppearanceThemeNameOrPath(AppearanceTheme.DEFAULT.name());
                LOGGER.error("Cannot find theme file: " + filePath + ".\nLoading default theme");
            }
        } else {
            try {
                InputStream themeFileStream = contextClassLoader.getResourceAsStream(filePath);
                var70 = null;

                try {
                    themes = themeLoader.loadThemes(themeFileStream);
                } catch (Throwable var60) {
                    var70 = var60;
                    throw var60;
                } finally {
                    if (themeFileStream != null) {
                        if (var70 != null) {
                            try {
                                themeFileStream.close();
                            } catch (Throwable var59) {
                                var70.addSuppressed(var59);
                            }
                        } else {
                            themeFileStream.close();
                        }
                    }

                }
            } catch (IOException var67) {
                LOGGER.error("Cannot load theme file: " + filePath, var67);
            }
        }

        if (themes != null) {
            Iterator var69 = themes.iterator();

            while(var69.hasNext()) {
                Theme theme = (Theme)var69.next();
                List<Style> styles = theme.getStyles();
                Iterator var8 = styles.iterator();

                while(var8.hasNext()) {
                    Style style = (Style)var8.next();
                    Map<String, Object> propertyMap = style.getPropertyMap();
                    Iterator var11 = propertyMap.keySet().iterator();

                    while(var11.hasNext()) {
                        String key = (String)var11.next();
                        UIManager.put(key, propertyMap.get(key));
                    }
                }

                List<CustomUI> customUIs = theme.getCustomUIs();
                Iterator var74 = customUIs.iterator();

                while(var74.hasNext()) {
                    CustomUI customUI = (CustomUI)var74.next();
                    UIManager.put(customUI.getId(), customUI.getUIClass());
                }
            }

        }
    }

    public ZoomMode getCurrentZoomMode() {
        return this.currentZoomMode;
    }

    public AppearanceTheme getCurrentTheme() {
        return this.currentTheme;
    }

    public void changeAppearanceTheme(AppearanceTheme theme, String customFilePath) {
        this.currentTheme = theme;
        String filePath;
        if (theme != AppearanceTheme.CUSTOM) {
            filePath = "loginform/res/themes/" + theme.getFileName();
            this.settingsStorage.putAppearanceThemeNameOrPath(theme.name());
        } else {
            filePath = customFilePath;
            this.settingsStorage.putAppearanceThemeNameOrPath(customFilePath);
        }

        this.loadThemeFile(filePath);
        boolean updateFonts = theme == AppearanceTheme.CUSTOM;
        this.updateAllComponents(true, updateFonts);
    }

    public void changeFontMode(int steps) {
        if (steps != 0) {
            List<ZoomMode> modes = Arrays.asList(ZoomMode.STANDARD, ZoomMode.LARGE_110, ZoomMode.LARGE_125, ZoomMode.LARGE_150);
            int currIdx = modes.indexOf(this.currentZoomMode);
            int resultIdx = currIdx + steps;
            resultIdx = Math.max(0, resultIdx);
            resultIdx = Math.min(modes.size() - 1, resultIdx);
            this.changeZoomMode((ZoomMode)modes.get(resultIdx));
        }
    }

    public void changeZoomMode(ZoomMode zoomMode) {
        this.currentZoomMode = zoomMode;
        this.settingsStorage.putZoomMode(zoomMode);
        this.updateAllComponents(false, true);
    }

    public void addAppearanceThemeChangeListener(IAppearanceThemeObservable listener) {
        this.customListeners.add(new WeakReference(listener));
    }

    public Font getFont(String themePropertyFont) {
        return this.getFont(themePropertyFont, this.getCurrentZoomMode());
    }

    public Font getFont(String themePropertyFont, ZoomMode fontMode) {
        Font font = UIManager.getFont(themePropertyFont);
        font = this.deriveFont(font, fontMode);
        return font;
    }

    public Font deriveFont(Font regularFont) {
        return this.deriveFont(regularFont, this.getCurrentZoomMode());
    }

    public Font deriveFont(Font regularFont, ZoomMode fontMode) {
        Language currLanguage = LocalizationManager.getLanguage();
        if (currLanguage == Language.JAPANESE) {
            regularFont = this.getCachedFont(UIManager.getString("FontName.Japanese"), regularFont.getStyle(), regularFont.getSize());
        } else if (currLanguage != Language.CHINESE && currLanguage != Language.GEORGIAN) {
            if (currLanguage == Language.KOREAN) {
                regularFont = this.getCachedFont(UIManager.getString("FontName.Korean"), regularFont.getStyle(), regularFont.getSize());
            }
        } else {
            regularFont = this.getCachedFont(UIManager.getString("FontName.CJKV"), regularFont.getStyle(), regularFont.getSize());
        }

        if (fontMode != ZoomMode.STANDARD) {
            int newSize = (int)((float)regularFont.getSize() * fontMode.getRatio() + 0.5F);
            regularFont = this.getCachedFont(regularFont.getName(), regularFont.getStyle(), newSize);
        }

        return regularFont;
    }

    private Font getCachedFont(String fontName, int style, int size) {
        String key = fontName + style + "_" + size;
        if (this.fontCache.containsKey(key)) {
            return (Font)this.fontCache.get(key);
        } else {
            Font font = new Font(fontName, style, size);
            this.fontCache.put(key, font);
            return font;
        }
    }

    public void updateFont() {
        this.updateAllComponents(false, true);
    }

    private void updateAllComponents(boolean updateTheme, boolean updateZoomMode) {
        List<WeakReference<IAppearanceThemeObservable>> refs = new ArrayList(this.customListeners);
        Iterator var4 = refs.iterator();

        while(var4.hasNext()) {
            WeakReference<IAppearanceThemeObservable> ref = (WeakReference)var4.next();
            IAppearanceThemeObservable listener = (IAppearanceThemeObservable)ref.get();
            if (listener == null) {
                this.customListeners.remove(ref);
            } else {
                if (listener instanceof Container) {
                    this.processContainerRecursively((Container)listener, updateTheme, updateZoomMode);
                }

                if (updateTheme) {
                    listener.applyNewTheme();
                }

                if (updateZoomMode) {
                    listener.applyZoomMode();
                }
            }
        }

        Window[] windows = Window.getWindows();
        Window[] var10 = windows;
        int var11 = windows.length;

        int var7;
        Window window;
        for(var7 = 0; var7 < var11; ++var7) {
            window = var10[var7];
            if (window instanceof IAppearanceThemeObservable) {
                if (updateTheme) {
                    ((IAppearanceThemeObservable)window).applyNewTheme();
                }

                if (updateZoomMode) {
                    ((IAppearanceThemeObservable)window).applyZoomMode();
                }
            }

            this.processContainerRecursively(window, updateTheme, updateZoomMode);
            if (!OperatingSystemType.MACOSX) {
                window.setBackground(UIManager.getColor("CommonBorder.dialog"));
            }
        }

        var10 = windows;
        var11 = windows.length;

        for(var7 = 0; var7 < var11; ++var7) {
            window = var10[var7];
            window.repaint();
        }

    }

    public void processContainerRecursively(Container container, boolean updateTheme, boolean updateZoomMode) {
        Component[] comps = container.getComponents();
        Component[] var5 = comps;
        int var6 = comps.length;

        for(int var7 = 0; var7 < var6; ++var7) {
            Component comp = var5[var7];
            if (comp instanceof Container) {
                this.processContainerRecursively((Container)comp, updateTheme, updateZoomMode);
                if (comp instanceof JMenu) {
                    this.processContainerRecursively(((JMenu)comp).getPopupMenu(), updateTheme, updateZoomMode);
                }
            }

            if (comp instanceof IAppearanceThemeObservable) {
                if (updateTheme) {
                    ((IAppearanceThemeObservable)comp).applyNewTheme();
                }

                if (updateZoomMode) {
                    ((IAppearanceThemeObservable)comp).applyZoomMode();
                }
            }
        }

    }
}
