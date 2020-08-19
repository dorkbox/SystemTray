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

import dorkbox.systemTray.peer.SeparatorPeer;
import dorkbox.jna.linux.GtkEventDispatch;

class GtkMenuItemSeparator extends GtkBaseMenuItem implements SeparatorPeer {

    private final GtkMenu parent;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuItemSeparator(final GtkMenu parent) {
        super(Gtk2.gtk_separator_menu_item_new());
        this.parent = parent;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk2.gtk_container_remove(parent._nativeMenu, _native);  // will automatically get destroyed if no other references to it

                parent.remove(GtkMenuItemSeparator.this);
            }
        });
    }

    @Override
    public
    boolean hasImage() {
        return false;
    }

    @Override
    public
    void setSpacerImage(final boolean everyoneElseHasImages) {
        // no op
    }
}
