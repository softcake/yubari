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

import java.awt.Image;
import java.awt.Toolkit;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageUtils.class);

    public ImageUtils() {
    }

    public static ImageIcon createImageIcon(String path) {
        ImageIcon imageIcon = null;
        int MAX_IMAGE_SIZE = 90000;
       // int count = false;
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (in == null) {
            LOGGER.error("Couldn't get the stream from file: " + path);
            return null;
        } else {
            BufferedInputStream bis = new BufferedInputStream(in);
            byte[] buf = new byte[MAX_IMAGE_SIZE];

            int count;
            try {
                count = bis.read(buf);
            } catch (Throwable var10) {
                LOGGER.error("Couldn't read stream from file: " + path);
                return null;
            }

            try {
                bis.close();
            } catch (Throwable var9) {
                LOGGER.error(var9.getMessage(), var9);
            }

            if (count <= 0) {
                LOGGER.error("Empty file: " + path);
                return null;
            } else {
                try {
                    Image image = Toolkit.getDefaultToolkit().createImage(buf);
                    imageIcon = new ImageIcon(image);
                } catch (Throwable var8) {
                    LOGGER.error("Cannot create the ImageIcon.");
                    LOGGER.error(var8.getMessage(), var8);
                }

                return imageIcon;
            }
        }
    }

    public static Image createImage(String path) {
        Image image = null;
        int MAX_IMAGE_SIZE = 90000;
       // int count = false;
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        if (in == null) {
            LOGGER.error("Couldn't get the stream from file: " + path);
            return null;
        } else {
            BufferedInputStream bis = new BufferedInputStream(in);
            byte[] buf = new byte[MAX_IMAGE_SIZE];

            int count;
            try {
                count = bis.read(buf);
            } catch (Throwable var10) {
                LOGGER.error("Couldn't read stream from file: " + path);
                return null;
            }

            try {
                bis.close();
            } catch (Throwable var9) {
                LOGGER.error(var9.getMessage(), var9);
            }

            if (count <= 0) {
                LOGGER.error("Empty file: " + path);
                return null;
            } else {
                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(buf);
                    image = ImageIO.read(bais);
                } catch (Throwable var8) {
                    LOGGER.error("Cannot create the Image.");
                    LOGGER.error(var8.getMessage(), var8);
                }

                return image;
            }
        }
    }
}
