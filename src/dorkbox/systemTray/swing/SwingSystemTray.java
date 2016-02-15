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
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.ScreenUtil;
import dorkbox.util.SwingUtil;

import javax.swing.ImageIcon;
import javax.swing.JMenuItem;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.InputStream;
import java.net.URL;

/**
 * Class for handling all system tray interaction, via SWING
 */
public
class SwingSystemTray extends dorkbox.systemTray.SystemTray {
    volatile SwingSystemTrayMenuPopup menu;

    volatile JMenuItem connectionStatusItem;
    private volatile String statusText = null;

    volatile SystemTray tray;
    volatile TrayIcon trayIcon;

    volatile boolean isActive = false;

    /**
     * Creates a new system tray handler class.
     */
    public
    SwingSystemTray() {
        super();
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray.this.tray = SystemTray.getSystemTray();
                if (SwingSystemTray.this.tray == null) {
                    logger.error("The system tray is not available");
                }
            }
        });
    }


    @Override
    public
    void shutdown() {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray tray = SwingSystemTray.this;
                synchronized (tray) {
                    tray.tray.remove(tray.trayIcon);

                    for (MenuEntry menuEntry : tray.menuEntries) {
                        menuEntry.remove();
                    }
                    tray.menuEntries.clear();

                    tray.connectionStatusItem = null;
                }
            }
        });
    }

    @Override
    public
    String getStatus() {
        return this.statusText;
    }

    @Override
    public
    void setStatus(final String statusText) {
        this.statusText = statusText;

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray tray = SwingSystemTray.this;
                synchronized (tray) {
                    if (tray.connectionStatusItem == null) {
                        final JMenuItem connectionStatusItem = new JMenuItem(statusText);
                        Font font = connectionStatusItem.getFont();
                        Font font1 = font.deriveFont(Font.BOLD);
                        connectionStatusItem.setFont(font1);

                        connectionStatusItem.setEnabled(false);
                        tray.menu.add(connectionStatusItem);

                        tray.connectionStatusItem = connectionStatusItem;
                    }
                    else {
                        tray.connectionStatusItem.setText(statusText);
                    }
                }
            }
        });
    }

    @Override
    protected
    void setIcon_(final String iconPath) {
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray tray = SwingSystemTray.this;
                synchronized (tray) {
                    if (!isActive) {
                        isActive = true;

                        SwingSystemTray.this.menu = new SwingSystemTrayMenuPopup();

                        Image trayImage = new ImageIcon(iconPath).getImage()
                                                                 .getScaledInstance(TRAY_SIZE, TRAY_SIZE, Image.SCALE_SMOOTH);
                        trayImage.flush();
                        final TrayIcon trayIcon = new TrayIcon(trayImage);
                        SwingSystemTray.this.trayIcon = trayIcon;

                        // appindicators don't support this, so we cater to the lowest common denominator
                        // trayIcon.setToolTip(SwingSystemTray.this.appName);

                        trayIcon.addMouseListener(new MouseAdapter() {
                            @Override
                            public
                            void mousePressed(MouseEvent e) {
                                final SwingSystemTrayMenuPopup menu = SwingSystemTray.this.menu;
                                Dimension size = menu.getPreferredSize();

                                Point point = e.getPoint();
                                Rectangle bounds = ScreenUtil.getScreenBoundsAt(point);

                                int x = point.x;
                                int y = point.y;

                                if (y < bounds.y) {
                                    y = bounds.y;
                                }
                                else if (y + size.height > bounds.y + bounds.height) {
                                    // our menu cannot have the top-edge snap to the mouse
                                    // so we make the bottom-edge snap to the mouse
                                    y -= size.height; // snap to edge of mouse
                                }

                                if (x < bounds.x) {
                                    x = bounds.x;
                                }
                                else if (x + size.width > bounds.x + bounds.width) {
                                    // our menu cannot have the left-edge snap to the mouse
                                    // so we make the right-edge snap to the mouse
                                    x -= size.width; // snap to edge of mouse
                                }

                                // weird voodoo to get this to popup with the correct parent
                                menu.setInvoker(menu);
                                menu.setLocation(x, y);
                                menu.setVisible(true);
                                menu.requestFocus();
                            }
                        });

                        try {
                            SwingSystemTray.this.tray.add(trayIcon);
                        } catch (AWTException e) {
                            logger.error("TrayIcon could not be added.", e);
                        }
                    } else {
                        Image trayImage = new ImageIcon(iconPath).getImage()
                                                                 .getScaledInstance(TRAY_SIZE, TRAY_SIZE, Image.SCALE_SMOOTH);
                        trayImage.flush();
                        tray.trayIcon.setImage(trayImage);
                    }
                }
            }
        });
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    private
    void addMenuEntry_(final String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray tray = SwingSystemTray.this;
                synchronized (tray) {
                    synchronized (menuEntries) {
                        MenuEntry menuEntry = getMenuEntry(menuText);

                        if (menuEntry != null) {
                            throw new IllegalArgumentException("Menu entry already exists for given label '" + menuText + "'");
                        }
                        else {
                            menuEntry = new SwingMenuEntry(menu, menuText, imagePath, callback, tray);
                            menuEntries.add(menuEntry);
                        }
                    }
                }
            }
        });
    }

    @Override
    public
    void addMenuEntry(String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        if (imagePath == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(imagePath), callback);
        }
    }

    @Override
    public
    void addMenuEntry(final String menuText, final URL imageUrl, final SystemTrayMenuAction callback) {
        if (imageUrl == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(imageUrl), callback);
        }
    }

    @Override
    public
    void addMenuEntry(final String menuText, final String cacheName, final InputStream imageStream, final SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(cacheName, imageStream), callback);
        }
    }

    @Override
    @Deprecated
    public
    void addMenuEntry(final String menuText, final InputStream imageStream, final SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPathNoCache(imageStream), callback);
        }
    }
}
