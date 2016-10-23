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
package dorkbox.systemTray.nativeUI;

import java.io.File;

import com.sun.jna.Pointer;

import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.peer.EntryPeer;
import dorkbox.systemTray.util.ImageUtils;

abstract
class GtkBaseMenuItem implements EntryPeer {
    private static File transparentIcon = null;
    // these are necessary BECAUSE GTK menus look funky as hell when there are some menu entries WITH icons and some WITHOUT
    private volatile boolean hasLegitImage = true;

    // these have to be volatile, because they can be changed from any thread
    private volatile Pointer spacerImage;

    GtkBaseMenuItem() {
        // cannot be done in a static initializer, because the tray icon size might not yet have been determined
        if (transparentIcon == null) {
            transparentIcon = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE);
        }
    }

    public
    boolean hasImage() {
        return hasLegitImage;
    }

    void setLegitImage(boolean isLegit) {
        hasLegitImage = isLegit;
    }

    /**
     * the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images.
     * This is primarily only with AppIndicators, although not always.
     * <p>
     * called on the DISPATCH thread
     */
    public
    void setSpacerImage(final Pointer _native, final boolean everyoneElseHasImages) {
        if (hasLegitImage) {
            // we have a legit icon, so there is nothing else we can do.
            return;
        }

        if (spacerImage != null) {
            Gtk.gtk_widget_destroy(spacerImage);
            spacerImage = null;
            Gtk.gtk_widget_show_all(_native);
        }

        if (everyoneElseHasImages) {
            spacerImage = Gtk.gtk_image_new_from_file(transparentIcon.getAbsolutePath());
            Gtk.gtk_image_menu_item_set_image(_native, spacerImage);

            //  must always re-set always-show after setting the image
            Gtk.gtk_image_menu_item_set_always_show_image(_native, true);
        }

        Gtk.gtk_widget_show_all(_native);
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    abstract void onDeleteMenu(final Pointer parentNative);
    abstract void onCreateMenu(final Pointer parentNative, final boolean hasImagesInMenu);

    // always on dispatch
    void onDeleteMenu(final Pointer parentNative, final Pointer _native) {
        Gobject.g_object_force_floating(_native);  // makes it a floating reference
        Gtk.gtk_container_remove(parentNative, _native);
    }

    // always on dispatch
    void onCreateMenu(final Pointer parentNative, final Pointer _native, final boolean hasImagesInMenu) {
        setSpacerImage(_native, hasImagesInMenu);

        // will also get:  gsignal.c:2516: signal 'child-added' is invalid for instance '0x7f1df8244080' of type 'GtkMenu'
        Gtk.gtk_menu_shell_append(parentNative, _native);
        Gobject.g_object_ref_sink(_native);  // undoes "floating"
        Gtk.gtk_widget_show_all(_native);    // necessary to guarantee widget is visible
    }

    @Override
    public
    void remove() {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (spacerImage != null) {
                    Gtk.gtk_widget_destroy(spacerImage);
                    spacerImage = null;
                }
            }
        });
    }
}
