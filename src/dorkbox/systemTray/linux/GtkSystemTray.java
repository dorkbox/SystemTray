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
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.linux.jna.GtkSupport;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public
class GtkSystemTray extends GtkTypeSystemTray {
    private Pointer trayIcon;

    // have to make this a field, to prevent GC on this object
    @SuppressWarnings("FieldCanBeLocal")
    private Gobject.GEventCallback gtkCallback;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private NativeLong button_press_event;

    // This is required if we have JavaFX or SWT shutdown hooks (to prevent us from shutting down twice...)
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    private volatile boolean isActive = false;

    public
    GtkSystemTray() {
        super();
        GtkSupport.startGui();

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                final Pointer trayIcon_ = gtk.gtk_status_icon_new();
                gtk.gtk_status_icon_set_title(trayIcon_, "SystemTray@Dorkbox");
                gtk.gtk_status_icon_set_name(trayIcon_, "SystemTray");

                trayIcon = trayIcon_;

                gtkCallback = new Gobject.GEventCallback() {
                    @Override
                    public
                    void callback(Pointer notUsed, final Gtk.GdkEventButton event) {
                        // BUTTON_PRESS only (any mouse click)
                        if (event.type == 4) {
                            gtk.gtk_menu_popup(getMenu(), null, null, Gtk.gtk_status_icon_position_menu, trayIcon, 0, event.time);
                        }
                    }
                };
                button_press_event = gobject.g_signal_connect_object(trayIcon, "button_press_event", gtkCallback, null, 0);
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
                    gtk.gtk_status_icon_set_visible(trayIcon, false);
                    gobject.g_object_unref(trayIcon);

                    // GC it
                    trayIcon = null;
                    gtkCallback = null;
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
                gtk.gtk_status_icon_set_from_file(trayIcon, iconPath);

                if (!isActive) {
                    isActive = true;
                    gtk.gtk_status_icon_set_visible(trayIcon, true);
                }
            }
        });
    }
}
