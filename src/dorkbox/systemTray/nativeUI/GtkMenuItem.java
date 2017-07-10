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

import com.sun.jna.Pointer;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.linux.GCallback;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.jna.linux.GtkEventDispatch;
import dorkbox.systemTray.peer.MenuItemPeer;

class GtkMenuItem extends GtkBaseMenuItem implements MenuItemPeer, GCallback {
    private final GtkMenu parent;

    // these have to be volatile, because they can be changed from any thread
    private volatile MenuItem menuItemForActionCallback;
    private volatile Pointer image;

    // The mnemonic will ONLY show-up once a menu entry is selected. IT WILL NOT show up before then!
    // AppIndicators will only show if you use the keyboard to navigate
    // GtkStatusIconTray will show on mouse+keyboard movement
    private volatile char mnemonicKey = 0;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuItem(final GtkMenu parent) {
        super(Gtk.gtk_image_menu_item_new_with_mnemonic(""));

        this.parent = parent;
        Gobject.g_signal_connect_object(_native, "activate", this, null, 0);
    }


    // called by native code, always on the GTK event dispatch thread
    @Override
    public
    int callback(final Pointer instance, final Pointer data) {
        if (menuItemForActionCallback != null) {
            final ActionListener cb = menuItemForActionCallback.getCallback();
            if (cb != null) {
                try {
                    GtkEventDispatch.proxyClick(menuItemForActionCallback, cb);
                } catch (Exception e) {
                    SystemTray.logger.error("Error calling menu entry {} click event.", menuItemForActionCallback.getText(), e);
                }
            }
        }

        return Gtk.TRUE;
    }

    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
    @SuppressWarnings("Duplicates")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        setLegitImage(menuItem.getImage() != null);

        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (image != null) {
                    Gtk.gtk_container_remove(_native, image);  // will automatically get destroyed if no other references to it
                    image = null;
                    Gtk.gtk_widget_show_all(_native);
                }

                if (menuItem.getImage() != null) {
                    image = Gtk.gtk_image_new_from_file(menuItem.getImage()
                                                                .getAbsolutePath());
                    Gtk.gtk_image_menu_item_set_image(_native, image);

                    //  must always re-set always-show after setting the image
                    Gtk.gtk_image_menu_item_set_always_show_image(_native, true);
                }

                Gtk.gtk_widget_show_all(_native);
            }
        });
    }

    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_widget_set_sensitive(_native, menuItem.getEnabled());
            }
        });
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void setText(final MenuItem menuItem) {
        final String textWithMnemonic;

        if (mnemonicKey != 0) {
            String text = menuItem.getText();

            if (text != null) {
                // they are CASE INSENSITIVE!
                int i = text.toLowerCase()
                            .indexOf(mnemonicKey);

                if (i >= 0) {
                    textWithMnemonic = text.substring(0, i) + "_" + text.substring(i);
                }
                else {
                    textWithMnemonic = menuItem.getText();
                }
            } else {
                textWithMnemonic = null;
            }
        }
        else {
            textWithMnemonic = menuItem.getText();
        }

        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_menu_item_set_label(_native, textWithMnemonic);
                Gtk.gtk_widget_show_all(_native);
            }
        });
    }

    @Override
    public
    void setCallback(final MenuItem menuItem) {
        this.menuItemForActionCallback = menuItem;
    }

    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        this.mnemonicKey = Character.toLowerCase(menuItem.getShortcut());

        setText(menuItem);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_container_remove(parent._nativeMenu, _native); // will automatically get destroyed if no other references to it

                GtkMenuItem.super.remove();

                menuItemForActionCallback = null;
                if (image != null) {
                    Gtk.gtk_container_remove(_native, image); // will automatically get destroyed if no other references to it
                    image = null;
                }

                parent.remove(GtkMenuItem.this);
            }
        });
    }
}
