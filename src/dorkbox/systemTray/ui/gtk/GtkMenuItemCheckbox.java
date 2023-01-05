/*
 * Copyright 2021 dorkbox, llc
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

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import com.sun.jna.Pointer;

import dorkbox.jna.linux.GCallback;
import dorkbox.jna.linux.GObject;
import dorkbox.jna.linux.GtkEventDispatch;
import dorkbox.jna.linux.GtkTheme;
import dorkbox.os.OS;
import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.peer.CheckboxPeer;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.systemTray.util.HeavyCheckMark;
import dorkbox.systemTray.util.SizeAndScaling;

class GtkMenuItemCheckbox extends GtkBaseMenuItem implements CheckboxPeer, GCallback {
    private static volatile String checkedFile;

    // here, it doesn't matter what size the image is, as long as there is an image, the text in the menu will be shifted correctly
    // This is set from _AppIndicatorNativeTray or _GtkStatusIconNativeTray
    static String uncheckedFile = null;

    // Note:  So far, ONLY Ubuntu has managed to fail at rendering (via bad layouts) checkbox menu items.
    //          If there are OTHER OSes that fail, checks for them should be added here
    private static final boolean useFakeCheckMark;
    static {
        // this class is initialized on the GTK dispatch thread.

        if (SystemTray.AUTO_FIX_INCONSISTENCIES &&
            _AppIndicatorNativeTray.isLoaded &&
            OS.Linux.INSTANCE.isUbuntu()) {

            // Ubuntu < 17.10 (so 14.04, 14.10, 15.04, 15.10, 16.04, 16.10, 17.04) SCREW UP checkboxes. Ubuntu 17.10 uses gnome-shell properly and thus works correctly.
            int[] version = OS.Linux.INSTANCE.getUbuntuVersion();
            useFakeCheckMark = (version[0] < 17 || (version[0] == 17 && version[1] == 4));
        } else {
            useFakeCheckMark = false;
        }

        if (SystemTray.DEBUG) {
            SystemTray.logger.debug("Using Fake CheckMark: " + useFakeCheckMark);
        }
    }

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



    /**
     * called from inside GTK dispatch thread. ONLY creates the menu item, but DOES NOT attach it!
     * this is a FLOATING reference. See: https://developer.gnome.org/gobject/stable/gobject-The-Base-Object-Type.html#floating-ref
     *
     * Because Ubuntu AppIndicator checkbox's DO NOT align correctly, we use an image_menu_item (instead of a check_menu_item),
     * so that the alignment is correct for the menu item (with a check_menu_item, they are shifted left - which looks pretty bad)
     *
     * For AppIndicators, this is not possible to fix, because we cannot control how the menu's are rendered (this is by design)
     * Specifically, since it's implementation was copied from GTK, GtkCheckButton and GtkRadioButton allocate only the minimum size
     * necessary for its child. This causes the child alignment to fail. There is no fix we can apply - so we don't use them.
     *
     * Again, this is ONLY noticed on UBUNTU. For example, ElementaryOS is OK (it is also with a checkbox on the right).
     * ElementaryOS shows the checkbox on the right, everyone else is on the left. With eOS, we CANNOT show the spacer image, so we MUST
     * show this as a GTK Status Icon (not an AppIndicator), this way the "proper" checkbox is shown.
     */
    GtkMenuItemCheckbox(final GtkMenu parent) {
        super(useFakeCheckMark ?
                Gtk2.gtk_image_menu_item_new_with_mnemonic("") :
                Gtk2.gtk_check_menu_item_new_with_mnemonic(""));

        this.parent = parent;

        handlerId = GObject.g_signal_connect_object(_native, "activate", this, null, 0);

        if (useFakeCheckMark) {
            if (checkedFile == null) {
                Color color = GtkTheme.getTextColor();
                if (color == null) {
                    SystemTray.logger.error("Unable to determine the text color in use by your system. Please create an issue and include your " +
                                            "full OS configuration and desktop environment, including theme details, such as the theme name, color " +
                                            "variant, and custom theme options (if any).");
                    color = Color.BLACK;
                }

                if (checkedFile == null) {
                    Rectangle size = GtkTheme.getPixelTextHeight("X");
                    int imageHeight = SizeAndScaling.TRAY_MENU_SIZE;
                    int height = size.height;

                    if (SystemTray.DEBUG) {
                        SystemTray.logger.debug("Fake checkmark size: {}px", height);
                    }

                    if (_AppIndicatorNativeTray.isLoaded) {
                        // only app indicators don't need padding, as they automatically center the icon
                        checkedFile = HeavyCheckMark.get(color, height, height);
                    } else {
                        checkedFile = HeavyCheckMark.get(color, height, imageHeight);
                    }
                }
            }

            setCheckedIconForFakeCheckMarks();
        } else {
            GObject.g_signal_handler_block(_native, handlerId);
            Gtk2.gtk_check_menu_item_set_active(_native, false);
            GObject.g_signal_handler_unblock(_native, handlerId);
        }
    }

    // called by native code ONLY
    @Override
    public
    int callback(final Pointer instance, final Pointer data) {
        ActionListener callback = this.callback;
        if (callback != null) {
            GtkEventDispatch.proxyClick(callback);
        }

        return Gtk2.TRUE;
    }

    @Override
    public
    boolean hasImage() {
        return true;
    }

    @Override
    public
    void setSpacerImage(final boolean everyoneElseHasImages) {
        // no op
    }

    @Override
    public
    void setEnabled(final Checkbox menuItem) {
        GtkEventDispatch.dispatch(()->Gtk2.gtk_widget_set_sensitive(_native, menuItem.getEnabled()));
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

        GtkEventDispatch.dispatch(()->{
            Gtk2.gtk_menu_item_set_label(_native, textWithMnemonic);
            Gtk2.gtk_widget_show_all(_native);
        });
    }

    @SuppressWarnings({"Duplicates"})
    @Override
    public
    void setCallback(final Checkbox menuItem) {
        callback = menuItem.getCallback();  // can be set to null

        if (callback != null) {
            callback = new ActionListener() {
                final ActionListener cb = menuItem.getCallback();

                @Override
                public
                void actionPerformed(ActionEvent e) {
                    // this will run on the EDT, since we are calling it from the EDT. This can ALSO recursively call the callback
                    menuItem.setChecked(!isChecked);

                    // we want it to run on our own with our own action event info (so it is consistent across all platforms)
                    EventDispatch.runLater(()->{
                        try {
                            cb.actionPerformed(new ActionEvent(menuItem, ActionEvent.ACTION_PERFORMED, ""));
                        } catch (Throwable throwable) {
                            SystemTray.logger.error("Error calling menu checkbox entry {} click event.", menuItem.getText(), throwable);
                        }
                    });
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

            GtkEventDispatch.dispatch(()->{
                if (useFakeCheckMark) {
                    setCheckedIconForFakeCheckMarks();
                } else {
                    // note: this will trigger "activate", which will then trigger the callback.
                    // we assume this is consistent across ALL versions and variants of GTK
                    // https://github.com/GNOME/gtk/blob/master/gtk/gtkcheckmenuitem.c#L317
                    // this disables the signal handler, then enables it
                    GObject.g_signal_handler_block(_native, handlerId);
                    Gtk2.gtk_check_menu_item_set_active(_native, isChecked);
                    GObject.g_signal_handler_unblock(_native, handlerId);
                }
            });
        }
    }

    @Override
    public
    void setTooltip(final Checkbox menuItem) {
        GtkEventDispatch.dispatch(()->{
            // NOTE: this will not work for AppIndicator tray types!
            // null will remove the tooltip
            Gtk2.gtk_widget_set_tooltip_text(_native, menuItem.getTooltip());
        });
    }

    // this is pretty much ONLY for Ubuntu AppIndicators
    private
    void setCheckedIconForFakeCheckMarks() {
        if (checkedImage != null) {
            Gtk2.gtk_container_remove(_native, checkedImage);  // will automatically get destroyed if no other references to it
            checkedImage = null;
        }


        if (this.isChecked) {
            checkedImage = Gtk2.gtk_image_new_from_file(checkedFile);
        } else {
            checkedImage = Gtk2.gtk_image_new_from_file(uncheckedFile);
        }

        Gtk2.gtk_image_menu_item_set_image(_native, checkedImage);

        //  must always re-set always-show after setting the image
        Gtk2.gtk_image_menu_item_set_always_show_image(_native, true);

        Gtk2.gtk_widget_show_all(_native);
    }

    @Override
    public
    void setShortcut(final Checkbox checkbox) {
        char shortcut = checkbox.getShortcut();

        if (shortcut != 0) {
            this.mnemonicKey = Character.toLowerCase(shortcut);
        } else {
            this.mnemonicKey = 0;
        }

        setText(checkbox);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public
    void remove() {
        GtkEventDispatch.dispatch(()->{
            GtkMenuItemCheckbox.super.remove();

            callback = null;

            Gtk2.gtk_container_remove(parent._nativeMenu, _native);  // will automatically get destroyed if no other references to it

            if (image != null) {
                Gtk2.gtk_container_remove(_native, image); // will automatically get destroyed if no other references to it
                image = null;
            }

            parent.remove(GtkMenuItemCheckbox.this);
        });
    }
}
