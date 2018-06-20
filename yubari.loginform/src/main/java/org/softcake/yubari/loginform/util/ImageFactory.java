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

package org.softcake.yubari.loginform.util;

import org.softcake.yubari.loginform.service.IAppearanceThemeManager;
import org.softcake.yubari.loginform.service.UICoreServiceProvider;
import org.softcake.yubari.loginform.ui.laf.DynamicBufferedImage;

import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.net.URL;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImageFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageFactory.class);
    public static final float DEFAULT_IMAGE_RATIO;
    public static final float PREV_DEFAULT_IMAGE_RATIO;
    public static final float OLD_INIT_IMAGE_RATIO;
    private static final String IMAGES_PATH = "loginform/res/icons/";
    private static final Map<IAppearanceThemeManager.ZoomMode, Map<ImageFactory.ImageType, Map<String, Image>>> cachedImagesMap;
    private static final MediaTracker mTracker;

    public ImageFactory() {
    }

    public static Image getImage(String imageName) {
        return getImage(imageName, ImageFactory.ImageType.STATIC, (Object)null);
    }

    public static Image getImage(String imageName, ImageFactory.ImageType type, Object arg) {
        if (imageName == null) {
            throw new IllegalArgumentException("Image name cannot be null");
        } else {
            URL url = getClassLoader().getResource("loginform/res/icons/" + imageName);
            if (type == ImageFactory.ImageType.STATIC) {
                return Toolkit.getDefaultToolkit().getImage(url);
            } else {
                Image result = getCachedImage(IAppearanceThemeManager.ZoomMode.STANDARD, imageName, type, arg);
                if (result == null) {
                    try {
                        BufferedImage sourceImage = ImageIO.read(url);
                        result = new DynamicBufferedImage(sourceImage, type, arg);
                        cacheImage(IAppearanceThemeManager.ZoomMode.STANDARD, imageName, type, arg, (Image)result);
                    } catch (IOException var6) {
                        LOGGER.error("Cannot load image", var6);
                    }
                }

                return (Image)result;
            }
        }
    }

    public static Image getResizedImage(String imageName, ImageFactory.ImageType type, Object arg) {
        return getResizedImage(imageName, type, arg, DEFAULT_IMAGE_RATIO);
    }

    public static Image getResizedImage(Image image, ImageFactory.ImageType type, Object arg, float imageRatio) {
        if (image == null) {
            throw new IllegalArgumentException("Image cannot be null");
        } else {
            try {
                float ratio = UICoreServiceProvider.getAppearanceThemeManager().getCurrentZoomMode().getRatio();
                if (ratio != imageRatio) {
                    int width = (int)((float)((Image)image).getWidth((ImageObserver)null) * ratio / imageRatio);
                    int height = (int)((float)((Image)image).getHeight((ImageObserver)null) * ratio / imageRatio);
                    image = ((Image)image).getScaledInstance(width, height, 4);
                }

                if (type != ImageFactory.ImageType.STATIC) {
                    image = new DynamicBufferedImage(toBufferedImage((Image)image), type, arg);
                }
            } catch (Throwable var7) {
                LOGGER.error("Cannot resize image", var7);
            }

            return (Image)image;
        }
    }

    public static Image getResizedImage(String imageName, ImageFactory.ImageType type, Object arg, float imageRatio) {
        if (imageName == null) {
            throw new IllegalArgumentException("Image name cannot be null");
        } else {
            IAppearanceThemeManager.ZoomMode zoomMode = UICoreServiceProvider.getAppearanceThemeManager().getCurrentZoomMode();
            Image image = getCachedImage(zoomMode, imageName, type, arg);
            if (image == null) {
                URL url = getClassLoader().getResource("loginform/res/icons/" + imageName);
                if (url == null) {
                    return null;
                }

                try {
                    image = ImageIO.read(url);
                    float ratio = zoomMode.getRatio();
                    if (ratio != imageRatio) {
                        int width = (int)((float)((Image)image).getWidth((ImageObserver)null) * ratio / imageRatio);
                        int height = (int)((float)((Image)image).getHeight((ImageObserver)null) * ratio / imageRatio);
                        image = ((Image)image).getScaledInstance(width, height, 4);
                    }

                    if (type != ImageFactory.ImageType.STATIC) {
                        image = new DynamicBufferedImage(toBufferedImage((Image)image), type, arg);
                    }

                    cacheImage(zoomMode, imageName, type, arg, (Image)image);
                } catch (IOException var10) {
                    LOGGER.error("Cannot load image", var10);
                }
            }

            return (Image)image;
        }
    }

    public static Icon getIcon(String iconName) {
        URL url = getClassLoader().getResource("loginform/res/icons/" + iconName);
        return url != null ? new ImageIcon(url) : null;
    }

    public static Icon getIcon(String iconName, ImageFactory.ImageType type, Object arg) {
        if (type == ImageFactory.ImageType.STATIC) {
            return getIcon(iconName);
        } else {
            Image image = getImage(iconName, type, arg);
            if (image != null) {
                return new ImageIcon(image);
            } else {
                LOGGER.warn("Unable to load image icon : " + iconName);
                return null;
            }
        }
    }

    public static Icon getResizedIcon(String iconName) {
        return getResizedIcon(iconName, ImageFactory.ImageType.PLATFORM_THEME_APPLICABLE, (Object)null);
    }

    public static Icon getResizedIcon(String iconName, ImageFactory.ImageType type, Object arg) {
        return getResizedIcon(iconName, type, arg, DEFAULT_IMAGE_RATIO);
    }

    public static Icon getResizedIcon(String iconName, ImageFactory.ImageType type, Object arg, float imageRatio) {
        if (IsRetina.isRetina) {
            imageRatio /= 2.0F;
        }

        Image image = getResizedImage(iconName, type, arg, imageRatio);
        if (image == null) {
            return null;
        } else {
            return (Icon)(IsRetina.isRetina ? new RetinaIcon(image) : new ImageIcon(image));
        }
    }

    private static void cacheImage(IAppearanceThemeManager.ZoomMode fontMode, String imageName, ImageFactory.ImageType type, Object arg, Image image) {
        if (!cachedImagesMap.containsKey(fontMode)) {
            cachedImagesMap.put(fontMode, new EnumMap(ImageFactory.ImageType.class));
        }

        Map<ImageFactory.ImageType, Map<String, Image>> innerMap = (Map)cachedImagesMap.get(fontMode);
        if (!innerMap.containsKey(type)) {
            innerMap.put(type, new HashMap());
        }

        String key = arg == null ? imageName : imageName + arg.toString();
        ((Map)((Map)cachedImagesMap.get(fontMode)).get(type)).put(key, image);
    }

    private static Image getCachedImage(IAppearanceThemeManager.ZoomMode fontMode, String imageName, ImageFactory.ImageType type, Object arg) {
        if (cachedImagesMap.containsKey(fontMode)) {
            Map<ImageFactory.ImageType, Map<String, Image>> innerMap = (Map)cachedImagesMap.get(fontMode);
            if (innerMap.containsKey(type)) {
                String key = arg == null ? imageName : imageName + arg.toString();
                return (Image)((Map)innerMap.get(type)).get(key);
            }
        }

        return null;
    }

    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage)img;
        } else {
            if (img.getWidth((ImageObserver)null) < 0) {
                mTracker.addImage(img, 0);

                try {
                    mTracker.waitForID(0);
                } catch (InterruptedException var3) {
                    ;
                }
            }

            BufferedImage bimage = new BufferedImage(img.getWidth((ImageObserver)null), img.getHeight((ImageObserver)null), 2);
            Graphics2D bGr = bimage.createGraphics();
            bGr.drawImage(img, 0, 0, (ImageObserver)null);
            bGr.dispose();
            return bimage;
        }
    }

    private static ClassLoader getClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }

        return classLoader;
    }

    static {
        DEFAULT_IMAGE_RATIO = IAppearanceThemeManager.ZoomMode.LARGE_150.getRatio() * 2.0F;
        PREV_DEFAULT_IMAGE_RATIO = IAppearanceThemeManager.ZoomMode.LARGE_150.getRatio();
        OLD_INIT_IMAGE_RATIO = IAppearanceThemeManager.ZoomMode.STANDARD.getRatio();
        cachedImagesMap = new EnumMap(IAppearanceThemeManager.ZoomMode.class);
        mTracker = new MediaTracker(new Container());
    }

    public static enum ImageType {
        STATIC,
        PLATFORM_THEME_APPLICABLE,
        DISABLED,
        MANUAL_GREYSCALE,
        HOVER,
        REPLACE_ALL_COLORS;

        private ImageType() {
        }
    }
}
