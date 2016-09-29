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

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.ImageIcon;

import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.util.ScreenUtil;
import dorkbox.util.SwingUtil;

/**
 * Class for handling all system tray interaction, via SWING.
 *
 * It doesn't work well on linux. See bugs:
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
 * https://stackoverflow.com/questions/331407/java-trayicon-using-image-with-transparent-background/3882028#3882028
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "WeakerAccess"})
public
class SwingSystemTray extends SwingMenu {
    volatile SystemTray tray;
    volatile TrayIcon trayIcon;

    /**
     * Creates a new system tray handler class.
     */
    public
    SwingSystemTray(final dorkbox.systemTray.SystemTray systemTray) {
        super(systemTray, null);

        // have to reassign our type...
        _native = new SwingSystemTrayMenuPopup();

        ImageUtils.determineIconSize(dorkbox.systemTray.SystemTray.TYPE_SWING);

        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                SwingSystemTray.this.tray = SystemTray.getSystemTray();
            }
        });
    }

    public
    void shutdown() {
        SwingUtil.invokeAndWait(new Runnable() {
            @Override
            public
            void run() {
                tray.remove(trayIcon);

                synchronized (menuEntries) {
                    for (MenuEntry menuEntry : menuEntries) {
                        menuEntry.remove();
                    }
                    menuEntries.clear();
                }
            }
        });
    }

    public
    String getStatus() {
        synchronized (menuEntries) {
            MenuEntry menuEntry = menuEntries.get(0);
            if (menuEntry instanceof SwingMenuEntryStatus) {
                return menuEntry.getText();
            }
        }

        return null;
    }

    public
    void setStatus(final String statusText) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    // status is ALWAYS at 0 index...
                    SwingMenuEntry menuEntry = null;
                    if (!menuEntries.isEmpty()) {
                        menuEntry = (SwingMenuEntry) menuEntries.get(0);
                    }

                    if (menuEntry instanceof SwingMenuEntryStatus) {
                        // set the text or delete...

                        if (statusText == null) {
                            // delete
                            removeMenuEntry(menuEntry);
                        }
                        else {
                            // set text
                            menuEntry.setText(statusText);
                        }

                    } else {
                        // create a new one
                        menuEntry = new SwingMenuEntryStatus(SwingSystemTray.this, statusText);
                        // status is ALWAYS at 0 index...
                        menuEntries.add(0, menuEntry);
                    }
                }
            }
        });
    }

    public
    void setIcon_(final File iconFile) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // stupid java won't scale it right away, so we have to do this twice to get the correct size
                final Image trayImage = new ImageIcon(iconFile.getAbsolutePath()).getImage();
                trayImage.flush();

                if (trayIcon == null) {
                    // here we init. everything
                    trayIcon = new TrayIcon(trayImage);

                    // appindicators DO NOT support anything other than PLAIN gtk-menus
                    //   they ALSO do not support tooltips, so we cater to the lowest common denominator
                    // trayIcon.setToolTip(SwingSystemTray.this.appName);

                    trayIcon.addMouseListener(new MouseAdapter() {
                        @Override
                        public
                        void mousePressed(MouseEvent e) {
                            Dimension size = _native.getPreferredSize();

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

                            // voodoo to get this to popup to have the correct parent
                            // from: http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6285881
                            ((SwingSystemTrayMenuPopup) _native).setInvoker(_native);
                            _native.setLocation(x, y);
                            _native.setVisible(true);
                            _native.setFocusable(true);
                            _native.requestFocusInWindow();
                        }
                    });

                    try {
                        tray.add(trayIcon);
                    } catch (AWTException e) {
                        dorkbox.systemTray.SystemTray.logger.error("TrayIcon could not be added.", e);
                    }
                } else {
                    trayIcon.setImage(trayImage);
                }
            }
        });
    }
}
