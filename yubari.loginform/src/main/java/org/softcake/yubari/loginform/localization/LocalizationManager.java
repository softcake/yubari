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

package org.softcake.yubari.loginform.localization;


import org.softcake.yubari.loginform.controller.Language;


import org.softcake.yubari.loginform.service.IAppearanceThemeManager;
import org.softcake.yubari.loginform.service.UICoreServiceProvider;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Window;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalizationManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalizationManager.class);
    private static final List<WeakReference<Localizable>> CACHE = new ArrayList();
    private static final String RESOURCE_BUNDLE_PATH = "rc/languages/";
    private static Language currentLanguage;
    private static ResourceBundle bundle;
    private static ResourceBundle engBundle;

    public LocalizationManager() {
    }

    private static synchronized void loadBundle() {
        try {
            String languageFileName = "rc/languages/" + currentLanguage.getLocale().getLanguage() + ".properties";
            InputStream inputStream = LocalizationManager.class.getClassLoader().getResourceAsStream(languageFileName);
            if (inputStream == null) {
                throw new MissingResourceException("Localization file not found", LocalizationManager.class.getSimpleName(), currentLanguage.getLocale().getLanguage());
            }

            if (engBundle == null && currentLanguage.equals(Language.ENGLISH)) {
                engBundle = new LocalizationPropertyResourceBundle(new InputStreamReader(inputStream, "UTF-8"));
            } else {
                bundle = new LocalizationPropertyResourceBundle(new InputStreamReader(inputStream, "UTF-8"));
            }
        } catch (Throwable var2) {
            LOGGER.warn("Unable to load resource bundle for [" + currentLanguage.getLocale() + "] due : " + var2.getMessage());
        }

    }

    public static Language getCurrentLanguage() {
        return currentLanguage;
    }

    public static Language getLanguage() {
        return currentLanguage;
    }

    private static synchronized void setLanguage(Language language) {
        currentLanguage = language;
        Locale.setDefault(language.getLocale());
        JOptionPane.setDefaultLocale(language.getLocale());
    }

    public static void changeLanguage(final Language language) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (LocalizationManager.currentLanguage == null || !LocalizationManager.currentLanguage.equals(language)) {
                    LocalizationManager.setLanguage(language);
                    LocalizationManager.loadBundle();
                    LocalizationManager.fireLanguageChanged();
                }
            }
        });
    }

    public static void addLocalizable(Localizable localizable) {
        List var1 = CACHE;
        synchronized(CACHE) {
            CACHE.add(new WeakReference(localizable));
        }

        localizable.localize();
    }

    public static String getText(String key) {
        return getTextForLang(currentLanguage, key);
    }

    public static String getTextWithArguments(String key, Object... arguments) {
        return getTextForLangWithArgs(currentLanguage, key, arguments);
    }

    public static String getTextWithArgumentKeys(String key, Object... argumentKeys) {
        return getTextForLangWithArgKeys(currentLanguage, key, argumentKeys);
    }

    public static String getTextForLangWithArgs(Language language, String key, Object... arguments) {
        if (key == null) {
            return "";
        } else {
            try {
                String pattern = getTranslation(language, key);
                return MessageFormat.format(pattern, arguments);
            } catch (MissingResourceException var4) {
                LOGGER.warn(var4.getMessage());
                return Language.ENGLISH.equals(language) ? key : getTextForLangWithArgs(Language.ENGLISH, key, arguments);
            }
        }
    }

    public static String getTextForLangWithArgKeys(Language language, String key, Object... argumentKeys) {
        if (key == null) {
            return "";
        } else {
            try {
                String pattern = getTranslation(language, key);
                Object[] paramsArray = new Object[argumentKeys.length];

                for(int i = 0; i < argumentKeys.length; ++i) {
                    try {
                        paramsArray[i] = getTranslation(language, argumentKeys[i].toString());
                    } catch (MissingResourceException var7) {
                        paramsArray[i] = argumentKeys[i];
                    }
                }

                return MessageFormat.format(pattern, paramsArray);
            } catch (MissingResourceException var8) {
                LOGGER.warn(var8.getMessage());
                return Language.ENGLISH.equals(language) ? key : getTextForLangWithArgKeys(Language.ENGLISH, key, argumentKeys);
            }
        }
    }

    public static String getTextForLang(Language language, String key) {
        if (key != null && !key.isEmpty()) {
            try {
                return getTranslation(language, key);
            } catch (MissingResourceException var3) {
                LOGGER.warn(var3.getMessage());
                return Language.ENGLISH.equals(language) ? key : getTextForLang(Language.ENGLISH, key);
            }
        } else {
            return "";
        }
    }

    private static String getTranslation(Language language, String key) {
        String translation = "";
        ResourceBundle bundle = getBundle(language);
        if (bundle != null) {
            translation = bundle.getString(key);
        }

        if (translation == null || translation.trim().isEmpty()) {
            bundle = getBundle(Language.ENGLISH);
            if (bundle != null) {
                translation = bundle.getString(key);
            }
        }

        return translation;
    }

    private static synchronized ResourceBundle getBundle(Language lang) {
        if (Language.ENGLISH.equals(lang)) {
            return engBundle;
        } else {
            LocalizationPropertyResourceBundle newBundle = null;

            try {
                Locale newLocale = lang.getLocale();
                if (bundle != null) {
                    Locale currentLocale = bundle.getLocale();
                    if (newLocale.equals(currentLocale)) {
                        return bundle;
                    }
                }

                Language[] var24 = Language.values();
                int var4 = var24.length;

                for(int var5 = 0; var5 < var4; ++var5) {
                    Language language = var24[var5];
                    if (language.getLocale().equals(newLocale)) {
                        InputStream inputStream = LocalizationManager.class.getClassLoader().getResourceAsStream("rc/languages/" + newLocale.getLanguage() + ".properties");
                        if (inputStream == null) {
                            throw new MissingResourceException("Localization file not found", LocalizationManager.class.getSimpleName(), newLocale.getLanguage());
                        }

                        try {
                            InputStreamReader reader = new InputStreamReader(inputStream, "UTF-8");
                            Throwable var9 = null;

                            try {
                                newBundle = new LocalizationPropertyResourceBundle(reader);
                                break;
                            } catch (Throwable var20) {
                                var9 = var20;
                                throw var20;
                            } finally {
                                if (reader != null) {
                                    if (var9 != null) {
                                        try {
                                            reader.close();
                                        } catch (Throwable var19) {
                                            var9.addSuppressed(var19);
                                        }
                                    } else {
                                        reader.close();
                                    }
                                }

                            }
                        } catch (Exception var22) {
                            LOGGER.error("Unable to load resource bundle for [" + newLocale + "] due : " + var22.getMessage());
                            break;
                        }
                    }
                }
            } catch (Exception var23) {
                LOGGER.error(var23.getMessage(), var23);
            }

            if (newBundle == null) {
                return bundle != null ? bundle : engBundle;
            } else {
                return newBundle;
            }
        }
    }

    private static void fireLanguageChanged() {
        List var0 = CACHE;
        synchronized(CACHE) {
            Iterator it = CACHE.iterator();

            while(it.hasNext()) {
                Localizable localizable = (Localizable)((WeakReference)it.next()).get();
                if (localizable != null) {
                    if (localizable instanceof Component) {
                        ((Component)localizable).setLocale(currentLanguage.getLocale());
                    }

                    localizable.localize();
                } else {
                    it.remove();
                }
            }
        }

        IAppearanceThemeManager appearanceThemeManager = UICoreServiceProvider.getAppearanceThemeManager();
        if (appearanceThemeManager != null) {
            appearanceThemeManager.updateFont();
        }

        Window[] windows = Window.getWindows();
        Window[] var9 = windows;
        int var3 = windows.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Window window = var9[var4];
            window.applyComponentOrientation(ComponentOrientation.getOrientation(currentLanguage.getLocale()));
        }

    }

    public static Font getDefaultFont(int fontSize) {
        Font f;

        if (Language.JAPANESE.equals(currentLanguage)) {
            f = new Font(UIManager.getString("FontName.Japanese"), 0, fontSize);
            f = f.deriveFont((new BigDecimal(fontSize)).floatValue());
            return f;
        } else if (!Language.GEORGIAN.equals(currentLanguage) && !Language.CHINESE.equals(currentLanguage)) {
            if (Language.KOREAN.equals(currentLanguage)) {
                String fontName = UIManager.getString("FontName.Korean");
                f = new Font(fontName, 0, fontSize);
                Font derivedFont = f.deriveFont((new BigDecimal(fontSize)).floatValue());
                return derivedFont;
            } else {
                return ((Font)UIManager.getDefaults().get("Label.font")).deriveFont((new BigDecimal(fontSize)).floatValue());
            }
        } else {
            f = new Font(UIManager.getString("FontName.CJKV"), 0, fontSize);
            f = f.deriveFont((new BigDecimal(fontSize)).floatValue());
            return f;
        }
    }

    static {
        currentLanguage = Language.ENGLISH;
        bundle = null;
        engBundle = null;
        loadBundle();
    }
}
