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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.linux.GCallback;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.peer.CheckboxPeer;

// ElementaryOS shows the checkbox on the right, everyone else is on the left. With eOS, we CANNOT show the spacer image. It does not work
class GtkMenuItemCheckbox extends GtkBaseMenuItem implements CheckboxPeer, GCallback {
    private final GtkMenu parent;

    // these have to be volatile, because they can be changed from any thread
    private volatile ActionListener callback;
    private volatile boolean isChecked = false;
    private volatile Pointer image;

    // The mnemonic will ONLY show-up once a menu entry is selected. IT WILL NOT show up before then!
    // AppIndicators will only show if you use the keyboard to navigate
    // GtkStatusIconTray will show on mouse+keyboard movement
    private volatile char mnemonicKey = 0;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     */
    GtkMenuItemCheckbox(final GtkMenu parent) {
        super(Gtk.gtk_check_menu_item_new_with_mnemonic(""));
        this.parent = parent;

        Gobject.g_signal_connect_object(_native, "activate", this, null, 0);
    }

    // called by native code ONLY
    @Override
    public
    int callback(final Pointer instance, final Pointer data) {
        if (callback != null) {
            // this will redispatch to our created callback via `setCallback`
            Gtk.proxyClick(null, callback);
        }

        return Gtk.TRUE;
    }

    public
    boolean hasImage() {
        return true;
    }

    public
    void setSpacerImage(final boolean everyoneElseHasImages) {
        // no op
    }

    @Override
    public
    void setEnabled(final Checkbox menuItem) {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_widget_set_sensitive(_native, menuItem.getEnabled());
            }
        });
    }

    @Override
    public
    void setText(final Checkbox menuItem) {
        final String textWithMnemonic;

        if (mnemonicKey != 0) {
            String text = menuItem.getText();

            // they are CASE INSENSITIVE!
            int i = text.toLowerCase()
                        .indexOf(mnemonicKey);

            if (i >= 0) {
                textWithMnemonic = text.substring(0, i) + "_" + text.substring(i);
            }
            else {
                textWithMnemonic = menuItem.getText();
            }
        }
        else {
            textWithMnemonic = menuItem.getText();
        }

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_menu_item_set_label(_native, textWithMnemonic);
                Gtk.gtk_widget_show_all(_native);
            }
        });
    }

    AtomicBoolean triggeredSetChecked = new AtomicBoolean(false);



    @SuppressWarnings({"Duplicates", "StatementWithEmptyBody"})
    @Override
    public
    void setCallback(final Checkbox menuItem) {
        callback = menuItem.getCallback();  // can be set to null

        if (callback != null) {
            callback = new ActionListener() {
                @Override
                public
                void actionPerformed(ActionEvent e) {
                    if (triggeredSetChecked.getAndSet(false)) {
                        // note: changing the state of the checkbox will trigger "activate", which will then trigger the callback. We don't want that.
                        // we assume this is consistent across ALL versions and variants of GTK
                        // https://github.com/GNOME/gtk/blob/master/gtk/gtkcheckmenuitem.c#L317
                        return;
                    }

                    // this will run on the EDT, since we are calling it from the EDT
                    menuItem.setChecked(!isChecked);

                    // we want it to run on the EDT, but with our own action event info (so it is consistent across all platforms)
                    ActionListener cb = menuItem.getCallback();
                    if (cb != null) {
                        try {
                            cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                        } catch (Throwable throwable) {
                            SystemTray.logger.error("Error calling menu entry {} click event.", menuItem.getText(), throwable);
                        }
                    }
                }
            };

        }
    }

    @Override
    public
    void setChecked(final Checkbox menuItem) {
        boolean checked = menuItem.getChecked();

        // only dispatch if it's actually different
        if (checked != this.isChecked) {
            this.isChecked = checked;

            Gtk.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    // note: this will trigger "activate", which will then trigger the callback.  We don't want to do that, so we hack around it.
                    // BECAUSE we are on the GTK thread, the "set_active" call is QUEUED... so we check the callback state AFTER this.
                    // we assume this is consistent across ALL versions and variants of GTK
                    // https://github.com/GNOME/gtk/blob/master/gtk/gtkcheckmenuitem.c#L317
                    Gtk.gtk_check_menu_item_set_active(_native, isChecked);


                    if (callback != null) {
                        // only need to worry about this if we have a callback specified.
                        // This is because of how GTK handles setting the checkbox state + callbacks
                        triggeredSetChecked.set(true);
                    }
                }
            });
        }
    }

    @Override
    public
    void setShortcut(final Checkbox checkbox) {
        this.mnemonicKey = Character.toLowerCase(checkbox.getShortcut());

        setText(checkbox);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_container_remove(parent._nativeMenu, _native);  // will automatically get destroyed if no other references to it

                GtkMenuItemCheckbox.super.remove();

                if (image != null) {
                    Gtk.gtk_container_remove(_native, image); // will automatically get destroyed if no other references to it
                    image = null;
                }

                parent.remove(GtkMenuItemCheckbox.this);
            }
        });
    }
}
