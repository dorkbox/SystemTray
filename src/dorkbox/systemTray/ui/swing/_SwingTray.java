/*
 * Copyright 2021 dorkbox, llc
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
import java.awt.Point;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JPopupMenu;

import dorkbox.collections.ArrayMap;
import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.os.OS;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.systemTray.util.SizeAndScalingUtil;
import dorkbox.util.SwingUtil;

/**
 * Class for handling all system tray interaction, via Swing.
 *
 * It doesn't work well AT ALL on linux. See bugs:
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6267936
 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6453521
 * https://stackoverflow.com/questions/331407/java-trayicon-using-image-with-transparent-background/3882028#3882028
 */
@SuppressWarnings({"WeakerAccess"})
public final
class _SwingTray extends Tray {
    private volatile SystemTray tray;
    private volatile TrayIcon trayIcon;

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;
    private volatile String tooltipText = "";

    // The image resources are cached, so that if someone is trying to create an animation, the image resource is re-used instead of
    // constantly created/destroyed -- which over time leads to issues.
    // This cache isn't anything fancy, it just lets us reuse what we have. It's cleared on hide(), and it will auto-grow as necessary.
    // If someone uses a different file every time, then this will cause problems. An error log is added if a different image is created 100x
    private final ArrayMap<String, Image> imageCache = new ArrayMap<>(false, 10);

    // Called in the EDT
    @SuppressWarnings("unused")
    public
    _SwingTray(final String trayName, final ImageResizeUtil imageResizeUtil, final Runnable onRemoveEvent) {
        super(onRemoveEvent);

        if (!SystemTray.isSupported()) {
            throw new RuntimeException("System Tray is not supported in this configuration! Please write an issue and include your OS " +
                                       "type and configuration");
        }

        // setup some swing menu bits...
        // This creates the transparent icon
        SwingMenuItem.createTransparentIcon(SizeAndScalingUtil.TRAY_MENU_SIZE, imageResizeUtil);
        SwingMenuItemCheckbox.createCheckedIcon(SizeAndScalingUtil.TRAY_MENU_SIZE);


        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final SwingMenu swingMenu = new SwingMenu(trayName) {
            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                SwingUtil.invokeLater(()->{
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
                });
            }

            @Override
            public
            void setImage(final MenuItem menuItem) {
                imageFile = menuItem.getImage();

                SwingUtil.invokeLater(()->{
                    if (tray == null) {
                        tray = SystemTray.getSystemTray();
                    }

                    final Image trayImage;
                    if (imageFile != null) {
                        String path = imageFile.getAbsolutePath();
                        synchronized (imageCache) {
                            Image previousImage = imageCache.get(path);
                            if (previousImage == null) {
                                previousImage = new ImageIcon(path).getImage();
                                imageCache.put(path, previousImage);
                                if (imageCache.size > 120) {
                                    dorkbox.systemTray.SystemTray.logger.error("More than 120 different images used for the SystemTray icon. This will lead to performance issues.");
                                }
                            }

                            trayImage = previousImage;
                        }
                    } else {
                        trayImage = null;
                    }


                    if (trayIcon == null) {
                        if (trayImage == null) {
                            // we can't do anything!
                            return;
                        } else {
                            trayIcon = new TrayIcon(trayImage);
                        }

                        // here we init. everything
                        JPopupMenu popupMenu = (JPopupMenu) _native;
                        popupMenu.pack();
                        popupMenu.setFocusable(true);

                        if (tooltipText != null && !tooltipText.isEmpty()) {
                            trayIcon.setToolTip(tooltipText);
                        }

                        trayIcon.addMouseListener(new MouseAdapter() {
                            @Override
                            public
                            void mousePressed(MouseEvent e) {
                                TrayPopup popupMenu = (TrayPopup) _native;
                                Point mousePosition = e.getPoint();

                                double scale = SizeAndScalingUtil.getDpiScaleForMouseClick(mousePosition.x, mousePosition.y);
                                Point point = new Point((int) (mousePosition.x * scale), (int) (mousePosition.y * scale));
                                popupMenu.doShow(point, 0);
                            }
                        });

                        try {
                            tray.add(trayIcon);
                        } catch (AWTException e) {
                            dorkbox.systemTray.SystemTray.logger.error("TrayIcon could not be added.", e);
                        }
                    } else if (trayImage != null) {
                        trayIcon.setImage(trayImage);
                    }

                    // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                    // want to make sure keep the tooltip text the same as before.
                    trayIcon.setToolTip(tooltipText);

                    if (imageFile != null) {
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
            void setTooltip(final MenuItem menuItem) {
                final String text = menuItem.getTooltip();

                if (tooltipText != null && tooltipText.equals(text) ||
                    tooltipText == null && text != null) {
                    return;
                }

                tooltipText = text;

                SwingUtil.invokeLater(()->{
                    // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                    // want to make sure keep the tooltip text the same as before.
                    if (trayIcon != null) {
                        trayIcon.setToolTip(text);
                    }
                });
            }

            @Override
            public
            void remove() {
                synchronized (imageCache) {
                    for (final Image value : imageCache.values()) {
                        value.flush();
                    }

                    imageCache.clear();
                }

                SwingUtil.invokeAndWaitQuietly(()->{
                    if (trayIcon != null) {
                        if (tray != null) {
                            tray.remove(trayIcon);
                        }
                        trayIcon = null;
                    }

                    tray = null;
                });

                super.remove();


                if (OS.INSTANCE.isLinux() || OS.INSTANCE.isUnix()) {
                    // does not need to be called on the dispatch (it does that). Startup happens in the SystemTray (in a special block),
                    // because we MUST startup the system tray BEFORE to access GTK before we create the swing version (to get size info)
                    GtkEventDispatch.shutdownGui();
                }
            }
        };

        bind(swingMenu, null, imageResizeUtil);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
