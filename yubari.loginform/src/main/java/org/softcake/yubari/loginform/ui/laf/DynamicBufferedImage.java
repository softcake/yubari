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

package org.softcake.yubari.loginform.ui.laf;

import org.softcake.yubari.loginform.service.UICoreServiceProvider;
import org.softcake.yubari.loginform.ui.components.IAppearanceThemeObservable;
import org.softcake.yubari.loginform.util.ImageFactory;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageProducer;
import javax.swing.GrayFilter;
import javax.swing.UIManager;

public class DynamicBufferedImage extends BufferedImage implements IAppearanceThemeObservable {
    private static final int ALLOWED_GREY_DISPERSION = 10;
    private final BufferedImage srcImg;
    private final ImageFactory.ImageType type;
    private Object arg;

    public DynamicBufferedImage(BufferedImage srcImg, ImageFactory.ImageType type, Object arg) {
        super(srcImg.getWidth(), srcImg.getHeight(), 6);
        this.srcImg = srcImg;
        this.type = type;
        this.arg = arg;
        switch(type) {
        case PLATFORM_THEME_APPLICABLE:
            UICoreServiceProvider.getAppearanceThemeManager().addAppearanceThemeChangeListener(this);
            if (arg == null) {
                this.arg = "CustomIcons.defaultGreyShiftPercent";
            }

            this.adjustImageColors();
            break;
        case MANUAL_GREYSCALE:
            if (arg != null) {
                this.adjustImageColors();
            }
            break;
        case HOVER:
            UICoreServiceProvider.getAppearanceThemeManager().addAppearanceThemeChangeListener(this);
            if (arg == null) {
                this.arg = "CustomIcons.hover";
            }

            this.adjustImageColors();
            break;
        case DISABLED:
        case REPLACE_ALL_COLORS:
            UICoreServiceProvider.getAppearanceThemeManager().addAppearanceThemeChangeListener(this);
            this.adjustImageColors();
            break;
        default:
            throw new IllegalStateException("Unsupported image type");
        }

    }

    public void applyNewTheme() {
        this.adjustImageColors();
    }

    public void applyZoomMode() {
    }

    public void changeArg(Object arg) {
        if (this.arg == null && arg != null || this.arg != null && !this.arg.equals(arg)) {
            this.arg = arg;
            this.adjustImageColors();
        }

    }

    private void adjustImageColors() {
        int adjustment;
        switch(this.type) {
        case PLATFORM_THEME_APPLICABLE:
            adjustment = UIManager.getInt(this.arg);
            int nonGreyAdjustment = this.arg == "CustomIcons.defaultGreyShiftPercent" ? UIManager.getInt("CustomIcons.defaultNonGreyShiftAbs") : 0;
            this.adjustColors(adjustment, nonGreyAdjustment);
            break;
        case MANUAL_GREYSCALE:
            adjustment = (Integer)this.arg;
            this.adjustColors(adjustment, 0);
            break;
        case HOVER:
            this.adjustHoverColor();
            break;
        case DISABLED:
            this.adjustDisabledColors();
            break;
        case REPLACE_ALL_COLORS:
            this.replaceAllColors();
            break;
        default:
            throw new IllegalStateException("Unsupported image type");
        }

    }

    private void replaceAllColors() {
        int newColorRGB = UIManager.getColor(this.arg).getRGB();

        for(int x = 0; x < this.getWidth(); ++x) {
            for(int y = 0; y < this.getHeight(); ++y) {
                int rgb = this.srcImg.getRGB(x, y);
                if ((rgb & -16777216) == 0) {
                    this.setRGB(x, y, rgb);
                } else {
                    this.setRGB(x, y, newColorRGB & (rgb | 16777215));
                }
            }
        }

    }

    private void adjustHoverColor() {
        int hoverColorRGB = UIManager.getColor(this.arg).getRGB();
        int otherColorAdjustment = UIManager.getInt("CustomIcons.defaultNonGreyShiftAbs");

        for(int x = 0; x < this.getWidth(); ++x) {
            for(int y = 0; y < this.getHeight(); ++y) {
                int rgb = this.srcImg.getRGB(x, y);
                if ((rgb & -16777216) == 0) {
                    this.setRGB(x, y, rgb);
                } else if ((rgb & -16777216) != 0 && this.isGrey(rgb)) {
                    this.setRGB(x, y, hoverColorRGB & (rgb | 16777215));
                } else {
                    this.setRGB(x, y, this.adjustOther(rgb, otherColorAdjustment));
                }
            }
        }

    }

    private void adjustDisabledColors() {
        GrayFilter filter = new GrayFilter(true, UIManager.getInt("CustomIcons.disabledGreyShiftPercent"));
        ImageProducer prod = new FilteredImageSource(this.srcImg.getSource(), filter);
        Image grayImage = Toolkit.getDefaultToolkit().createImage(prod);
        BufferedImage disabledImage = ImageFactory.toBufferedImage(grayImage);

        for(int x = 0; x < this.getWidth(); ++x) {
            for(int y = 0; y < this.getHeight(); ++y) {
                int rgb = disabledImage.getRGB(x, y);
                this.setRGB(x, y, rgb);
            }
        }

    }

    private void adjustColors(int greyAdjustment, int colorAdjustment) {
        for(int x = 0; x < this.getWidth(); ++x) {
            for(int y = 0; y < this.getHeight(); ++y) {
                int rgb = this.srcImg.getRGB(x, y);
                if ((rgb & -16777216) == 0) {
                    this.setRGB(x, y, rgb);
                } else if (this.isGrey(rgb)) {
                    this.setRGB(x, y, this.adjustGrey(rgb, greyAdjustment));
                } else {
                    this.setRGB(x, y, this.adjustOther(rgb, colorAdjustment));
                }
            }
        }

    }

    private boolean isGrey(int rgb) {
        int red = rgb & 255;
        int green = rgb >> 8 & 255;
        if (Math.abs(red - green) > 10) {
            return false;
        } else {
            int blue = rgb >> 16 & 255;
            return Math.abs(red - blue) <= 10;
        }
    }

    private int adjustGrey(int rgb, int adjustment) {
        int red = this.adjustGreyColor(rgb & 255, adjustment);
        int green = this.adjustGreyColor(rgb >> 8 & 255, adjustment);
        int blue = this.adjustGreyColor(rgb >> 16 & 255, adjustment);
        return (rgb & -16777216) + (blue << 16) + (green << 8) + red;
    }

    private int adjustGreyColor(int color, int adjustment) {
        int amountToAdjust = (128 - Math.abs(128 - color)) * adjustment / 100;
        return Math.max(0, Math.min(255, color + amountToAdjust));
    }

    private int adjustOther(int rgb, int adjustment) {
        if (adjustment == 0) {
            return rgb;
        } else {
            int red = this.adjustOtherColor(rgb & 255, adjustment);
            int green = this.adjustOtherColor(rgb >> 8 & 255, adjustment);
            int blue = this.adjustOtherColor(rgb >> 16 & 255, adjustment);
            return (rgb & -16777216) + (blue << 16) + (green << 8) + red;
        }
    }

    private int adjustOtherColor(int color, int adjustment) {
        return Math.max(0, Math.min(255, color + adjustment));
    }
}
