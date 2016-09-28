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

import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuStatus;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;

class GtkMenuEntryStatus extends GtkMenuEntry implements MenuStatus {

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuEntryStatus(final String label, final GtkTypeSystemTray parent) {
        super(Gtk.gtk_menu_item_new_with_label(""), parent);
        setText(label);
    }

    @Override
    void setSpacerImage(final boolean everyoneElseHasImages) {
    }

    // called in the GTK thread
    @Override
    void renderText(final String text) {
        // evil hacks abound...
        Pointer label = Gtk.gtk_bin_get_child(menuItem);
        Gtk.gtk_label_set_use_markup(label, Gtk.TRUE);
        Pointer markup = Gobject.g_markup_printf_escaped("<b>%s</b>", text);
        Gtk.gtk_label_set_markup(label, markup);
        Gobject.g_free(markup);

        Gtk.gtk_widget_set_sensitive(menuItem, Gtk.FALSE);
    }

    @Override
    void setImage_(final File imageFile) {
    }

    @Override
    void removePrivate() {
    }

    @Override
    public
    boolean hasImage() {
        return false;
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {

    }
}
