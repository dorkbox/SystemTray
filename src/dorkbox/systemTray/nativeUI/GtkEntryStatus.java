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

import java.awt.event.ActionListener;

import dorkbox.systemTray.jna.linux.Gtk;

// you might wonder WHY this extends MenuEntryItem -- the reason is that an AppIndicator "status" will be offset from everyone else,
// where a GtkStatusIconTray + SwingUI will have everything lined up. (with or without icons).  This is to normalize how it looks
class GtkEntryStatus extends GtkEntryItem {

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkEntryStatus(final GtkMenu parent, final String text) {
        super(parent, null);
        // need that extra space so it matches windows/mac
        hasLegitIcon = false;
        setText(text);
    }

    // called in the GTK thread
    @Override
    void renderText(final String text) {
        // AppIndicator strips out markup text.
        // https://mail.gnome.org/archives/commits-list/2016-March/msg05444.html

        Gtk.gtk_menu_item_set_label(_native, text);
        Gtk.gtk_widget_show_all(_native);

        Gtk.gtk_widget_set_sensitive(_native, Gtk.FALSE);
    }

    @Override
    public
    void setCallback(final ActionListener callback) {
    }

    @Override
    public
    void setEnabled(final boolean enabled) {
    }
}
