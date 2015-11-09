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

package dorkbox.util.tray.linux;

import com.sun.jna.Pointer;
import dorkbox.util.NamedThreadFactory;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.jna.linux.GtkSupport;
import dorkbox.util.tray.MenuEntry;
import dorkbox.util.tray.SystemTray;
import dorkbox.util.tray.SystemTrayMenuAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract
class GtkTypeSystemTray extends SystemTray {
    protected static final Gobject libgobject = Gobject.INSTANCE;
    protected static final Gtk libgtk = Gtk.INSTANCE;

    final static ExecutorService callbackExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("SysTrayExecutor", false));

    protected volatile Pointer menu;

    private volatile Pointer connectionStatusItem;

    // need to hang on to these to prevent gc
    private final List<Pointer> widgets = new ArrayList<Pointer>(4);


    @Override
    public
    void shutdown() {
//        libgtk.gdk_threads_enter();  called by implementation
        for (Pointer widget : this.widgets) {
            libgtk.gtk_widget_destroy(widget);
        }

        // GC it
        this.widgets.clear();

        // unrefs the children too
        libgobject.g_object_unref(this.menu);
        this.menu = null;

        synchronized (this.menuEntries) {
            for (MenuEntry menuEntry : this.menuEntries) {
                menuEntry.remove();
            }
            this.menuEntries.clear();
        }

        this.connectionStatusItem = null;
        GtkSupport.shutdownGui();

        libgtk.gdk_threads_leave();

        callbackExecutor.shutdown();
    }

    @Override
    public
    void setStatus(String infoString) {
        synchronized (this.menuEntries) {
            libgtk.gdk_threads_enter();

            if (this.connectionStatusItem == null && infoString != null && !infoString.isEmpty()) {
                this.connectionStatusItem = libgtk.gtk_menu_item_new_with_label("");

                // evil hacks abound...
                Pointer label = libgtk.gtk_bin_get_child(connectionStatusItem);
                libgtk.gtk_label_set_use_markup(label, Gtk.TRUE);
                Pointer markup = libgobject.g_markup_printf_escaped ("<b>%s</b>", infoString);
                libgtk.gtk_label_set_markup (label, markup);
                libgobject.g_free (markup);


                this.widgets.add(this.connectionStatusItem);

                libgtk.gtk_widget_set_sensitive(this.connectionStatusItem, Gtk.FALSE);
                libgtk.gtk_menu_shell_prepend(this.menu, this.connectionStatusItem);
            }
            else {
                if (infoString == null || infoString.isEmpty()) {
                    libgtk.gtk_menu_shell_deactivate(menu, connectionStatusItem);
                    libgtk.gtk_widget_destroy(connectionStatusItem);
                }
                else {
                    // set bold instead
                    // libgtk.gtk_menu_item_set_label(this.connectionStatusItem, infoString);

                    // evil hacks abound...
                    Pointer label = libgtk.gtk_bin_get_child(connectionStatusItem);
                    libgtk.gtk_label_set_use_markup(label, Gtk.TRUE);
                    Pointer markup = libgobject.g_markup_printf_escaped ("<b>%s</b>", infoString);
                    libgtk.gtk_label_set_markup (label, markup);
                    libgobject.g_free (markup);
                }
            }

            libgtk.gtk_widget_show_all(menu);
            libgtk.gdk_threads_leave();
        }
    }

    @Override
    public
    void addMenuEntry(String menuText, final String imagePath, final SystemTrayMenuAction callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        synchronized (this.menuEntries) {
            MenuEntry menuEntry = getMenuEntry(menuText);

            if (menuEntry != null) {
                throw new IllegalArgumentException("Menu entry already exists for given label '" + menuText + "'");
            }
            else {
                libgtk.gdk_threads_enter();
                menuEntry = new GtkMenuEntry(menu, menuText, imagePath, callback, this);
                libgtk.gdk_threads_leave();

                this.menuEntries.add(menuEntry);
            }
        }
    }
}
