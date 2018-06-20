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

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.ImageObserver;
import javax.swing.ImageIcon;

public class RetinaIcon extends ImageIcon {
    private static final long serialVersionUID = 1L;

    public RetinaIcon(Image image) {
        super(image);
    }

    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        ImageObserver observer = this.getImageObserver();
        if (observer == null) {
            observer = c;
        }

        Image image = this.getImage();
        int width = image.getWidth((ImageObserver)observer);
        int height = image.getHeight((ImageObserver)observer);
        Graphics2D g2d = (Graphics2D)g.create(x, y, width, height);
        g2d.scale(0.5D, 0.5D);
        g2d.drawImage(image, 0, 0, (ImageObserver)observer);
        g2d.scale(1.0D, 1.0D);
        g2d.dispose();
    }

    public int getIconWidth() {
        return (int)((double)super.getIconWidth() * 0.5D);
    }

    public int getIconHeight() {
        return (int)((double)super.getIconHeight() * 0.5D);
    }
}
