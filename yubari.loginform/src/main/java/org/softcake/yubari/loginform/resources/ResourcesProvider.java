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

package org.softcake.yubari.loginform.resources;

import org.softcake.yubari.loginform.utils.ImageUtils;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourcesProvider implements IResourcesProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcesProvider.class);
    public static final String JNLP_COMPANY_LOGO_URL = "jnlp.company.logo.url";
    public static final String JNLP_PLATFORM_LOGO_URL = "jnlp.platform.logo.url";
    public static final String DEFAULT_COMPANY_LOGO_LOCAL_PATH = "rc/images/DC_logo_150_no_offset.png";
    public static final String DEFAULT_PLATFORM_LOGO_LOCAL_PATH = "rc/images/logo_empty_titlebar.png";
    public static final String EMPTY_COMPANY_LOGO_LOCAL_PATH = "rc/images/no_logo_150_no_offset.png";
    public static final String JNLP_SRP6_LOGIN_URL = "jnlp.srp6.login.url";
    public static final String JNLP_LOGIN_URL = "jnlp.login.url";
    public static final String LAUNCHER_UTILS_CLASS_NAME = "com.dukascopy.launcher.utils.LauncherUtils";
    private static final String COMPANY_LOGO_FILE_NAME = "companyLogo.png";
    private static final String PLATFORM_LOGO_FILE_NAME = "platformLogo.png";
    private String defaultCompanyLogoPath = "rc/images/no_logo_150_no_offset.png";
    private String defaultPlatformLogoPath = "rc/images/logo_empty_titlebar.png";
    private IResourcesUrlProvider resourcesUrlProvider;
    private Image platformLogo = null;
    private Image companyLogo = null;

    public ResourcesProvider(IResourcesUrlProvider resourcesUrlProvider) {
        this.resourcesUrlProvider = resourcesUrlProvider;
    }

    public Image getPlatformLogo() {
        try {
            if (this.platformLogo == null) {
                this.platformLogo = this.getPlatformLogoImpl();
                if (ResourcesUrlProviderType.STANDALONE == this.resourcesUrlProvider.getResourcesUrlProviderType()) {
                    this.saveImageToCache(this.platformLogo, "platformLogo.png");
                }
            }

            return this.platformLogo;
        } catch (Throwable var4) {
            if (var4 instanceof UnavailableWSServiceException) {
                if (ResourcesUrlProviderType.WEBSTART == this.resourcesUrlProvider.getResourcesUrlProviderType()) {
                    this.defaultPlatformLogoPath = "rc/images/logo_empty_titlebar.png";
                }
            } else if (var4 instanceof JNLPObjectAbsentException) {
                LOGGER.error("Cannot get the platform logo, the jnlp object is absent.");
            } else {
                LOGGER.error("Cannot get the platform logo.");
                LOGGER.error(var4.getMessage(), var4);
            }

            if (ResourcesUrlProviderType.STANDALONE == this.resourcesUrlProvider.getResourcesUrlProviderType()) {
                Image platformLogo = this.loadImagefromCache("platformLogo.png");
                if (platformLogo != null) {
                    return platformLogo;
                }
            }

            try {
                return this.getDefaultPlatformLogo();
            } catch (Throwable var3) {
                LOGGER.error(var4.getMessage(), var3);
                return null;
            }
        }
    }

    public Image getCompanyLogo() {
        try {
            if (this.companyLogo == null) {
                this.companyLogo = this.getCompanyLogoImpl();
                if (ResourcesUrlProviderType.STANDALONE == this.resourcesUrlProvider.getResourcesUrlProviderType()) {
                    this.saveImageToCache(this.companyLogo, "companyLogo.png");
                }
            }

            return this.companyLogo;
        } catch (Throwable var4) {
            if (var4 instanceof UnavailableWSServiceException) {
                if (ResourcesUrlProviderType.WEBSTART == this.resourcesUrlProvider.getResourcesUrlProviderType()) {
                    this.defaultCompanyLogoPath = "rc/images/DC_logo_150_no_offset.png";
                }
            } else if (var4 instanceof JNLPObjectAbsentException) {
                LOGGER.error("Cannot get the company logo, the jnlp object is absent.");
            } else {
                LOGGER.error("Cannot get the company logo.");
                LOGGER.error(var4.getMessage(), var4);
            }

            if (ResourcesUrlProviderType.STANDALONE == this.resourcesUrlProvider.getResourcesUrlProviderType()) {
                Image companyLogo = this.loadImagefromCache("companyLogo.png");
                if (companyLogo != null) {
                    return companyLogo;
                }
            }

            try {
                return this.getDefaultCompanyLogo();
            } catch (Throwable var3) {
                LOGGER.error(var4.getMessage(), var3);
                return null;
            }
        }
    }

    private Image getDefaultCompanyLogo() {
        Image image = null;

        try {
            image = ImageUtils.createImage(this.defaultCompanyLogoPath);
            LOGGER.error("The default company logo is used.");
        } catch (Throwable var3) {
            LOGGER.error(var3.getMessage(), var3);
        }

        return image;
    }

    private Image getDefaultPlatformLogo() {
        Image image = null;

        try {
            image = ImageUtils.createImage(this.defaultPlatformLogoPath);
            LOGGER.error("The default platform logo is used.");
        } catch (Throwable var3) {
            LOGGER.error(var3.getMessage(), var3);
        }

        return image;
    }

    private Image getCompanyLogoImpl() throws Exception {
        String companyLogoUrl = this.resourcesUrlProvider.getCompanyLogoUrl();
        Image image = this.createAndGetImage(companyLogoUrl);
        return image;
    }

    private Image getPlatformLogoImpl() throws Exception {
        String platformLogoUrl = this.resourcesUrlProvider.getPlatformLogoUrl();
        Image image = this.createAndGetImage(platformLogoUrl);
        return image;
    }

    private Image createAndGetImage(String logoUrl) throws Exception {
        byte[] companyLogoAsByteArray = this.downloadAndGetResourceAsByteArray(logoUrl);
        ByteArrayInputStream bais = new ByteArrayInputStream(companyLogoAsByteArray);
        BufferedImage bufferedImage = ImageIO.read(bais);
        return bufferedImage;
    }

    private byte[] downloadAndGetResourceAsByteArray(String urlAsString) throws Exception {
        InputStream is = null;

        try {
            URL url = new URL(urlAsString);
            URLConnection connection = url.openConnection();
            is = connection.getInputStream();
            byte[] byteChunk = new byte[4096];
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);

            int n;
            while((n = is.read(byteChunk)) > 0) {
                baos.write(byteChunk, 0, n);
            }

            byte[] byteArray = baos.toByteArray();
            byte[] var9 = byteArray;
            return var9;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable var16) {
                    LOGGER.error(var16.getMessage(), var16);
                }
            }

        }
    }

    private void saveImageToCache(Image image, String imageFileName) {
        String var3 = "png";

        try {
            if (image != null) {
                Path imagesCachePath = getCachePath();
                Path imageFilePath = imagesCachePath.resolve(imageFileName);
                BufferedImage bufferedImage = null;
                if (image instanceof BufferedImage) {
                    bufferedImage = (BufferedImage)image;
                } else {
                    bufferedImage = new BufferedImage(image.getWidth((ImageObserver)null), image.getHeight((ImageObserver)null), 2);
                    Graphics g = bufferedImage.createGraphics();
                    g.drawImage(image, 0, 0, (ImageObserver)null);
                    g.dispose();
                }

                String imageFilePathAsAtring = imageFilePath.toString();

                try {
                    ImageIO.write(bufferedImage, "png", new File(imageFilePathAsAtring));
                } catch (Exception var10) {
                    StringBuilder sb = new StringBuilder(128);
                    sb.append("Cannot save the image, ").append("imageFilePath=").append(imageFilePathAsAtring).append(", formatName=").append("png").append(", exceptionMessage=").append(var10.getMessage());
                    LOGGER.error(sb.toString(), var10);
                }
            }
        } catch (Throwable var11) {
            LOGGER.error("Cannot save the company/platform logo");
            LOGGER.error(var11.getMessage(), var11);
        }

    }

    private Image loadImagefromCache(String imageFileName) {
        BufferedImage image = null;

        try {
            Path imagesCachePath = getCachePath();
            Path imageFilePath = imagesCachePath.resolve(imageFileName);
            File imageFile = imageFilePath.toFile();
            if (imageFile.exists()) {
                BufferedImage bufferedImage = ImageIO.read(imageFile);
                image = bufferedImage;
            }
        } catch (Throwable var7) {
            LOGGER.error("Cannot load the company/platform logo from cache");
            LOGGER.error(var7.getMessage(), var7);
        }

        return image;
    }

    public static Path getCachePath() throws Exception {
        Path path = null;
        Class<?> clazz = Class.forName("com.dukascopy.launcher.utils.LauncherUtils");
        URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
        path = Paths.get(url.toURI());
        path = path.getParent();
        path = path.getParent();
        int nameCount = path.getNameCount();
        if (nameCount > 0) {
            Path name = path.getName(nameCount - 1);
            if ("libs".equalsIgnoreCase(name.toString())) {
                path = path.getParent();
            }
        }

        return path;
    }
}
