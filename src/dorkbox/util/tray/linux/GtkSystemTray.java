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
package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.GtkSupport;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public
class GtkSystemTray extends GtkTypeSystemTray {
    private volatile Pointer trayIcon;

    // have to make this a field, to prevent GC on this object
    @SuppressWarnings("FieldCanBeLocal")
    private Gobject.GEventCallback gtkCallback;

    public
    GtkSystemTray(String iconName) {
        super();

        libgtk.gdk_threads_enter();

        final Pointer trayIcon = libgtk.gtk_status_icon_new();
        libgtk.gtk_status_icon_set_title(trayIcon, "SystemTray@Dorkbox");

        this.trayIcon = trayIcon;

        libgtk.gtk_status_icon_set_from_file(trayIcon, iconPath(iconName));

        this.menu = libgtk.gtk_menu_new();
        this.gtkCallback = new Gobject.GEventCallback() {
            @Override
            public
            void callback(Pointer system_tray, final Gtk.GdkEventButton event) {
                // BUTTON_PRESS only (any mouse click)
                if (event.type == 4) {
                    libgtk.gtk_menu_popup(menu, null, null, Gtk.gtk_status_icon_position_menu, system_tray, 0, event.time);
                }
            }
        };
        libgobject.g_signal_connect_data(trayIcon, "button_press_event", gtkCallback, menu, null, 0);
        libgtk.gtk_status_icon_set_visible(trayIcon, true);

        libgtk.gdk_threads_leave();

        GtkSupport.startGui();
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void shutdown() {
        libgtk.gdk_threads_enter();

        // this hides the indicator
        libgtk.gtk_status_icon_set_visible(this.trayIcon, false);
        libgobject.g_object_unref(this.trayIcon);

        // GC it
        this.trayIcon = null;

//        libgtk.gdk_threads_leave(); called by parent class
        super.shutdown();
    }

    @Override
    public
    void setIcon(final String iconName) {
        libgtk.gdk_threads_enter();
        libgtk.gtk_status_icon_set_from_file(trayIcon, iconPath(iconName));
        libgtk.gdk_threads_leave();
    }
}
