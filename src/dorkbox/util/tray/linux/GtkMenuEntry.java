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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gobject.GCallback;
import dorkbox.util.jna.linux.Gtk;
import dorkbox.util.tray.MenuEntry;
import dorkbox.util.tray.SystemTrayMenuAction;

class GtkMenuEntry implements MenuEntry {
    private static final Gtk libgtk = Gtk.INSTANCE;
    private static final Gobject libgobject = Gobject.INSTANCE;

    private final GCallback gtkCallback;
    final Pointer menuItem;
    private final Pointer parentMenu;
    final GtkTypeSystemTray systemTray;
    private final NativeLong nativeLong;

    // these have to be volatile, because they can be changed from any thread
    private volatile String text;
    private volatile SystemTrayMenuAction callback;
    private volatile Pointer image;

    GtkMenuEntry(final Pointer parentMenu, final String label, final String imagePath, final SystemTrayMenuAction callback,
                 final GtkTypeSystemTray systemTray) {
        this.parentMenu = parentMenu;
        this.text = label;
        this.callback = callback;
        this.systemTray = systemTray;

        // have to watch out! These can get garbage collected!
        gtkCallback = new Gobject.GCallback() {
            @Override
            public
            int callback(Pointer instance, Pointer data) {
                handle();
                return Gtk.TRUE;
            }
        };

        menuItem = libgtk.gtk_image_menu_item_new_with_label(label);

        if (imagePath != null && !imagePath.isEmpty()) {
            // NOTE: XFCE uses appindicator3, which DOES NOT support images in the menu. This change was reverted.
            // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
            // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
            image = libgtk.gtk_image_new_from_file(imagePath);

            libgtk.gtk_image_menu_item_set_image(menuItem, image);
            //  must always re-set always-show after setting the image
            libgtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
        }

        nativeLong = libgobject.g_signal_connect_data(menuItem, "activate", gtkCallback, null, null, 0);
    }

    private
    void handle() {
        final SystemTrayMenuAction cb = this.callback;
        if (cb != null) {
            GtkTypeSystemTray.callbackExecutor.execute(new Runnable() {
                @Override
                public
                void run() {
                    cb.onClick(systemTray, GtkMenuEntry.this);
                }
            });
        }
    }

    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        this.text = newText;
        libgtk.gdk_threads_enter();

        libgtk.gtk_menu_item_set_label(menuItem, newText);

        libgtk.gtk_widget_show_all(parentMenu);

        libgtk.gdk_threads_leave();
    }

    @Override
    public
    void setImage(final String imagePath) {
        libgtk.gdk_threads_enter();

        if (imagePath != null && !imagePath.isEmpty()) {
            if (image != null) {
                libgtk.gtk_widget_destroy(image);
            }
            libgtk.gtk_widget_show_all(parentMenu);
            libgtk.gdk_threads_leave();

            libgtk.gdk_threads_enter();
            image = libgtk.gtk_image_new_from_file(imagePath);

            libgtk.gtk_image_menu_item_set_image(menuItem, image);

            //  must always re-set always-show after setting the image
            libgtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
        }
        libgtk.gtk_widget_show_all(parentMenu);

        libgtk.gdk_threads_leave();
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        this.callback = callback;
    }

    /**
     * This is ONLY called via systray.menuEntry.remove() !!
     */
    public
    void remove() {
        libgtk.gdk_threads_enter();

        removePrivate();

        // have to rebuild the menu now...
        systemTray.deleteMenu();
        systemTray.createMenu();

        libgtk.gdk_threads_leave();
    }

    void removePrivate() {
        libgobject.g_signal_handler_disconnect(menuItem, nativeLong);
        libgtk.gtk_menu_shell_deactivate(parentMenu, menuItem);

        if (image != null) {
            libgtk.gtk_widget_destroy(image);
        }
        libgtk.gtk_widget_destroy(menuItem);
    }

    @Override
    public
    int hashCode() {
        return 0;
    }


    @Override
    public
    boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GtkMenuEntry other = (GtkMenuEntry) obj;
        return this.text.equals(other.text);
    }
}
