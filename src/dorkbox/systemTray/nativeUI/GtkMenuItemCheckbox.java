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

import com.sun.jna.Pointer;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.jna.linux.GCallback;
import dorkbox.systemTray.jna.linux.Gobject;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.systemTray.util.ImageUtils;

// ElementaryOS shows the checkbox on the right, everyone else is on the left. With eOS, we CANNOT show the spacer image. It does not work
class GtkMenuItemCheckbox extends GtkBaseMenuItem implements CheckboxPeer, GCallback {
    private static String checkedFile;
    private static String uncheckedFile;

    private final GtkMenu parent;

    // these have to be volatile, because they can be changed from any thread
    private volatile ActionListener callback;
    private volatile boolean isChecked = false;
    private volatile Pointer checkedImage;
    private volatile Pointer image;

    // The mnemonic will ONLY show-up once a menu entry is selected. IT WILL NOT show up before then!
    // AppIndicators will only show if you use the keyboard to navigate
    // GtkStatusIconTray will show on mouse+keyboard movement
    private volatile char mnemonicKey = 0;
    private final long handlerId;
    private final boolean isAppIndicator;

    /**
     * called from inside dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     *
     * note: AppIndicator tray's DO NOT show the spacer image for checkboxes so they are "shifted left", which looks awkward.
     */
    GtkMenuItemCheckbox(final GtkMenu parent, final boolean isAppIndicator) {
        super(isAppIndicator ? Gtk.gtk_image_menu_item_new_with_mnemonic("") : Gtk.gtk_check_menu_item_new_with_mnemonic(""));
        this.parent = parent;
        this.isAppIndicator = isAppIndicator;

        handlerId = Gobject.g_signal_connect_object(_native, "activate", this, null, 0);

        if (checkedFile == null) {
            // from Brankic1979, public domain
            checkedFile = ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, ImageUtils.class.getResource("checked_32.png")).getAbsolutePath();
            uncheckedFile = ImageUtils.getTransparentImage(ImageUtils.ENTRY_SIZE).getAbsolutePath();
        }

        if (isAppIndicator) {
            setCheckedIconForAppIndicators();
        }
        else {
            Gobject.g_signal_handler_block(_native, handlerId);
            Gtk.gtk_check_menu_item_set_active(_native, false);
            Gobject.g_signal_handler_unblock(_native, handlerId);
        }
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
                    // this will run on the EDT, since we are calling it from the EDT. This can ALSO recursively call the callback
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
        final boolean checked = menuItem.getChecked();

        // only dispatch if it's actually different
        if (checked != this.isChecked) {
            this.isChecked = checked;

            Gtk.dispatch(new Runnable() {
                @Override
                public
                void run() {
                    if (!isAppIndicator) {
                        // note: this will trigger "activate", which will then trigger the callback.
                        // we assume this is consistent across ALL versions and variants of GTK
                        // https://github.com/GNOME/gtk/blob/master/gtk/gtkcheckmenuitem.c#L317
                        // this disables the signal handler, then enables it
                        Gobject.g_signal_handler_block(_native, handlerId);
                        Gtk.gtk_check_menu_item_set_active(_native, isChecked);
                        Gobject.g_signal_handler_unblock(_native, handlerId);
                    } else {
                        setCheckedIconForAppIndicators();
                    }
                }
            });
        }
    }

    private
    void setCheckedIconForAppIndicators() {
        if (checkedImage != null) {
            Gtk.gtk_container_remove(_native, checkedImage);  // will automatically get destroyed if no other references to it
            checkedImage = null;
            Gtk.gtk_widget_show_all(_native);
        }


        if (this.isChecked) {
            checkedImage = Gtk.gtk_image_new_from_file(checkedFile);
        } else {
            checkedImage = Gtk.gtk_image_new_from_file(uncheckedFile);
        }
        Gtk.gtk_image_menu_item_set_image(_native, checkedImage);

        //  must always re-set always-show after setting the image
        Gtk.gtk_image_menu_item_set_always_show_image(_native, true);

        Gtk.gtk_widget_show_all(_native);
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
