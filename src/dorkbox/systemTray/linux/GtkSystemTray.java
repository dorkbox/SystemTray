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
    private Pointer trayIcon;

    // have to make this a field, to prevent GC on this object
    @SuppressWarnings("FieldCanBeLocal")
    private final Gobject.GEventCallback gtkCallback;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private NativeLong button_press_event;

    private volatile boolean isActive = false;
    private volatile Pointer menu;

    public
    GtkSystemTray() {
        super();

        gtk.gdk_threads_enter();

        final Pointer trayIcon = gtk.gtk_status_icon_new();
        gtk.gtk_status_icon_set_title(trayIcon, "SystemTray@Dorkbox");
        gtk.gtk_status_icon_set_name(trayIcon, "SystemTray");

        this.trayIcon = trayIcon;

        this.gtkCallback = new Gobject.GEventCallback() {
            @Override
            public
            void callback(Pointer notUsed, final Gtk.GdkEventButton event) {
                // BUTTON_PRESS only (any mouse click)
                if (event.type == 4) {
                    gtk.gtk_menu_popup(menu, null, null, Gtk.gtk_status_icon_position_menu, trayIcon, 0, event.time);
                }
            }
        };
        button_press_event = gobject.g_signal_connect_data(trayIcon, "button_press_event", gtkCallback, null, null, 0);

        gtk.gdk_threads_leave();

        GtkSupport.startGui();
    }

    /**
     * Called inside the gdk_threads block
     */
    protected
    void onMenuAdded(final Pointer menu) {
        this.menu = menu;
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public synchronized
    void shutdown() {
        gtk.gdk_threads_enter();

        // this hides the indicator
        gtk.gtk_status_icon_set_visible(this.trayIcon, false);
        gobject.g_object_unref(this.trayIcon);

        // GC it
        this.trayIcon = null;

//        libgtk.gdk_threads_leave(); called by parent class
        super.shutdown();
    }

    @Override
    protected synchronized
    void setIcon_(final String iconPath) {
        gtk.gdk_threads_enter();

        gtk.gtk_status_icon_set_from_file(trayIcon, iconPath);

        if (!isActive) {
            isActive = true;
            gtk.gtk_status_icon_set_visible(trayIcon, true);
        }
        gtk.gdk_threads_leave();
    }
}
