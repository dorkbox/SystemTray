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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dorkbox.systemTray.linux.jna.GEventCallback;
import dorkbox.systemTray.linux.jna.GdkEventButton;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public
class GtkSystemTray extends GtkTypeSystemTray {
    private Pointer trayIcon;

    // have to save these in a field to prevent GC on the objects (since they go out-of-scope from java)
    private final List<Object> gtkCallbacks = new ArrayList<Object>();

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile boolean isActive = false;

    public
    GtkSystemTray() {
        super();
        Gtk.startGui();

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                final Pointer trayIcon_ = Gtk.gtk_status_icon_new();
                Gtk.gtk_status_icon_set_name(trayIcon_, "SystemTray");

                trayIcon = trayIcon_;

                final GEventCallback gtkCallback = new GEventCallback() {
                    @Override
                    public
                    void callback(Pointer notUsed, final GdkEventButton event) {
                        // BUTTON_PRESS only (any mouse click)
                        if (event.type == 4) {
                            Gtk.gtk_menu_popup(getMenu(), null, null, Gtk.gtk_status_icon_position_menu, trayIcon, 0, event.time);
                        }
                    }
                };
                final NativeLong button_press_event = Gobject.g_signal_connect_object(trayIcon, "button_press_event", gtkCallback, null, 0);

                // have to do this to prevent GC on these objects
                gtkCallbacks.add(gtkCallback);
                gtkCallbacks.add(button_press_event);
            }
        });
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void shutdown() {
        if (!shuttingDown.getAndSet(true)) {
            dispatch(new Runnable() {
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

    @Override
    protected
    void setIcon_(final String iconPath) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_status_icon_set_from_file(trayIcon, iconPath);

                if (!isActive) {
                    isActive = true;
                    Gtk.gtk_status_icon_set_visible(trayIcon, true);
                }
            }
        });
    }
}
