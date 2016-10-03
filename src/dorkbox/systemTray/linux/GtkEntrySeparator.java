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

import dorkbox.systemTray.MenuSpacer;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.Gtk;

class GtkEntrySeparator extends GtkEntry implements MenuSpacer {

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkEntrySeparator(final GtkMenu parent) {
        super(parent, Gtk.gtk_separator_menu_item_new());
    }

    @Override
    void setSpacerImage(final boolean everyoneElseHasImages) {
    }

    // called in the GTK thread
    @Override
    void renderText(final String text) {
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

    @Override
    public
    void setEnabled(final boolean enabled) {
    }
}
