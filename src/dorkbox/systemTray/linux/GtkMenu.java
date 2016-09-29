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

import com.sun.jna.Pointer;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;

class GtkMenu extends Menu {
    // must ONLY be created at the end of delete!
    volatile Pointer _native;

    // called on dispatch
    GtkMenu(SystemTray systemTray, GtkMenu parent) {
        super(systemTray, parent);
    }


    /**
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    @Override
    protected
    void dispatch(final Runnable runnable) {
        Gtk.dispatch(runnable);
    }

    public
    void shutdown() {
        dispatch(new Runnable() {
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
    void addMenuSpacer() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                synchronized (menuEntries) {
                    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                    // To work around this issue, we destroy then recreate the menu every time something is changed.
                    deleteMenu();

                    GtkMenuEntry menuEntry = new GtkMenuEntrySpacer(GtkMenu.this);
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
        if (_native != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                    Gobject.g_object_force_floating(menuEntry__._native);
                    Gtk.gtk_container_remove(_native, menuEntry__._native);
                }

                Gtk.gtk_widget_destroy(_native);
            }
        }

        // makes a new one
        _native = Gtk.gtk_menu_new();
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
                Gtk.gtk_menu_shell_append(this._native, menuEntry__._native);
                Gobject.g_object_ref_sink(menuEntry__._native);  // undoes "floating"
            }

            onMenuAdded(_native);
            Gtk.gtk_widget_show_all(_native);
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
        if (_native != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);
                    menuEntry__.removePrivate();
                }
                menuEntries.clear();

                Gtk.gtk_widget_destroy(_native);
            }
        }
    }


    /**
     * Will add a new menu entry, or update one if it already exists
     */
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

                        menuEntry = new GtkMenuEntryItem(GtkMenu.this, callback);
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);
                        menuEntries.add(menuEntry);

                        if (menuText.equals("AAAAAAAA")) {
                            Gtk.gtk_widget_set_sensitive(((GtkMenuEntryItem) menuEntry)._native, Gtk.TRUE);
                            GtkMenu subMenu = new GtkMenu(getSystemTray(), GtkMenu.this);
                            subMenu.addMenuEntry("asdasdasd", null, null, null);
                            Gtk.gtk_menu_item_set_submenu(((GtkMenuEntryItem) menuEntry)._native, subMenu._native);
                        }

                        createMenu();
                    }
                }
            }
        });
    }
}
