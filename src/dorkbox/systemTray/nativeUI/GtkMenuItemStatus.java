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

import com.sun.jna.Pointer;

import dorkbox.systemTray.Status;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.peer.StatusPeer;

// you might wonder WHY this extends MenuEntryItem -- the reason is that an AppIndicator "status" will be offset from everyone else,
// where a GtkStatusIconTray + SwingUI will have everything lined up. (with or without icons).  This is to normalize how it looks
class GtkMenuItemStatus extends GtkBaseMenuItem implements StatusPeer {

    private final GtkMenu parent;
    private final Pointer _native = Gtk.gtk_image_menu_item_new_with_mnemonic("");

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuItemStatus(final GtkMenu parent) {
        super();
        this.parent = parent;

        // need that extra space so it matches windows/mac
        setLegitImage(false);
    }

    @Override
    public
    void setText(final Status menuItem) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // AppIndicator strips out markup text.
                // https://mail.gnome.org/archives/commits-list/2016-March/msg05444.html

                Gtk.gtk_menu_item_set_label(_native, menuItem.getText());
                Gtk.gtk_widget_show_all(_native);

                Gtk.gtk_widget_set_sensitive(_native, false);
            }
        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_container_remove(parent._nativeMenu, _native);
                Gtk.gtk_menu_shell_deactivate(parent._nativeMenu, _native);

                GtkMenuItemStatus.super.remove();

                Gtk.gtk_widget_destroy(_native);

                parent.remove(GtkMenuItemStatus.this);
            }
        });
    }

    @Override
    void onDeleteMenu(final Pointer parentNative) {
        onDeleteMenu(parentNative, _native);
    }

    @Override
    void onCreateMenu(final Pointer parentNative, final boolean hasImagesInMenu) {
        onCreateMenu(parentNative, _native, hasImagesInMenu);
    }
}
