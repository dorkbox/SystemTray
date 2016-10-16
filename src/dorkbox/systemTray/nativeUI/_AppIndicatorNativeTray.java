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
package dorkbox.systemTray.nativeUI;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.linux.AppIndicator;
import dorkbox.systemTray.jna.linux.AppIndicatorInstanceStruct;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.util.ImageUtils;

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
@SuppressWarnings("Duplicates")
public
class _AppIndicatorNativeTray extends GtkMenu {
    private volatile AppIndicatorInstanceStruct appIndicator;
    private boolean isActive = false;

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    // is the system tray visible or not.
    private volatile boolean visible = true;

    // appindicators DO NOT support anything other than PLAIN gtk-menus (which we hack to support swing menus)
    //   they ALSO do not support tooltips, so we cater to the lowest common denominator
    // trayIcon.setToolTip("app name");

    public
    _AppIndicatorNativeTray(final SystemTray systemTray) {
        super(systemTray, null);

        Gtk.startGui();

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // we initialize with a blank image
                File image = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE);
                String id = System.nanoTime() + "DBST";
                appIndicator = AppIndicator.app_indicator_new(id, image.getAbsolutePath(), AppIndicator.CATEGORY_APPLICATION_STATUS);
            }
        });

        Gtk.waitForStartup();
    }

    public final
    void shutdown() {
        if (!shuttingDown.getAndSet(true)) {
            // must happen asap, so our hook properly notices we are in shutdown mode
            final AppIndicatorInstanceStruct savedAppIndicator = appIndicator;
            appIndicator = null;

            Gtk.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    // STATUS_PASSIVE hides the indicator
                    AppIndicator.app_indicator_set_status(savedAppIndicator, AppIndicator.STATUS_PASSIVE);
                    Pointer p = savedAppIndicator.getPointer();
                    Gobject.g_object_unref(p);
                }
            });

            super.shutdown();
        }
    }

    @Override
    public final
    boolean hasImage() {
        return true;
    }

    @Override
    public final
    void setImage_(final File imageFile) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                AppIndicator.app_indicator_set_icon(appIndicator, imageFile.getAbsolutePath());

                if (!isActive) {
                    isActive = true;

                    AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_ACTIVE);
                }
            }
        });
    }

    @Override
    public final
    void setEnabled(final boolean setEnabled) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                 if (visible && !setEnabled) {
                    // STATUS_PASSIVE hides the indicator
                    AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_PASSIVE);
                    visible = false;
                }
                else if (!visible && setEnabled) {
                    AppIndicator.app_indicator_set_status(appIndicator, AppIndicator.STATUS_ACTIVE);
                    visible = true;
                }
            }
        });
    }

    /**
     * MUST BE AFTER THE ITEM IS ADDED/CHANGED from the menu
     */
    protected final
    void onMenuAdded(final Pointer menu) {
        // see: https://code.launchpad.net/~mterry/libappindicator/fix-menu-leak/+merge/53247
        AppIndicator.app_indicator_set_menu(appIndicator, menu);
    }
}
