/*
 * Copyright 2015 dorkbox, llc
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
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;

/**
 * Derived from
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public abstract
class GtkTypeSystemTray extends SystemTray {
    static final AtomicInteger MENU_ID_COUNTER = new AtomicInteger();

    private volatile Pointer menu;

    @Override
    protected
    void dispatch(final Runnable runnable) {
        Gtk.dispatch(runnable);
    }

    @Override
    public
    void shutdown() {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                obliterateMenu();

                Gtk.shutdownGui();
            }
        });
    }

    @Override
    public
    String getStatus() {
        synchronized (menuEntries) {
            MenuEntry menuEntry = menuEntries.get(0);
            if (menuEntry instanceof GtkMenuEntryStatus) {
                return menuEntry.getText();
            }
        }

        return null;
    }

    @Override
    public
    void setStatus(final String statusText) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                synchronized (menuEntries) {
                    // status is ALWAYS at 0 index...
                    GtkMenuEntry menuEntry = null;
                    if (!menuEntries.isEmpty()) {
                        menuEntry = (GtkMenuEntry) menuEntries.get(0);
                    }

                    if (menuEntry instanceof GtkMenuEntryStatus) {
                        // always delete...
                        removeMenuEntry(menuEntry);
                    }

                    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                    // To work around this issue, we destroy then recreate the menu every time something is changed.
                    deleteMenu();

                    if (menuEntry == null) {
                        menuEntry = new GtkMenuEntryStatus(statusText, GtkTypeSystemTray.this);
                        // status is ALWAYS at 0 index...
                        menuEntries.add(0, menuEntry);
                    } else if (menuEntry instanceof GtkMenuEntryStatus) {
                        // change the text?
                        if (statusText != null) {
                            menuEntry = new GtkMenuEntryStatus(statusText, GtkTypeSystemTray.this);
                            menuEntries.add(0, menuEntry);
                        }
                    }

                    createMenu();
                }
            }
        });
    }

    @Override
    public
    void addMenuSpacer() {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                synchronized (menuEntries) {
                    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                    // To work around this issue, we destroy then recreate the menu every time something is changed.
                    deleteMenu();

                    GtkMenuEntry menuEntry = new GtkMenuEntrySpacer(GtkTypeSystemTray.this);
                    menuEntries.add(menuEntry);

                    createMenu();
                }
            }
        });
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    /**
     * Deletes the menu, and unreferences everything in it. ALSO recreates ONLY the menu object.
     */
    void deleteMenu() {
        if (menu != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                    Gobject.g_object_force_floating(menuEntry__.menuItem);
                    Gtk.gtk_container_remove(menu, menuEntry__.menuItem);
                }

                Gtk.gtk_widget_destroy(menu);
            }
        }

        // makes a new one
        menu = Gtk.gtk_menu_new();
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    void createMenu() {
        boolean hasImages = false;

        // now add back other menu entries
        synchronized (menuEntries) {
            for (int i = 0; i < menuEntries.size(); i++) {
                MenuEntry menuEntry__ = menuEntries.get(i);
                hasImages |= menuEntry__.hasImage();
            }


            for (int i = 0; i < menuEntries.size(); i++) {
                GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);
                // the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images
                menuEntry__.setSpacerImage(hasImages);

                // will also get:  gsignal.c:2516: signal 'child-added' is invalid for instance '0x7f1df8244080' of type 'GtkMenu'
                Gtk.gtk_menu_shell_append(this.menu, menuEntry__.menuItem);
                Gobject.g_object_ref_sink(menuEntry__.menuItem);
            }

            onMenuAdded(menu);
            Gtk.gtk_widget_show_all(menu);
        }
    }

    /**
     * Called inside the gdk_threads block
     */
    void onMenuAdded(final Pointer menu) {
        // only needed for AppIndicator
    }

    /**
     * Completely obliterates the menu, no possible way to reconstruct it.
     */
    private
    void obliterateMenu() {
        if (menu != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);
                    menuEntry__.removePrivate();
                }
                menuEntries.clear();

                Gtk.gtk_widget_destroy(menu);
            }
        }
    }

    protected
    Pointer getMenu() {
        return menu;
    }

    protected
    void addMenuEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback) {
        // some implementations of appindicator, do NOT like having a menu added, which has no menu items yet.
        // see: https://bugs.launchpad.net/glipper/+bug/1203888

        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(menuText);
                    if (menuEntry == null) {
                        // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                        // To work around this issue, we destroy then recreate the menu every time something is changed.
                        deleteMenu();

                        menuEntry = new GtkMenuEntryItem(menuText, imagePath, callback, GtkTypeSystemTray.this);
                        menuEntries.add(menuEntry);

                        createMenu();
                    }
                }
            }
        });
    }
}
