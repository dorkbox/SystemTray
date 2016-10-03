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
package dorkbox.systemTray.linux;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.linux.jna.GEventCallback;
import dorkbox.systemTray.linux.jna.GdkEventButton;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.util.ImageUtils;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public
class GtkSystemTray extends GtkTypeSystemTray {
    private volatile Pointer trayIcon;

    // http://code.metager.de/source/xref/gnome/Platform/gtk%2B/gtk/deprecated/gtkstatusicon.c
    // https://github.com/djdeath/glib/blob/master/gobject/gobject.c

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private final List<Object> gtkCallbacks = new ArrayList<Object>();

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile boolean isActive = false;

    public
    GtkSystemTray(final SystemTray systemTray) {
        super(systemTray);
        if (SystemTray.FORCE_TRAY_TYPE == SystemTray.TYPE_APP_INDICATOR) {
            // if we force GTK type system tray, don't attempt to load AppIndicator libs
            throw new IllegalArgumentException("Unable to start GtkStatusIcon if 'SystemTray.FORCE_TRAY_TYPE' is set to AppIndicator");
        }

        ImageUtils.determineIconSize(SystemTray.TYPE_GTK_STATUSICON);
        Gtk.startGui();

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                final Pointer trayIcon_ = Gtk.gtk_status_icon_new();
                trayIcon = trayIcon_;

                final GEventCallback gtkCallback = new GEventCallback() {
                    @Override
                    public
                    void callback(Pointer notUsed, final GdkEventButton event) {
                        // BUTTON_PRESS only (any mouse click)
                        if (event.type == 4) {
                            Gtk.gtk_menu_popup(_native, null, null, Gtk.gtk_status_icon_position_menu, trayIcon, 0, event.time);
                        }
                    }
                };
                final NativeLong button_press_event = Gobject.g_signal_connect_object(trayIcon, "button_press_event", gtkCallback,
                                                                                      null, 0);

                // have to do this to prevent GC on these objects
                gtkCallbacks.add(gtkCallback);
                gtkCallbacks.add(button_press_event);
            }
        });

        Gtk.waitForStartup();

        // we have to be able to set our title, otherwise the gnome-shell extension WILL NOT work
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // by default, the title/name of the tray icon is "java". We are the only java-based tray icon, so we just use that.
                // If you change "SystemTray" to something else, make sure to change it in extension.js as well

                // necessary for gnome icon detection/placement because we move tray icons around by title. This is hardcoded
                //  in extension.js, so don't change it
                Gtk.gtk_status_icon_set_title(trayIcon, "SystemTray");

                // can cause
                // Gdk-CRITICAL **: gdk_window_thaw_toplevel_updates: assertion 'window->update_and_descendants_freeze_count > 0' failed
                // Gdk-CRITICAL **: IA__gdk_window_thaw_toplevel_updates_libgtk_only: assertion 'private->update_and_descendants_freeze_count > 0' failed

                // ... so, bizzaro things going on here. These errors DO NOT happen if JavaFX is dispatching the events.
                //     BUT   this is REQUIRED when running JavaFX. For unknown reasons, the title isn't pushed to GTK, so our
                //           gnome-shell extension cannot see our tray icon -- so naturally, it won't move it to the "top" area and
                //           we appear broken.
                if (SystemTray.isJavaFxLoaded) {
                    Gtk.gtk_status_icon_set_name(trayIcon, "SystemTray");
                }
            }
        });
    }


    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void shutdown() {
        if (!shuttingDown.getAndSet(true)) {
            dispatchAndWait(new Runnable() {
                @Override
                public
                void run() {
                    // this hides the indicator
                    Gtk.gtk_status_icon_set_visible(trayIcon, false);
                    Gobject.g_object_unref(trayIcon);

                    // mark for GC
                    trayIcon = null;
                    gtkCallbacks.clear();
                }
            });

            super.shutdown();
        }
    }

    public
    void setIcon_(final File iconFile) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_status_icon_set_from_file(trayIcon, iconFile.getAbsolutePath());

                if (!isActive) {
                    isActive = true;
                    Gtk.gtk_status_icon_set_visible(trayIcon, true);
                }
            }
        });
    }
}
