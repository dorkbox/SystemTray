/*
 * Copyright 2014 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dorkbox.systemTray.swing;

import dorkbox.systemTray.ImageUtil;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.FileUtil;
import dorkbox.util.SwingUtil;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class SwingMenuEntry implements MenuEntry {
    private static final String tempDirPath = ImageUtil.TEMP_DIR.getAbsolutePath();

    private final SwingSystemTrayMenuPopup parent;
    private final SystemTray systemTray;
    private final JMenuItem menuItem;
    private final ActionListener swingCallback;

    private volatile String text;
    private volatile SystemTrayMenuAction callback;

    private int iconHeight = -1;




    SwingMenuEntry(final SwingSystemTrayMenuPopup parentMenu, final String label, final String imagePath, final SystemTrayMenuAction callback,
                   final SystemTray systemTray) {
        this.parent = parentMenu;
        this.text = label;
        this.callback = callback;
        this.systemTray = systemTray;

        swingCallback = new ActionListener() {
            @Override
            public
            void actionPerformed(ActionEvent e) {
                // we want it to run on the EDT
                handle();
            }
        };

        menuItem = new JMenuItem(label);
        menuItem.addActionListener(swingCallback);

        if (imagePath != null && !imagePath.isEmpty()) {
            setImageIcon(imagePath);
        }

        parentMenu.add(menuItem);
    }

    private
    void handle() {
        SystemTrayMenuAction cb = this.callback;
        if (cb != null) {
            cb.onClick(systemTray, this);
        }
    }

    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        this.text = newText;

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                menuItem.setText(newText);
            }
        });
    }

    private
    void setImage_(final String imagePath) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                setImageIcon(imagePath);
            }
        });
    }

    private
    void setImageIcon(final String imagePath) {
        if (imagePath != null && !imagePath.isEmpty()) {

            if (iconHeight != 0) {
                // this will (and should) be the correct size for the system. On the systems tested, it was 16
                // see: http://en-human-begin.blogspot.de/2007/11/javas-icons-by-default.html
                Icon icon = UIManager.getIcon("FileView.fileIcon");
                iconHeight = icon.getIconHeight();
            }

            ImageIcon origIcon = new ImageIcon(imagePath);
            int origIconHeight = origIcon.getIconHeight();
            int origIconWidth = origIcon.getIconWidth();

            int savedIconHeight = this.iconHeight;

            // it is necessary to resize this icon, so that it matches what our preferred size is for icons
            if (origIconHeight != savedIconHeight && savedIconHeight != 0) {
                //noinspection SuspiciousNameCombination
                Dimension scaledDimension = getScaledDimension(origIconWidth, origIconHeight, savedIconHeight, savedIconHeight);

                Image image = origIcon.getImage();

                // scale it the smoothly
                Image newImage = image.getScaledInstance(scaledDimension.width, scaledDimension.height, java.awt.Image.SCALE_SMOOTH);
                origIcon = new ImageIcon(newImage);

                // save it to temp spot on disk (so we don't have to KEEP on doing this). (but it MUST be the temp location, otherwise
                // it's always 'on the fly')
                if (imagePath.startsWith(tempDirPath)) {
                    // have to delete the old one
                    File file = new File(imagePath);
                    boolean delete = file.delete();

                    if (delete) {
                        // now write out the new one
                        String extension = FileUtil.getExtension(imagePath);
                        if (extension == null) {
                            extension = ".png"; // this is just made up
                        }
                        BufferedImage bufferedImage = getBufferedImage(image);
                        try {
                            ImageIO.write(bufferedImage, extension, file);
                        } catch (IOException e) {
                            // this shouldn't happen, but you never know...
                            e.printStackTrace();
                        }
                    }
                }
            }

            menuItem.setIcon(origIcon);
        }
        else {
            menuItem.setIcon(null);
        }
    }

    @Override
    public
    void setImage(final String imagePath) {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imagePath));
        }
    }

    @Override
    public
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imageUrl));
        }
    }

    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(cacheName, imageStream));
        }
    }

    @Override
    @Deprecated
    public
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPathNoCache(imageStream));
        }
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        this.callback = callback;
    }

    @Override
    public
    void remove() {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                menuItem.removeActionListener(swingCallback);
                parent.remove(menuItem);
            }
        });
    }

    private static
    Dimension getScaledDimension(int originalWidth, int originalHeight, int boundWidth, int boundHeight) {
        //this function comes from http://stackoverflow.com/questions/10245220/java-image-resize-maintain-aspect-ratio

        int newWidth = originalWidth;
        int newHeight = originalHeight;

        // first check if we need to scale width
        if (originalWidth > boundWidth) {
            //scale width to fit
            newWidth = boundWidth;

            //scale height to maintain aspect ratio
            newHeight = (newWidth * originalHeight) / originalWidth;
        }

        // then check if we need to scale even with the new height
        if (newHeight > boundHeight) {
            //scale height to fit instead
            newHeight = boundHeight;

            //scale width to maintain aspect ratio
            newWidth = (newHeight * originalWidth) / originalHeight;
        }

        return new Dimension(newWidth, newHeight);
    }

    private static
    BufferedImage getBufferedImage(Image image) {
        if (image instanceof BufferedImage) {
            return (BufferedImage) image;
        }

        BufferedImage bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(image, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }
}
