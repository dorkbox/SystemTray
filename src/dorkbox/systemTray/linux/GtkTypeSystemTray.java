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
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.linux.jna.GtkSupport;
import dorkbox.util.NamedThreadFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Derived from
 * Lantern: https://github.com/getlantern/lantern/ Apache 2.0 License Copyright 2010 Brave New Software Project, Inc.
 */
public abstract
class GtkTypeSystemTray extends SystemTray {
    protected static final Gobject gobject = Gobject.INSTANCE;
    protected static final Gtk gtk = Gtk.INSTANCE;

    final static ExecutorService callbackExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SysTrayExecutor", false));

    private volatile Pointer menu;

    private volatile Pointer connectionStatusItem;
    private volatile String statusText = null;


    @Override
    public
    void shutdown() {
        GtkSupport.dispatch(new Runnable() {
            @Override
            public
            void run() {
                obliterateMenu();

                boolean terminated = false;
                try {
                    terminated = callbackExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }

                if (!terminated) {
                    callbackExecutor.shutdownNow();
                }

                GtkSupport.shutdownGui();
            }
        });
    }

    @Override
    public
    String getStatus() {
        return statusText;
    }

    @Override
    public
    void setStatus(final String statusText) {
        this.statusText = statusText;

        GtkSupport.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                if (connectionStatusItem == null && statusText != null && !statusText.isEmpty()) {
                    deleteMenu();

                    connectionStatusItem = gtk.gtk_menu_item_new_with_label("");

                    // evil hacks abound...
                    Pointer label = gtk.gtk_bin_get_child(connectionStatusItem);
                    gtk.gtk_label_set_use_markup(label, Gtk.TRUE);
                    Pointer markup = gobject.g_markup_printf_escaped("<b>%s</b>", statusText);
                    gtk.gtk_label_set_markup(label, markup);
                    gobject.g_free(markup);

                    gtk.gtk_widget_set_sensitive(connectionStatusItem, Gtk.FALSE);

                    createMenu();
                }
                else {
                    if (statusText == null || statusText.isEmpty()) {
                        // this means the status text already exists, and we are removing it

                        gtk.gtk_container_remove(menu, connectionStatusItem);
                        connectionStatusItem = null; // because we manually delete it

                        gtk.gtk_widget_show_all(menu);

                        deleteMenu();
                        createMenu();
                    }
                    else {
                        // here we set the text only. it already exists

                        // set bold instead
                        // libgtk.gtk_menu_item_set_label(this.connectionStatusItem, statusText);

                        // evil hacks abound...
                        Pointer label = gtk.gtk_bin_get_child(connectionStatusItem);
                        gtk.gtk_label_set_use_markup(label, Gtk.TRUE);
                        Pointer markup = gobject.g_markup_printf_escaped("<b>%s</b>", statusText);
                        gtk.gtk_label_set_markup(label, markup);
                        gobject.g_free(markup);

                        gtk.gtk_widget_show_all(menu);
                    }
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
            // have to remove status from menu (but not destroy the object)
            if (connectionStatusItem != null) {
                gobject.g_object_force_floating(connectionStatusItem);
                gtk.gtk_container_remove(menu, connectionStatusItem);
            }

            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                    gobject.g_object_force_floating(menuEntry__.menuItem);
                    gtk.gtk_container_remove(menu, menuEntry__.menuItem);
                }

                gtk.gtk_widget_destroy(menu);
            }
        }

        // makes a new one
        menu = gtk.gtk_menu_new();
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    void createMenu() {
        // now add status
        if (connectionStatusItem != null) {
            gtk.gtk_menu_shell_append(this.menu, this.connectionStatusItem);
            gobject.g_object_ref_sink(connectionStatusItem);
        }

        // now add back other menu entries
        synchronized (menuEntries) {
            for (int i = 0; i < menuEntries.size(); i++) {
                GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                // will also get:  gsignal.c:2516: signal 'child-added' is invalid for instance '0x7f1df8244080' of type 'GtkMenu'
                gtk.gtk_menu_shell_append(this.menu, menuEntry__.menuItem);
                gobject.g_object_ref_sink(menuEntry__.menuItem);
            }

            onMenuAdded(menu);
            gtk.gtk_widget_show_all(menu);
        }
    }

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
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    GtkMenuEntry menuEntry__ = (GtkMenuEntry) menuEntries.get(i);

                    menuEntry__.removePrivate();
                }
                menuEntries.clear();

                gtk.gtk_widget_destroy(menu);
            }
        }
    }

    /**
     * Called inside the gdk_threads block
     */
    protected
    void onMenuAdded(final Pointer menu) {};

    protected
    Pointer getMenu() {
        return menu;
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
                        // To work around this issue, we destroy then recreate the menu every time something is changed.
                        deleteMenu();

                        menuEntry = new GtkMenuEntry(menuText, imagePath, callback, GtkTypeSystemTray.this);
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
