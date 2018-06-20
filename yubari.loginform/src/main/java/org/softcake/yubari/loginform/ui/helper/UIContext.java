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

package org.softcake.yubari.loginform.ui.helper;

import java.awt.Toolkit;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

public class UIContext {
    public UIContext() {
    }

    public static UIContext.OsType getOperatingSystemType() {
        UIContext.OsType result = null;
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().startsWith("Windows 2000")) {
            result = UIContext.OsType.WIN2000;
        } else if (osName.toLowerCase().startsWith("windows")) {
            result = UIContext.OsType.WINDOWS;
        } else if (osName.toLowerCase().startsWith("linux")) {
            result = UIContext.OsType.LINUX;
        } else if (osName.toLowerCase().startsWith("mac os x")) {
            result = UIContext.OsType.MACOSX;
        } else {
            result = UIContext.OsType.OTHER;
        }

        return result;
    }

    public static UIContext.LookAndFeelType getLookAndFeelType() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        String osName = System.getProperty("os.name");
        boolean isVista = osName.trim().endsWith("Vista");
        boolean isWin7 = osName.trim().endsWith("Windows 7");
        boolean isXP = osName.trim().endsWith("XP");
        boolean xpThemeActive = Boolean.TRUE.equals(toolkit.getDesktopProperty("win.xpstyle.themeActive"));
        boolean noxp = System.getProperty("swing.noxp") != null;
        boolean isClassic = false;
        boolean isVistaStyle = false;
        boolean isMetal = false;
        boolean isXPStyle = false;
        isClassic = laf.getClass().getName().endsWith("WindowsClassicLookAndFeel") || laf.getClass().getName().endsWith("WindowsLookAndFeel") && (!xpThemeActive || noxp);
        if (isXP) {
            isXPStyle = laf.getClass().getName().endsWith("WindowsLookAndFeel") && !isClassic && xpThemeActive;
        } else if (isVista || isWin7) {
            boolean var10000;
            if (laf.getClass().getName().endsWith("WindowsLookAndFeel") && !isClassic) {
                var10000 = true;
            } else {
                var10000 = false;
            }
        }

        isMetal = laf.getClass().getName().endsWith("metal.MetalLookAndFeel");
        if (isXPStyle) {
            return UIContext.LookAndFeelType.WINDOWS_XP;
        } else if (isMetal) {
            return UIContext.LookAndFeelType.METAL;
        } else if (isClassic) {
            return UIContext.LookAndFeelType.CLASSIC;
        } else if (isVista) {
            return UIContext.LookAndFeelType.VISTA;
        } else if (isWin7) {
            return UIContext.LookAndFeelType.WIN7;
        } else {
            return osName.toLowerCase().startsWith("mac os x") ? UIContext.LookAndFeelType.MACOS : UIContext.LookAndFeelType.OTHER;
        }
    }

    public static enum LookAndFeelType {
        WINDOWS_XP,
        CLASSIC,
        VISTA,
        WIN7,
        MACOS,
        METAL,
        OTHER;

        private LookAndFeelType() {
        }
    }

    public static enum OsType {
        WIN2000,
        WINDOWS,
        MACOSX,
        LINUX,
        OTHER;

        private OsType() {
        }
    }
}
