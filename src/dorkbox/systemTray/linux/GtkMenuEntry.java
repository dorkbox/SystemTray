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
import dorkbox.systemTray.ImageUtil;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.util.jna.linux.Gobject;
import dorkbox.util.jna.linux.Gobject.GCallback;
import dorkbox.util.jna.linux.Gtk;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class GtkMenuEntry implements MenuEntry {
    private static final Gtk gtk = Gtk.INSTANCE;
    private static final Gobject gobject = Gobject.INSTANCE;

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

        menuItem = gtk.gtk_image_menu_item_new_with_label(label);

        if (imagePath != null && !imagePath.isEmpty()) {
            // NOTE: XFCE uses appindicator3, which DOES NOT support images in the menu. This change was reverted.
            // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
            // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
            image = gtk.gtk_image_new_from_file(imagePath);

            gtk.gtk_image_menu_item_set_image(menuItem, image);
            //  must always re-set always-show after setting the image
            gtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
        }

        nativeLong = gobject.g_signal_connect_data(menuItem, "activate", gtkCallback, null, null, 0);
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
        gtk.gdk_threads_enter();

        gtk.gtk_menu_item_set_label(menuItem, newText);

        gtk.gtk_widget_show_all(parentMenu);

        gtk.gdk_threads_leave();
    }

    private
    void setImage_(final String imagePath) {
        gtk.gdk_threads_enter();

        if (imagePath != null && !imagePath.isEmpty()) {
            if (image != null) {
                gtk.gtk_widget_destroy(image);
            }
            gtk.gtk_widget_show_all(parentMenu);

            image = gtk.gtk_image_new_from_file(imagePath);

            gtk.gtk_image_menu_item_set_image(menuItem, image);

            //  must always re-set always-show after setting the image
            gtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
        }

        gtk.gdk_threads_leave();
        gtk.gtk_widget_show_all(parentMenu);
    }

    @Override
    public
    void setImage(final String imagePath) throws IOException {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imagePath));
        }
    }

    @Override
    public
    void setImage(final URL imageUrl) throws IOException {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(imageUrl));
        }
    }

    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) throws IOException {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPath(cacheName, imageStream));
        }
    }

    @Override
    @Deprecated
    public
    void setImage(final InputStream imageStream) throws IOException {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtil.iconPathNoCache(imageStream));
        }
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
        gtk.gdk_threads_enter();

        removePrivate();

        // have to rebuild the menu now...
        systemTray.deleteMenu();
        systemTray.createMenu();

        gtk.gdk_threads_leave();
    }

    void removePrivate() {
        gobject.g_signal_handler_disconnect(menuItem, nativeLong);
        gtk.gtk_menu_shell_deactivate(parentMenu, menuItem);

        if (image != null) {
            gtk.gtk_widget_destroy(image);
        }
        gtk.gtk_widget_destroy(menuItem);
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
