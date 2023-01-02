/*
 * Copyright 2022 dorkbox, llc
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
package dorkbox.systemTray.ui.osx;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Image;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.concurrent.CountDownLatch;

import javax.swing.ImageIcon;

import dorkbox.collections.ArrayMap;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.util.AwtAccessor;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.util.SwingUtil;

/**
 * The previous, native access we used to create menus NO LONGER works on any OS beyond Big Sur (macOS 11), and now the *best* way
 * to access this (since I do not want to rewrite a LOT of code), is to use AWT hacks to access images + tooltips via reflection. This
 * has been possible since jdk8. While I don't like reflection, it is sadly the only way to do this.
 *
 * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/7fcf35286d52/src/macosx/classes/sun/lwawt/macosx/CMenuItem.java
 */
public
class _OsxAwtTray extends Tray {
    private volatile SystemTray tray;
    private volatile TrayIcon trayIcon;

    // is the system tray visible or not.
    private volatile boolean visible = false;
    private volatile File imageFile;
    private volatile String tooltipText = "";

    private volatile CountDownLatch keepAliveLatch = new CountDownLatch(1);
    private volatile Thread keepAliveThread;

    // The image resources are cached, so that if someone is trying to create an animation, the image resource is re-used instead of
    // constantly created/destroyed -- which over time leads to issues.
    // This cache isn't anything fancy, it just lets us reuse what we have. It's cleared on hide(), and it will auto-grow as necessary.
    // If someone uses a different file every time, then this will cause problems. An error log is added if a different image is created 100x
    private final ArrayMap<String, Image> imageCache = new ArrayMap<>(false, 10);

    @SuppressWarnings("unused")
    public
    _OsxAwtTray(final String trayName, final ImageResizeUtil imageResizeUtil, final Runnable onRemoveEvent) {
        super(onRemoveEvent);

        if (!SystemTray.isSupported()) {
            throw new RuntimeException("System Tray is not supported in this configuration! Please write an issue and include your OS " +
                                       "type and configuration");
        }

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final AwtOsxMenu awtMenu = new AwtOsxMenu(null) {
            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                SwingUtil.invokeLater(()->{
                    if (tray == null) {
                        tray = SystemTray.getSystemTray();
                    }

                    boolean enabled = menuItem.getEnabled();

                    if (keepAliveThread != null) {
                        synchronized (keepAliveLatch) {
                            keepAliveLatch.countDown();
                        }
                    }
                    keepAliveThread = null;

                    if (visible && !enabled) {
                        // THIS WILL NOT keep the app running, so we use a "keep-alive" thread so this behavior is THE SAME across
                        // all platforms. This was only noticed on macOS (where the app would quit after calling setEnabled(false);
                        keepAliveThread = new Thread(()->{
                            synchronized (keepAliveLatch) {
                                keepAliveLatch.countDown();
                                keepAliveLatch = new CountDownLatch(1);
                            }
                        }, "TrayKeepAliveThread");
                        keepAliveThread.start();
                    }

                    if (visible && !enabled) {
                        tray.remove(trayIcon);
                        visible = false;
                    }
                    else if (!visible && enabled && trayIcon != null) {
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

                        trayIcon.setPopupMenu((PopupMenu) _native);

                        final AwtOsxMenu awtOsxMenu = this;

                        if (dorkbox.systemTray.SystemTray.AUTO_FIX_INCONSISTENCIES) {
                            trayIcon.addMouseListener(new MouseAdapter() {
                                @Override
                                public
                                void mouseClicked(final MouseEvent e) {
                                    final Point2D location = AwtAccessor.getLocation(trayIcon);
                                    final Component component = new Component() {
                                        @Override
                                        public
                                        Point getLocationOnScreen() {
                                            return new Point((int) location.getX()-5, (int) location.getY()+5);
                                        }
                                    };

                                    AwtAccessor.showPopup(component, _native);
                                }
                            });
                        }



                        try {
                            tray.add(trayIcon);
                            visible = true;
                        } catch (AWTException e) {
                            dorkbox.systemTray.SystemTray.logger.error("TrayIcon could not be added.", e);
                        }
                    } else {
                        trayIcon.setImage(trayImage);
                    }

                    // don't want to matter which (setImage/setTooltip/setEnabled) is done first, and if the image/enabled is changed, we
                    // want to make sure keep the tooltip text the same as before.
                    trayIcon.setToolTip(tooltipText);
                });
            }

            @Override
            public
            void setText(final MenuItem menuItem) {
                // no op.
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

                SwingUtil.invokeLater(()->{
                    if (trayIcon != null) {
                        trayIcon.setPopupMenu(null);
                        if (tray != null) {
                            tray.remove(trayIcon);
                        }

                        trayIcon = null;
                    }

                    tray = null;

                    super.remove();
                });

                // make sure this thread doesn't keep the JVM alive anymore
                if (keepAliveThread != null) {
                    synchronized (keepAliveLatch) {
                        keepAliveLatch.countDown();
                    }
                }
            }
        };

        bind(awtMenu, null, imageResizeUtil);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
