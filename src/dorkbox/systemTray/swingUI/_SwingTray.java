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
package dorkbox.systemTray.swingUI;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Tray;
import dorkbox.util.SwingUtil;

/**
 * Class for handling all system tray interaction, via Swing.
 *
 * It doesn't work well AT ALL on linux. See bugs:
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
 * https://stackoverflow.com/questions/331407/java-trayicon-using-image-with-transparent-background/3882028#3882028
 */
@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "WeakerAccess"})
public final
class _SwingTray extends Tray implements SwingUI {
    private volatile SystemTray tray;
    private volatile TrayIcon trayIcon;

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;

    // Called in the EDT
    public
    _SwingTray(final dorkbox.systemTray.SystemTray systemTray) {
        super();

        if (!SystemTray.isSupported()) {
            throw new RuntimeException("System Tray is not supported in this configuration! Please write an issue and include your OS " +
                                       "type and configuration");
        }

        _SwingTray.this.tray = SystemTray.getSystemTray();

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final SwingMenu swingMenu = new SwingMenu(null) {
            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        boolean enabled = menuItem.getEnabled();

                        if (visible && !enabled) {
                            tray.remove(trayIcon);
                            visible = false;
                        }
                        else if (!visible && enabled) {
                            try {
                                tray.add(trayIcon);
                                visible = true;
                            } catch (AWTException e) {
                                dorkbox.systemTray.SystemTray.logger.error("Error adding the icon back to the tray", e);
                            }
                        }
                    }
                });
            }

            @Override
            public
            void setImage(final MenuItem menuItem) {
                imageFile = menuItem.getImage();
                if (imageFile == null) {
                    return;
                }

                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        // stupid java won't scale it right away, so we have to do this twice to get the correct size
                        final Image trayImage = new ImageIcon(imageFile.getAbsolutePath()).getImage();
                        trayImage.flush();

                        if (trayIcon == null) {
                            // here we init. everything
                            trayIcon = new TrayIcon(trayImage);

                            JPopupMenu popupMenu = (JPopupMenu) _native;
                            popupMenu.pack();
                            popupMenu.setFocusable(true);

                            // appindicators DO NOT support anything other than PLAIN gtk-menus (which we hack to support swing menus)
                            //   they ALSO do not support tooltips, so we cater to the lowest common denominator
                            // trayIcon.setToolTip("app name");

                            trayIcon.addMouseListener(new MouseAdapter() {
                                @Override
                                public
                                void mousePressed(MouseEvent e) {
                                    TrayPopup popupMenu = (TrayPopup) _native;
                                    popupMenu.doShow(e.getPoint(), 0);
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

                        ((TrayPopup) _native).setTitleBarImage(imageFile);
                    }
                });
            }

            @Override
            public
            void setText(final MenuItem menuItem) {
                // no op
            }

            @Override
            public
            void setShortcut(final MenuItem menuItem) {
                // no op
            }

            @Override
            public
            void remove() {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (trayIcon != null) {
                            tray.remove(trayIcon);
                            trayIcon = null;
                        }

                        tray = null;
                    }
                });

                super.remove();
            }
        };

        bind(swingMenu, null, systemTray);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
