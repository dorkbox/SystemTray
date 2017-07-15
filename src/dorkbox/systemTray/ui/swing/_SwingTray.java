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
package dorkbox.systemTray.ui.swing;

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
import dorkbox.util.OS;
import dorkbox.util.SwingUtil;
import dorkbox.util.jna.linux.GtkEventDispatch;

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
class _SwingTray extends Tray {
    private volatile SystemTray tray;
    private volatile TrayIcon trayIcon;

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;
    private volatile String tooltipText = "";

    // Called in the EDT
    public
    _SwingTray(final dorkbox.systemTray.SystemTray systemTray) {
        super();

        if (!SystemTray.isSupported()) {
            throw new RuntimeException("System Tray is not supported in this configuration! Please write an issue and include your OS " +
                                       "type and configuration");
        }

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final SwingMenu swingMenu = new SwingMenu(null, null) {
            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                SwingUtil.invokeLater(new Runnable() {
                    @Override
                    public
                    void run() {
                        if (tray == null) {
                            tray = SystemTray.getSystemTray();
                        }

                        boolean enabled = menuItem.getEnabled();

                        if (visible && !enabled) {
                            tray.remove(trayIcon);
                            visible = false;
                        }
                        else if (!visible && enabled) {
                            try {
                                tray.add(trayIcon);
                                visible = true;

                                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                                // want to make sure keep the tooltip text the same as before.
                                trayIcon.setToolTip(tooltipText);
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
                        if (tray == null) {
                            tray = SystemTray.getSystemTray();
                        }

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

                        // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                        // want to make sure keep the tooltip text the same as before.
                        trayIcon.setToolTip(tooltipText);

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
                            if (tray != null) {
                                tray.remove(trayIcon);
                            }
                            trayIcon = null;
                        }

                        tray = null;
                    }
                });

                super.remove();


                if (OS.isLinux() || OS.isUnix()) {
                    // does not need to be called on the dispatch (it does that). Startup happens in the SystemTray (in a special block),
                    // because we MUST startup the system tray BEFORE to access GTK before we create the swing version (to get size info)
                    GtkEventDispatch.shutdownGui();
                }
            }
        };

        bind(swingMenu, null, systemTray);
    }

    @Override
    protected
    void setTooltip_(final String tooltipText) {
        if (this.tooltipText.equals(tooltipText)){
            return;
        }
        this.tooltipText = tooltipText;

        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                // want to make sure keep the tooltip text the same as before.
                if (trayIcon != null) {
                    trayIcon.setToolTip(tooltipText);
                }
            }
        });
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
