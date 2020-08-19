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
package dorkbox.systemTray.ui.gtk;

import static dorkbox.jna.linux.Gtk.Gtk2;

import dorkbox.systemTray.Status;
import dorkbox.systemTray.peer.StatusPeer;
import dorkbox.jna.linux.GtkEventDispatch;

// you might wonder WHY this extends MenuEntryItem -- the reason is that an AppIndicator "status" will be offset from everyone else,
// where a GtkStatusIconTray + SwingUI will have everything lined up. (with or without icons).  This is to normalize how it looks
class GtkMenuItemStatus extends GtkBaseMenuItem implements StatusPeer {

    private final GtkMenu parent;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuItemStatus(final GtkMenu parent) {
        super(Gtk2.gtk_image_menu_item_new_with_mnemonic(""));
        this.parent = parent;

        // need that extra space so it matches windows/mac
        setLegitImage(false);
    }

    @Override
    public
    void setText(final Status menuItem) {
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                // AppIndicator strips out markup text.
                // https://mail.gnome.org/archives/commits-list/2016-March/msg05444.html

                Gtk2.gtk_menu_item_set_label(_native, menuItem.getText());
                Gtk2.gtk_widget_show_all(_native);

                Gtk2.gtk_widget_set_sensitive(_native, false);
            }
        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                GtkMenuItemStatus.super.remove();

                Gtk2.gtk_container_remove(parent._nativeMenu, _native); // will automatically get destroyed if no other references to it

                parent.remove(GtkMenuItemStatus.this);
            }
        });
    }
}
