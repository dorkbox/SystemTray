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
package dorkbox.systemTray.ui.gtk;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.Tray;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.jna.linux.AppIndicator;
import dorkbox.jna.linux.GObject;
import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.jna.linux.structs.AppIndicatorInstanceStruct;

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
public final
class _AppIndicatorNativeTray extends Tray {
    public static boolean isLoaded = false;

    private volatile AppIndicatorInstanceStruct appIndicator;
    private boolean isActive = false;

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    // is the system tray visible or not.
    private volatile boolean visible = true;
    private volatile File imageFile;

    // has the name already been set for the indicator?
    private volatile boolean setName = false;

    // appindicators DO NOT support anything other than PLAIN gtk-menus
    //   they ALSO do not support tooltips!!
    //  https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12

    public
    _AppIndicatorNativeTray(final SystemTray systemTray) {
        super(systemTray);

        isLoaded = true;

        // we override various methods, because each tray implementation is SLIGHTLY different. This allows us customization.
        final GtkMenu gtkMenu = new GtkMenu(systemTray) {
            /**
             * MUST BE AFTER THE ITEM IS ADDED/CHANGED from the menu
             *
             * ALWAYS CALLED ON THE EDT
             */
            @Override
            protected final
            void onMenuAdded(final Pointer menu) {
                // see: https://code.launchpad.net/~mterry/libappindicator/fix-menu-leak/+merge/53247
                appIndicator.app_indicator_set_menu(menu);

                if (!setName) {
                    setName = true;

                    // in GNOME by default, the title/name of the tray icon is "java". We are the only java-based tray icon, so we just use that.
                    // If you change "SystemTray" to something else, make sure to change it in extension.js as well

                    // can cause (potentially)
                    // GLib-GIO-CRITICAL **: g_dbus_connection_emit_signal: assertion 'object_path != NULL && g_variant_is_object_path (object_path)' failed
                    // Gdk-CRITICAL **: IA__gdk_window_thaw_toplevel_updates_libgtk_only: assertion 'private->update_and_descendants_freeze_count > 0' failed

                    // necessary for gnome icon detection/placement because we move tray icons around by title. This is hardcoded
                    //  in extension.js, so don't change it

                    // additionally, this is required to be set HERE (not somewhere else)
                    appIndicator.app_indicator_set_title(SystemTray.APP_NAME);
                }
            }

            @Override
            public
            void setEnabled(final MenuItem menuItem) {
                GtkEventDispatch.dispatch(new Runnable() {
                    @Override
                    public
                    void run() {
                        boolean enabled = menuItem.getEnabled();

                        if (visible && !enabled) {
                            // STATUS_PASSIVE hides the indicator
                            appIndicator.app_indicator_set_status(AppIndicator.STATUS_PASSIVE);
                            visible = false;
                        }
                        else if (!visible && enabled) {
                            appIndicator.app_indicator_set_status(AppIndicator.STATUS_ACTIVE);
                            visible = true;
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

                GtkEventDispatch.dispatch(new Runnable() {
                    @Override
                    public
                    void run() {
                        appIndicator.app_indicator_set_icon(imageFile.getAbsolutePath());

                        if (!isActive) {
                            isActive = true;
                            appIndicator.app_indicator_set_status(AppIndicator.STATUS_ACTIVE);
                        }
                    }
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
                // no op.
            }

            @Override
            public
            void setTooltip(final MenuItem menuItem) {
                // no op. see https://bugs.launchpad.net/indicator-application/+bug/527458/comments/12
            }

            @Override
            public
            void remove() {
                // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
                if (!shuttingDown.getAndSet(true)) {
                    super.remove();

                    GtkEventDispatch.dispatch(new Runnable() {
                        @Override
                        public
                        void run() {
                            // must happen asap, so our hook properly notices we are in shutdown mode
                            final AppIndicatorInstanceStruct savedAppIndicator = appIndicator;
                            appIndicator = null;

                            // STATUS_PASSIVE hides the indicator
                            savedAppIndicator.app_indicator_set_status(AppIndicator.STATUS_PASSIVE);
                            Pointer p = savedAppIndicator.getPointer();
                            GObject.g_object_unref(p);

                            GtkEventDispatch.shutdownGui();
                        }
                    });
                }
            }
        };

        GtkEventDispatch.dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                String id = "DBST" + System.nanoTime();

                // we initialize with a blank image. Throws RuntimeException if not possible (this should never happen!)
                // Ubuntu 17.10 REQUIRES this to be the correct tray image size, otherwise we get the error:
                // GLib-GIO-CRITICAL **: g_dbus_proxy_new: assertion 'G_IS_DBUS_CONNECTION (connection)' failed
                File image = ImageResizeUtil.getTransparentImage(systemTray.getTrayImageSize());
                appIndicator = AppIndicator.app_indicator_new(id, image.getAbsolutePath(), AppIndicator.CATEGORY_APPLICATION_STATUS);
            }
        });

        GtkEventDispatch.waitForEventsToComplete();

        bind(gtkMenu, null, systemTray);
    }

    @Override
    public
    boolean hasImage() {
        return imageFile != null;
    }
}
