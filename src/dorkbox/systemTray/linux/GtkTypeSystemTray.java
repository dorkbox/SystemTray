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

import com.sun.jna.Pointer;
import dorkbox.systemTray.ImageUtil;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.NamedThreadFactory;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.GtkSupport;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Derived from
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public abstract
class GtkTypeSystemTray extends SystemTray {
    protected static final Gobject gobject = Gobject.INSTANCE;
    protected static final Gtk gtk = Gtk.INSTANCE;

    final static ExecutorService callbackExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SysTrayExecutor", false));

    private Pointer menu;
    private Pointer connectionStatusItem;

    @Override
    public
    void shutdown() {
        GtkSupport.dispatch(new Runnable() {
            @Override
            public
            void run() {
                obliterateMenu();
                GtkSupport.shutdownGui();

                callbackExecutor.shutdown();
            }
        });
    }

    @Override
    public
    void setStatus(final String infoString) {
        GtkSupport.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (connectionStatusItem == null && infoString != null && !infoString.isEmpty()) {
                    deleteMenu();

                    connectionStatusItem = gtk.gtk_menu_item_new_with_label("");
                    gobject.g_object_ref(connectionStatusItem); // so it matches with 'createMenu'

                    // evil hacks abound...
                    Pointer label = gtk.gtk_bin_get_child(connectionStatusItem);
                    gtk.gtk_label_set_use_markup(label, Gtk.TRUE);
                    Pointer markup = gobject.g_markup_printf_escaped("<b>%s</b>", infoString);
                    gtk.gtk_label_set_markup(label, markup);
                    gobject.g_free(markup);


                    gtk.gtk_widget_set_sensitive(connectionStatusItem, Gtk.FALSE);

                    createMenu();
                }
                else {
                    if (infoString == null || infoString.isEmpty()) {
                        deleteMenu();
                        gtk.gtk_widget_destroy(connectionStatusItem);
                        connectionStatusItem = null;

                        createMenu();
                    }
                    else {
                        // set bold instead
                        // libgtk.gtk_menu_item_set_label(this.connectionStatusItem, infoString);

                        // evil hacks abound...
                        Pointer label = gtk.gtk_bin_get_child(connectionStatusItem);
                        gtk.gtk_label_set_use_markup(label, Gtk.TRUE);
                        Pointer markup = gobject.g_markup_printf_escaped("<b>%s</b>", infoString);
                        gtk.gtk_label_set_markup(label, markup);
                        gobject.g_free(markup);

                        gtk.gtk_widget_show_all(menu);
                    }
                }
            }
        });
    }

    /**
     * Called inside the gdk_threads block
     */
    protected abstract
    void onMenuAdded(final Pointer menu);


    /**
     * Completely obliterates the menu, no possible way to reconstruct it.
     */
    private
    void obliterateMenu() {
        if (menu != null) {
            // have to remove status from menu
            if (connectionStatusItem != null) {
                gtk.gtk_widget_destroy(connectionStatusItem);
                connectionStatusItem = null;
            }

            // have to remove all other menu entries
            for (int i = 0; i < menuEntries.size(); i++) {
                GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                menuEntry__.removePrivate();
            }
            menuEntries.clear();

            // GTK menu needs a "ref_sink"
            gobject.g_object_ref_sink(menu);
        }
    }

    /**
     * Deletes the menu, and unreferences everything in it. ALSO recreates ONLY the menu object.
     */
    void deleteMenu() {
        if (menu != null) {
            // have to remove status from menu
            if (connectionStatusItem != null) {
                gobject.g_object_ref(connectionStatusItem);

                gtk.gtk_container_remove(menu, connectionStatusItem);
            }

            // have to remove all other menu entries
            for (int i = 0; i < menuEntries.size(); i++) {
                GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                gobject.g_object_ref(menuEntry__.menuItem);
                gtk.gtk_container_remove(menu, menuEntry__.menuItem);
            }

            // GTK menu needs a "ref_sink"
            gobject.g_object_ref_sink(menu);
        }

        menu = gtk.gtk_menu_new();
    }

    // UNSAFE. must be protected inside dispatch
    void createMenu() {
        // now add status
        if (connectionStatusItem != null) {
            gtk.gtk_menu_shell_append(this.menu, this.connectionStatusItem);
            gobject.g_object_unref(connectionStatusItem);
        }

        // now add back other menu entries
        for (int i = 0; i < menuEntries.size(); i++) {
            GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

            // will also get:  gsignal.c:2516: signal 'child-added' is invalid for instance '0x7f1df8244080' of type 'GtkMenu'
            gtk.gtk_menu_shell_append(this.menu, menuEntry__.menuItem);
            gobject.g_object_unref(menuEntry__.menuItem);
        }

        onMenuAdded(menu);
        gtk.gtk_widget_show_all(menu);
    }

    private
    void addMenuEntry_(final String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        // some implementations of appindicator, do NOT like having a menu added, which has no menu items yet.
        // see: https://bugs.launchpad.net/glipper/+bug/1203888

        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        GtkSupport.dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    GtkMenuEntry menuEntry = (GtkMenuEntry) getMenuEntry(menuText);

                    if (menuEntry == null) {
                        // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                        // To work around this issue, we destroy then recreate the menu every time one is added.
                        deleteMenu();

                        menuEntry = new GtkMenuEntry(menu, menuText, imagePath, callback, GtkTypeSystemTray.this);

                        gobject.g_object_ref(menuEntry.menuItem); // so it matches with 'createMenu'
                        menuEntries.add(menuEntry);

                        createMenu();
                    }
                }
            }
        });
    }

    @Override
    public
    void addMenuEntry(String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        if (imagePath == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(imagePath), callback);
        }
    }

    @Override
    public
    void addMenuEntry(final String menuText, final URL imageUrl, final SystemTrayMenuAction callback) {
        if (imageUrl == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(imageUrl), callback);
        }
    }

    @Override
    public
    void addMenuEntry(final String menuText, final String cacheName, final InputStream imageStream, final SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPath(cacheName, imageStream), callback);
        }
    }

    @Override
    @Deprecated
    public
    void addMenuEntry(final String menuText, final InputStream imageStream, final SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtil.iconPathNoCache(imageStream), callback);
        }
    }
}
