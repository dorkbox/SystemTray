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

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.GCallback;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.util.ImageUtils;

class GtkMenuEntryItem extends GtkMenuEntry implements GCallback {
    private static File transparentIcon = null;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final NativeLong nativeLong;

    // these have to be volatile, because they can be changed from any thread
    private volatile SystemTrayMenuAction callback;
    private volatile Pointer image;

    // these are necessary BECAUSE GTK menus look funky as hell when there are some menu entries WITH icons and some WITHOUT
    private volatile boolean hasLegitIcon = true;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuEntryItem(final String label, final File image, final SystemTrayMenuAction callback, final GtkTypeSystemTray systemTray) {
        super(Gtk.gtk_image_menu_item_new_with_label(""), systemTray);
        this.callback = callback;
        setText(label);

        if (transparentIcon == null) {
            transparentIcon = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE);
        }

        setImage_(image);

        if (callback != null) {
            nativeLong = Gobject.g_signal_connect_object(menuItem, "activate", this, null, 0);
        }
        else {
            nativeLong = null;
        }
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        this.callback = callback;
    }

    // called by native code
    @Override
    public
    int callback(final Pointer instance, final Pointer data) {
        final SystemTrayMenuAction cb = this.callback;
        if (cb != null) {
            Gtk.proxyClick(cb, systemTray, GtkMenuEntryItem.this);
        }

        return Gtk.TRUE;
    }

    @Override
    public
    boolean hasImage() {
        return hasLegitIcon;
    }

    /**
     * the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images.
     * This is primarily only with AppIndicators, although not always.
     * <p>
     * called on the DISPATCH thread
     */
    void setSpacerImage(final boolean everyoneElseHasImages) {
        if (hasLegitIcon) {
            // we have a legit icon, so there is nothing else we can do.
            return;
        }

        if (image != null) {
            Gtk.gtk_widget_destroy(image);
            image = null;
            Gtk.gtk_widget_show_all(menuItem);
        }

        if (everyoneElseHasImages) {
            image = Gtk.gtk_image_new_from_file(transparentIcon.getAbsolutePath());
            Gtk.gtk_image_menu_item_set_image(menuItem, image);

            //  must always re-set always-show after setting the image
            Gtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
        }

        Gtk.gtk_widget_show_all(menuItem);
    }

    /**
     * must always be called in the GTK thread
     */
    void renderText(final String text) {
        Gtk.gtk_menu_item_set_label(menuItem, text);
        Gtk.gtk_widget_show_all(menuItem);
    }

    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
    void setImage_(final File imageFile) {
        hasLegitIcon = imageFile != null;

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (image != null) {
                    Gtk.gtk_widget_destroy(image);
                    image = null;
                    Gtk.gtk_widget_show_all(menuItem);
                }

                if (imageFile != null) {
                    image = Gtk.gtk_image_new_from_file(imageFile.getAbsolutePath());
                    Gtk.gtk_image_menu_item_set_image(menuItem, image);

                    //  must always re-set always-show after setting the image
                    Gtk.gtk_image_menu_item_set_always_show_image(menuItem, Gtk.TRUE);
                }

                Gtk.gtk_widget_show_all(menuItem);
            }
        });
    }

    void removePrivate() {
        callback = null;

        if (image != null) {
            Gtk.gtk_widget_destroy(image);
            image = null;
        }
    }
}
