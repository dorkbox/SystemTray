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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.jna.Pointer;

import dorkbox.util.jna.linux.AppIndicator;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.tray.SystemTray;
import dorkbox.util.tray.SystemTrayMenuAction;

/**
 * Class for handling all system tray interactions.
 *
 * specialization for using app indicators in ubuntu unity
 *
 * Heavily modified from
 *
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public class AppIndicatorTray extends SystemTray {
    private static final AppIndicator libappindicator = AppIndicator.INSTANCE;
    private static final Gobject libgobject = Gobject.INSTANCE;
    private static final Gtk libgtk = Gtk.INSTANCE;

    private final Map<String, MenuEntry> menuEntries = new HashMap<String, MenuEntry>(2);

    private volatile AppIndicator.AppIndicatorInstanceStruct appIndicator;
    private volatile Pointer menu;

    private volatile Pointer connectionStatusItem;

    // need to hang on to these to prevent gc
    private final List<Pointer> widgets = new ArrayList<Pointer>(4);


    public AppIndicatorTray() {
    }

    @Override
    public void createTray(String iconName) {
        libgtk.gdk_threads_enter();
        this.appIndicator =
                        libappindicator.app_indicator_new(this.appName, "indicator-messages-new", AppIndicator.CATEGORY_APPLICATION_STATUS);

        /*
         * basically a hack -- we should subclass the AppIndicator type and override the fallback entry in the 'vtable', instead we just
         * hack the app indicator class itself. Not an issue unless we need other appindicators.
         */
        AppIndicator.AppIndicatorClassStruct aiclass =
                        new AppIndicator.AppIndicatorClassStruct(this.appIndicator.parent.g_type_instance.g_class);


        aiclass.fallback = new AppIndicator.Fallback() {
            @Override
            public Pointer callback(final AppIndicator.AppIndicatorInstanceStruct self) {
                AppIndicatorTray.this.callbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        logger.warn("Failed to create appindicator system tray.");

                        if (AppIndicatorTray.this.failureCallback != null) {
                            AppIndicatorTray.this.failureCallback.createTrayFailed();
                        }
                    }
                });
                return null;
            }
        };
        aiclass.write();

        this.menu = libgtk.gtk_menu_new();
        libappindicator.app_indicator_set_menu(this.appIndicator, this.menu);

        libappindicator.app_indicator_set_icon_full(this.appIndicator, iconPath(iconName), this.appName);
        libappindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_ACTIVE);

        libgtk.gdk_threads_leave();

        this.active = true;
    }

    @Override
    public void removeTray() {
        libgtk.gdk_threads_enter();
        for (Pointer widget : this.widgets) {
            libgtk.gtk_widget_destroy(widget);
        }

        // this hides the indicator
        libappindicator.app_indicator_set_status(this.appIndicator, AppIndicator.STATUS_PASSIVE);
        this.appIndicator.write();
        Pointer p = this.appIndicator.getPointer();
        libgobject.g_object_unref(p);

        this.active = false;

        // GC it
        this.appIndicator = null;
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

    @Override
    public void setStatus(String infoString, String iconName) {
        libgtk.gdk_threads_enter();
        if (this.connectionStatusItem == null) {
            this.connectionStatusItem = libgtk.gtk_menu_item_new_with_label(infoString);
            this.widgets.add(this.connectionStatusItem);
            libgtk.gtk_widget_set_sensitive(this.connectionStatusItem, Gtk.FALSE);
            libgtk.gtk_menu_shell_append(this.menu, this.connectionStatusItem);
        } else {
            libgtk.gtk_menu_item_set_label(this.connectionStatusItem, infoString);
        }

        libgtk.gtk_widget_show_all(this.connectionStatusItem);

        libappindicator.app_indicator_set_icon_full(this.appIndicator, iconPath(iconName), this.appName);
        libgtk.gdk_threads_leave();
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    @Override
    public void addMenuEntry(String menuText, final SystemTrayMenuAction callback) {
        synchronized (this.menuEntries) {
            MenuEntry menuEntry = this.menuEntries.get(menuText);

            if (menuEntry == null) {
                libgtk.gdk_threads_enter();

                Pointer dashboardItem = libgtk.gtk_menu_item_new_with_label(menuText);

                // have to watch out! These can get garbage collected!
                Gobject.GCallback gtkCallback = new Gobject.GCallback() {
                    @Override
                    public void callback(Pointer instance, Pointer data) {
                        AppIndicatorTray.this.callbackExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                callback.onClick(AppIndicatorTray.this);
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
            } else {
                updateMenuEntry(menuText, menuText, callback);
            }
        }
    }

    /**
     * Will update an already existing menu entry (or add a new one, if it doesn't exist)
     */
    @Override
    public void updateMenuEntry(String origMenuText, String newMenuText, final SystemTrayMenuAction newCallback) {
        synchronized (this.menuEntries) {
            MenuEntry menuEntry = this.menuEntries.get(origMenuText);

            if (menuEntry != null) {
                libgtk.gdk_threads_enter();
                libgtk.gtk_menu_item_set_label(menuEntry.dashboardItem, newMenuText);

                // have to watch out! These can get garbage collected!
                menuEntry.gtkCallback = new Gobject.GCallback() {
                    @Override
                    public void callback(Pointer instance, Pointer data) {
                        AppIndicatorTray.this.callbackExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                newCallback.onClick(AppIndicatorTray.this);
                            }
                        });
                    }
                };

                libgobject.g_signal_connect_data(menuEntry.dashboardItem, "activate", menuEntry.gtkCallback, null, null, 0);

                libgtk.gtk_widget_show_all(menuEntry.dashboardItem);
                libgtk.gdk_threads_leave();
            } else {
                addMenuEntry(origMenuText, newCallback);
            }
        }
    }
}
