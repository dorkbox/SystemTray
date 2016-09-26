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
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.GCallback;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.util.ImageUtils;

class GtkMenuEntry implements MenuEntry, GCallback {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    private final int id = ID_COUNTER.getAndIncrement();

    final Pointer menuItem;
    final GtkTypeSystemTray parent;

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final NativeLong nativeLong;

    // these have to be volatile, because they can be changed from any thread
    private volatile String text;
    private volatile SystemTrayMenuAction callback;
    private volatile Pointer image;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuEntry(final String label, final File imagePath, final SystemTrayMenuAction callback, final GtkTypeSystemTray parent) {
        this.parent = parent;
        this.text = label;
        this.callback = callback;

        menuItem = Gtk.gtk_image_menu_item_new_with_label(label);

        if (imagePath != null) {
            // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
            // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
            // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
            image = Gtk.gtk_image_new_from_file(imagePath.getAbsolutePath());

            Gtk.gtk_image_menu_item_set_image(menuItem, image);
            //  must always re-set always-show after setting the image
            Gtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
        }

        nativeLong = Gobject.g_signal_connect_object(menuItem, "activate", this, null, 0);
    }


    // called by native code
    @Override
    public
    int callback(final Pointer instance, final Pointer data) {
        final SystemTrayMenuAction cb = this.callback;
        if (cb != null) {
            try {
                cb.onClick(parent, GtkMenuEntry.this);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        return Gtk.TRUE;
    }

    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                text = newText;
                Gtk.gtk_menu_item_set_label(menuItem, newText);

                Gtk.gtk_widget_show_all(menuItem);
            }
        });
    }

    private
    void setImage_(final File imagePath) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (image != null) {
                    Gtk.gtk_widget_destroy(image);
                    image = null;
                }

                Gtk.gtk_widget_show_all(menuItem);

                if (imagePath != null) {
                    image = Gtk.gtk_image_new_from_file(imagePath.getAbsolutePath());
                    Gtk.gtk_image_menu_item_set_image(menuItem, image);
                    Gobject.g_object_ref_sink(image);

                    //  must always re-set always-show after setting the image
                    Gtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
                }

                Gtk.gtk_widget_show_all(menuItem);
            }
        });
    }

    @Override
    public
    void setImage(final String imagePath) {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.SIZE, imagePath));
        }
    }

    @Override
    public
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.SIZE, imageUrl));
        }
    }

    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.SIZE, cacheName, imageStream));
        }
    }

    @Override
    @Deprecated
    public
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.SIZE, imageStream));
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
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                removePrivate();

                // have to rebuild the menu now...
                parent.deleteMenu();
                parent.createMenu();
            }
        });
    }

    void removePrivate() {
        callback = null;
        Gtk.gtk_menu_shell_deactivate(parent.getMenu(), menuItem);

        if (image != null) {
            Gtk.gtk_widget_destroy(image);
        }

        Gtk.gtk_widget_destroy(menuItem);
    }

    @Override
    public
    int hashCode() {
        return id;
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
        return this.id == other.id;
    }
}
