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
import dorkbox.util.tray.SystemTray;
import dorkbox.util.tray.SystemTrayMenuAction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for handling all system tray interactions via GTK.
 * <p/>
 * This is the "old" way to do it, and does not work with some desktop environments.
 */
public
class GtkSystemTray extends SystemTray {
    private static final Gobject libgobject = Gobject.INSTANCE;
    private static final Gtk libgtk = Gtk.INSTANCE;

    private final Map<String, MenuEntry> menuEntries = new HashMap<String, MenuEntry>(2);

    private volatile Pointer menu;
    private volatile Pointer connectionStatusItem;;

    private volatile Pointer trayIcon;

    // need to hang on to these to prevent gc
    private final List<Pointer> widgets = new ArrayList<Pointer>(4);

    // have to make this a field, to prevent GC on this object
    @SuppressWarnings("FieldCanBeLocal")
    private Gobject.GEventCallback gtkCallback;

    public
    GtkSystemTray() {
    }

    @Override
    public
    void createTray(String iconName) {
        libgtk.gdk_threads_enter();

        this.menu = libgtk.gtk_menu_new();

        final Pointer trayIcon = libgtk.gtk_status_icon_new();
        libgtk.gtk_status_icon_set_from_file(trayIcon, iconPath(iconName));

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

//       This is unreliable to use in our gnome-shell notification hook, because of race conditions, it will only sometimes be correct
//        libgtk.gtk_status_icon_set_title(trayIcon, "something");

        libgtk.gtk_status_icon_set_tooltip(trayIcon, this.appName);
        libgtk.gtk_status_icon_set_visible(trayIcon, true);

        this.trayIcon = trayIcon;

        libgtk.gdk_threads_leave();

        this.active = true;
    }

    @SuppressWarnings("FieldRepeatedlyAccessedInMethod")
    @Override
    public
    void removeTray() {
        libgtk.gdk_threads_enter();
        for (Pointer widget : this.widgets) {
            libgtk.gtk_widget_destroy(widget);
        }

        // this hides the indicator
        libgtk.gtk_status_icon_set_visible(this.trayIcon, false);
        libgobject.g_object_unref(this.trayIcon);

        this.active = false;

        // GC it
        this.trayIcon = null;
        this.widgets.clear();

        // unrefs the children too
        libgobject.g_object_unref(this.menu);
        this.menu = null;

        synchronized (this.menuEntries) {
            this.menuEntries.clear();
        }

        this.connectionStatusItem = null;

        GtkSupport.shutdownGTK();

        libgtk.gdk_threads_leave();
        super.removeTray();
    }

    @SuppressWarnings({"FieldRepeatedlyAccessedInMethod", "Duplicates"})
    @Override
    public
    void setStatus(final String infoString, String iconName) {
        libgtk.gdk_threads_enter();
        if (this.connectionStatusItem == null) {
            this.connectionStatusItem = libgtk.gtk_menu_item_new_with_label(infoString);
            this.widgets.add(this.connectionStatusItem);
            libgtk.gtk_widget_set_sensitive(this.connectionStatusItem, Gtk.FALSE);
            libgtk.gtk_menu_shell_append(this.menu, this.connectionStatusItem);
        }
        else {
            libgtk.gtk_menu_item_set_label(this.connectionStatusItem, infoString);
        }

        libgtk.gtk_widget_show_all(this.connectionStatusItem);

        libgtk.gtk_status_icon_set_from_file(GtkSystemTray.this.trayIcon, iconPath(iconName));
        libgtk.gdk_threads_leave();
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    @SuppressWarnings("Duplicates")
    @Override
    public
    void addMenuEntry(final String menuText, final SystemTrayMenuAction callback) {
        synchronized (this.menuEntries) {
            MenuEntry menuEntry = this.menuEntries.get(menuText);

            if (menuEntry == null) {
                libgtk.gdk_threads_enter();

                Pointer dashboardItem = libgtk.gtk_menu_item_new_with_label(menuText);

                // have to watch out! These can get garbage collected!
                Gobject.GCallback gtkCallback = new Gobject.GCallback() {
                    @Override
                    public
                    void callback(Pointer instance, Pointer data) {
                        GtkSystemTray.this.callbackExecutor.execute(new Runnable() {
                            @Override
                            public
                            void run() {
                                callback.onClick(GtkSystemTray.this);
                            }
                        });
                    }
                };

                libgobject.g_signal_connect_data(dashboardItem, "activate", gtkCallback, null, null, 0);
                libgtk.gtk_menu_shell_append(this.menu, dashboardItem);
                libgtk.gtk_widget_show_all(dashboardItem);

                libgtk.gdk_threads_leave();

                menuEntry = new MenuEntry();
                menuEntry.dashboardItem = dashboardItem;
                menuEntry.gtkCallback = gtkCallback;

                this.menuEntries.put(menuText, menuEntry);
            }
            else {
                updateMenuEntry(menuText, menuText, callback);
            }
        }
    }

    /**
     * Will update an already existing menu entry (or add a new one, if it doesn't exist)
     */
    @SuppressWarnings("Duplicates")
    @Override
    public
    void updateMenuEntry(final String origMenuText, final String newMenuText, final SystemTrayMenuAction newCallback) {
        synchronized (this.menuEntries) {
            MenuEntry menuEntry = this.menuEntries.get(origMenuText);

            if (menuEntry != null) {
                libgtk.gdk_threads_enter();
                libgtk.gtk_menu_item_set_label(menuEntry.dashboardItem, newMenuText);

                // have to watch out! These can get garbage collected!
                menuEntry.gtkCallback = new Gobject.GCallback() {
                    @Override
                    public
                    void callback(Pointer instance, Pointer data) {
                        GtkSystemTray.this.callbackExecutor.execute(new Runnable() {
                            @Override
                            public
                            void run() {
                                newCallback.onClick(GtkSystemTray.this);
                            }
                        });
                    }
                };

                libgobject.g_signal_connect_data(menuEntry.dashboardItem, "activate", menuEntry.gtkCallback, null, null, 0);

                libgtk.gtk_widget_show_all(menuEntry.dashboardItem);
                libgtk.gdk_threads_leave();
            }
            else {
                addMenuEntry(origMenuText, newCallback);
            }
        }
    }
}
