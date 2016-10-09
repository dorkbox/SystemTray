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

import static dorkbox.systemTray.SystemTray.TIMEOUT;

import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JPopupMenu;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.linux.jna.AppIndicator;
import dorkbox.systemTray.linux.jna.AppIndicatorInstanceStruct;
import dorkbox.systemTray.linux.jna.GEventCallback;
import dorkbox.systemTray.linux.jna.GdkEventButton;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.util.ScreenUtil;
import dorkbox.util.SwingUtil;

/**
 * Class for handling all system tray interactions.
 * specialization for using app indicators in ubuntu unity
 *
 * Derived from
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 *
 * AppIndicators DO NOT support anything other than plain gtk-menus, because of how they use dbus so no tooltips AND no custom widgets
 *
 *
 *
 * As a result of this decision by Canonical, we have to resort to hacks to get it to do what we want.  BY NO MEANS IS THIS PERFECT.
 *
 *
 * We still cannot have tooltips, but we *CAN* have custom widgets in the menu (because it's our swing menu now...)
 *
 *
 * It would be too much work to re-implement AppIndicators, or even to use LD_PRELOAD + restart service to do what we want.
 *
 * As a result, we have some wicked little hacks which are rather effective (but have a small side-effect of very briefly
 * showing a blank menu)
 *
 * // What are AppIndicators?
 * http://unity.ubuntu.com/projects/appindicators/
 *
 *
 * // Entry-point into appindicators
 * http://bazaar.launchpad.net/~unity-team/unity/trunk/view/head:/services/panel-main.c
 *
 *
 * // The idiocy of appindicators
 * https://bugs.launchpad.net/screenlets/+bug/522152
 *
 * // Code of how the dbus menus work
 * http://bazaar.launchpad.net/~dbusmenu-team/libdbusmenu/trunk.16.10/view/head:/libdbusmenu-gtk/client.c
 * https://developer.ubuntu.com/api/devel/ubuntu-12.04/c/dbusmenugtk/index.html
 *
 * // more info about trying to put widgets into GTK menus
 * http://askubuntu.com/questions/16431/putting-an-arbitrary-gtk-widget-into-an-appindicator-indicator
 *
 * // possible idea on how to get GTK widgets into GTK menus
 * https://launchpad.net/ido
 * http://bazaar.launchpad.net/~canonical-dx-team/ido/trunk/view/head:/src/idoentrymenuitem.c
 * http://bazaar.launchpad.net/~ubuntu-desktop/ido/gtk3/files
 */
public
class AppIndicatorTray extends SwingGenericTray {
    private AppIndicatorInstanceStruct appIndicator;
    private boolean isActive = false;

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile NativeLong nativeLong;
    private volatile GEventCallback gtkCallback;
    private Pointer dummyMenu;
    private final Runnable popupRunnable;

    public
    AppIndicatorTray(final SystemTray systemTray) {
        super(systemTray,null, new SwingSystemTrayMenuPopup());

        if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_GTK_STATUSICON) {
            // if we force GTK type system tray, don't attempt to load AppIndicator libs
            throw new IllegalArgumentException("Unable to start AppIndicator if 'SystemTray.FORCE_TRAY_TYPE' is set to GtkStatusIcon");
        }

        JPopupMenu popupMenu = (JPopupMenu) _native;
        popupMenu.pack();
        popupMenu.setFocusable(true);

        popupRunnable = new Runnable() {
            @Override
            public
            void run() {
                Dimension size = _native.getPreferredSize();

                Point point = MouseInfo.getPointerInfo()
                                       .getLocation();
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

                    x -= 32; // display over the stupid appindicator menu (which has to show, this is a major hack!)
                }
                else if (x + size.width > bounds.x + bounds.width) {
                    // our menu cannot have the left-edge snap to the mouse
                    // so we make the right-edge snap to the mouse
                    x -= size.width; // snap to edge of mouse

                    x += 32; // display over the stupid appindicator menu (which has to show, this is a major hack!)
                }

                SwingSystemTrayMenuPopup popupMenu = (SwingSystemTrayMenuPopup) _native;
                popupMenu.doShow(x, y);

                // Such ugly hacks to get AppIndicator support properly working. This is so horrible I am ashamed.
                Gtk.dispatch(new Runnable() {
                    @Override
                    public
                    void run() {
                        createAppIndicatorMenu();
                        hookMenuOpen();
                    }
                });
            }
        };

        // appindicators DO NOT support anything other than PLAIN gtk-menus
        //   they ALSO do not support tooltips, so we cater to the lowest common denominator
        // trayIcon.setToolTip(SwingSystemTray.this.appName);

        Gtk.startGui();

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // we initialize with a blank image
                File image = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE);
                String id = System.nanoTime() + "DBST";
                appIndicator = AppIndicator.app_indicator_new(id, image.getAbsolutePath(), AppIndicator.CATEGORY_APPLICATION_STATUS);

                createAppIndicatorMenu();
            }
        });

        Gtk.waitForStartup();
    }

    private
    void hookMenuOpen() {
        // now we have to setup a way for us to catch the "activation" click on this menu. Must be after the menu is set
        PointerByReference menuServer = new PointerByReference();
        PointerByReference rootMenuItem = new PointerByReference();

        Gobject.g_object_get(appIndicator.getPointer(), "dbus-menu-server", menuServer, null);
        Gobject.g_object_get(menuServer.getValue(), "root-node", rootMenuItem, null);

        gtkCallback = new GEventCallback() {
            @Override
            public
            void callback(Pointer notUsed, final GdkEventButton event) {
                Gtk.gtk_widget_destroy(dummyMenu);
                SwingUtil.invokeLater(popupRunnable);
            }
        };

        nativeLong = Gobject.g_signal_connect_object(rootMenuItem.getValue(), "about-to-show", gtkCallback, null, 0);
    }

    private void createAppIndicatorMenu() {
        dummyMenu = Gtk.gtk_menu_new();
        Pointer item = Gtk.gtk_image_menu_item_new_with_mnemonic("");
        Gtk.gtk_menu_shell_append(dummyMenu, item);
        Gtk.gtk_widget_show_all(item);

        AppIndicator.app_indicator_set_menu(appIndicator, dummyMenu);
    }

    public
    void shutdown() {
        if (!shuttingDown.getAndSet(true)) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);

            Gtk.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    try {
                        // STATUS_PASSIVE hides the indicator
                        AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_PASSIVE);
                        Pointer p = appIndicator.getPointer();
                        Gobject.g_object_unref(p);

                        appIndicator = null;
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });

            // this is slightly different than how swing does it. We have a timeout here so that we can make sure that updates on the GUI
            // thread occur in REASONABLE time-frames, and alert the user if not.
            try {
                if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                    SystemTray.logger.error("Event dispatch queue took longer than " + TIMEOUT + " seconds to shutdown. Please adjust " +
                                            "`SystemTray.TIMEOUT` to a value which better suites your environment.");
                }
            } catch (InterruptedException e) {
                SystemTray.logger.error("Error waiting for shutdown dispatch to complete.", new Exception());
            }

            Gtk.shutdownGui();

            // uses EDT
            super.remove();
        }
    }

    public
    void setImage_(final File imageFile) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                AppIndicator.app_indicator_set_icon(appIndicator, imageFile.getAbsolutePath());

                if (!isActive) {
                    isActive = true;

                    AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_ACTIVE);

                    // kindof lame, but necessary for KDE
                    if (Gtk.isKDE) {
                        AppIndicator.app_indicator_set_label(appIndicator, "SystemTray", null);
                    }

                    // now we have to setup a way for us to catch the "activation" click on this menu. Must be after the menu is set
                    hookMenuOpen();
                }
            }
        });

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                ((SwingSystemTrayMenuPopup) _native).setTitleBarImage(imageFile);
            }
        });
    }
}
