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

import com.sun.jna.Pointer;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.linux.jna.Gtk;
import dorkbox.systemTray.util.ImageUtils;

abstract
class GtkEntry implements MenuEntry {
    private final int id = Menu.MENU_ID_COUNTER.getAndIncrement();

    private final GtkMenu parent;
    final Pointer _native;

    // this have to be volatile, because they can be changed from any thread
    private volatile String text;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkEntry(final GtkMenu parent, final Pointer menuItem) {
        this.parent = parent;
        this._native = menuItem;
    }

    public
    Menu getParent() {
        return parent;
    }

    /**
     * the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images
     *
     * always called on the DISPATCH thread
     */
    abstract
    void setSpacerImage(final boolean everyoneElseHasImages);

    /**
     * must always be called in the GTK thread
     */
    abstract
    void renderText(final String text);

    /**
     * must always be called in the GTK thread
     */
    abstract
    void setImage_(final File imageFile);

    /**
     * must always be called in the GTK thread
     * called when this item is removed. Necessary to cleanup/remove itself
     */
    abstract
    void removePrivate();

    /**
     * Enables, or disables the sub-menu entry.
     */
    @Override
    public
    void setEnabled(final boolean enabled) {
        if (enabled) {
            Gtk.gtk_widget_set_sensitive(_native, Gtk.TRUE);
        } else {
            Gtk.gtk_widget_set_sensitive(_native, Gtk.FALSE);
        }
    }

    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public final
    void setText(final String newText) {
        text = newText;

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                renderText(text);
            }
        });
    }

    @Override
    public
    void setImage(final File imageFile) {
        if (imageFile == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageFile));
        }
    }

    @Override
    public final
    void setImage(final String imagePath) {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath));
        }
    }

    @Override
    public final
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl));
        }
    }

    @Override
    public final
    void setImage(final String cacheName, final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream));
        }
    }

    @Override
    public final
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream));
        }
    }

    // a child will always remove itself from the parent.
    @Override
    public final
    void remove() {
        parent.dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_container_remove(parent._native, _native);
                Gtk.gtk_menu_shell_deactivate(parent._native, _native);

                removePrivate();

                Gtk.gtk_widget_destroy(_native);

                // have to rebuild the menu now...
                parent.deleteMenu();
                parent.createMenu();
            }
        });
    }

    @Override
    public final
    int hashCode() {
        return id;
    }


    @Override
    public final
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

        GtkEntry other = (GtkEntry) obj;
        return this.id == other.id;
    }
}
