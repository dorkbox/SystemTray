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
package dorkbox.util.tray.swing;

import dorkbox.util.ScreenUtil;
import dorkbox.util.SwingUtil;
import dorkbox.util.tray.SystemTrayMenuAction;
import dorkbox.util.tray.SystemTrayMenuPopup;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for handling all system tray interaction, via SWING
 */
public
class SwingSystemTray extends dorkbox.util.tray.SystemTray {

    final Map<String, JMenuItem> menuEntries = new HashMap<String, JMenuItem>(2);

    volatile SystemTrayMenuPopup jmenu;
    volatile JMenuItem connectionStatusItem;

    volatile SystemTray tray;
    volatile TrayIcon trayIcon;

    /**
     * Creates a new system tray handler class.
     */
    public
    SwingSystemTray() {
    }


    @Override
    public
    void removeTray() {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray.this.tray.remove(SwingSystemTray.this.trayIcon);
                SwingSystemTray.this.menuEntries.clear();
            }
        });

        super.removeTray();
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void createTray(final String iconName) {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray.this.tray = SystemTray.getSystemTray();
                if (SwingSystemTray.this.tray == null) {
                    logger.error("The system tray is not available");
                }
                else {
                    SwingSystemTray.this.jmenu = new SystemTrayMenuPopup();

                    final Image trayImage = newImage(iconName);
                    final TrayIcon trayIcon = new TrayIcon(trayImage);
                    SwingSystemTray.this.trayIcon = trayIcon;
                    trayIcon.setToolTip(SwingSystemTray.this.appName);

                    trayIcon.addMouseListener(new MouseAdapter() {
                        @Override
                        public
                        void mousePressed(MouseEvent e) {
                            final SystemTrayMenuPopup jmenu = SwingSystemTray.this.jmenu;
                            Dimension size = jmenu.getPreferredSize();

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

                            jmenu.setInvoker(jmenu);
                            jmenu.setLocation(x, y);
                            jmenu.setVisible(true);
                            jmenu.requestFocus();
                        }
                    });

                    try {
                        SwingSystemTray.this.tray.add(trayIcon);
                        SwingSystemTray.this.active = true;
                    } catch (AWTException e) {
                        logger.error("TrayIcon could not be added.", e);
                    }
                }
            }
        });
    }

    Image newImage(String name) {
        String iconPath = iconPath(name);

        return new ImageIcon(iconPath).getImage().getScaledInstance(TRAY_SIZE, TRAY_SIZE, Image.SCALE_SMOOTH);
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void setStatus(final String infoString, final String iconName) {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                if (SwingSystemTray.this.connectionStatusItem == null) {
                    final JMenuItem connectionStatusItem = new JMenuItem(infoString);
                    connectionStatusItem.setEnabled(false);
                    SwingSystemTray.this.jmenu.add(connectionStatusItem);

                    SwingSystemTray.this.connectionStatusItem = connectionStatusItem;
                }
                else {
                    SwingSystemTray.this.connectionStatusItem.setText(infoString);
                }
            }
        });

        Image trayImage = newImage(iconName);
        SwingSystemTray.this.trayIcon.setImage(trayImage);
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    @Override
    public
    void addMenuEntry(final String menuText, final SystemTrayMenuAction callback) {
        SwingUtil.invokeAndWait(new Runnable() {
            @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
            @Override
            public
            void run() {
                final Map<String, JMenuItem> menuEntries2 = SwingSystemTray.this.menuEntries;

                synchronized (menuEntries2) {
                    JMenuItem menuEntry = menuEntries2.get(menuText);

                    if (menuEntry == null) {
                        SystemTrayMenuPopup menu = SwingSystemTray.this.jmenu;

                        menuEntry = new JMenuItem(menuText);
                        menuEntry.addActionListener(new ActionListener() {
                            @Override
                            public
                            void actionPerformed(ActionEvent e) {
                                SwingSystemTray.this.callbackExecutor.execute(new Runnable() {
                                    @Override
                                    public
                                    void run() {
                                        callback.onClick(SwingSystemTray.this);
                                    }
                                });
                            }
                        });
                        menu.add(menuEntry);

                        menuEntries2.put(menuText, menuEntry);
                    }
                    else {
                        updateMenuEntry(menuText, menuText, callback);
                    }
                }
            }
        });
    }

    /**
     * Will update an already existing menu entry (or add a new one, if it doesn't exist)
     */
    @Override
    public
    void updateMenuEntry(final String origMenuText, final String newMenuText, final SystemTrayMenuAction newCallback) {
        SwingUtil.invokeAndWait(new Runnable() {
            @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
            @Override
            public
            void run() {
                final Map<String, JMenuItem> menuEntries2 = SwingSystemTray.this.menuEntries;

                synchronized (menuEntries2) {
                    JMenuItem menuEntry = menuEntries2.get(origMenuText);

                    if (menuEntry != null) {
                        ActionListener[] actionListeners = menuEntry.getActionListeners();
                        for (ActionListener l : actionListeners) {
                            menuEntry.removeActionListener(l);
                        }

                        menuEntry.addActionListener(new ActionListener() {
                            @Override
                            public
                            void actionPerformed(ActionEvent e) {
                                SwingSystemTray.this.callbackExecutor.execute(new Runnable() {
                                    @Override
                                    public
                                    void run() {
                                        newCallback.onClick(SwingSystemTray.this);
                                    }
                                });
                            }
                        });
                        menuEntry.setText(newMenuText);
                        menuEntry.revalidate();
                    }
                    else {
                        addMenuEntry(origMenuText, newCallback);
                    }
                }
            }
        });
    }
}
