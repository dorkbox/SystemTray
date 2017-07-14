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

import static dorkbox.util.jna.linux.Gtk.Gtk2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Pointer;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.peer.MenuPeer;
import dorkbox.util.jna.linux.GtkEventDispatch;

@SuppressWarnings("deprecation")
class GtkMenu extends GtkBaseMenuItem implements MenuPeer {
    // this is a list (that mirrors the actual list) BECAUSE we have to create/delete the entire menu in GTK every time something is changed
    private final List<GtkBaseMenuItem> menuEntries = new ArrayList<GtkBaseMenuItem>();

    private final GtkMenu parent;
    volatile Pointer _nativeMenu;  // must ONLY be created at the end of delete!

    private volatile Pointer image;

    // The mnemonic will ONLY show-up once a menu entry is selected. IT WILL NOT show up before then!
    // AppIndicators will only show if you use the keyboard to navigate
    // GtkStatusIconTray will show on mouse+keyboard movement
    private volatile char mnemonicKey = 0;

    // have to make sure no other methods can call obliterate, delete, or create menu once it's already started
    private AtomicBoolean obliterateInProgress = new AtomicBoolean(false);

    // called by the system tray constructors
    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    GtkMenu() {
        super(null);
        this.parent = null;
    }

    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    private
    GtkMenu(final GtkMenu parent) {
        super(Gtk2.gtk_image_menu_item_new_with_mnemonic("")); // is what is added to the parent menu (so images work)
        this.parent = parent;
    }

    GtkMenu getParent() {
        return parent;
    }

    /**
     * Called inside the gdk_threads block
     *
     * ALWAYS CALLED ON THE EDT
     */
    protected
    void onMenuAdded(final Pointer menu) {
        // only needed for AppIndicator
    }


    /**
     * Deletes the menu, and unreferences everything in it. ALSO recreates ONLY the menu object.
     *
     * some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
     * To work around this issue, we destroy then recreate the menu every time something is changed.
     *
     * ALWAYS CALLED ON EDT
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private
    void deleteMenu() {
        if (obliterateInProgress.get()) {
            return;
        }

        if (_nativeMenu != null) {
            // have to remove all other menu entries
            for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                final GtkBaseMenuItem menuEntry__ = menuEntries.get(i);
                menuEntry__.onDeleteMenu(_nativeMenu);
            }

            Gtk2.gtk_widget_destroy(_nativeMenu);
        }

        if (parent != null) {
            parent.deleteMenu();
        }

        // makes a new one
        _nativeMenu = Gtk2.gtk_menu_new();

        // binds sub-menu to entry (if it exists! it does not for the root menu)
        if (parent != null) {
            Gtk2.gtk_menu_item_set_submenu(_native, _nativeMenu);
        }
    }

    /**
     * some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
     *
     * To work around this issue, we destroy then recreate the menu every time something is changed.
     *
     * ALWAYS CALLED ON THE EDT
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private
    void createMenu() {
        if (obliterateInProgress.get()) {
            return;
        }

        if (parent != null) {
            parent.createMenu();
        }

        // now add back other menu entries
        boolean hasImages = false;

        for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
            final GtkBaseMenuItem menuEntry__ = menuEntries.get(i);
            hasImages |= menuEntry__.hasImage();
        }

        for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
            // the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images
            final GtkBaseMenuItem menuEntry__ = menuEntries.get(i);
            menuEntry__.onCreateMenu(_nativeMenu, hasImages);

            if (menuEntry__ instanceof GtkMenu) {
                GtkMenu subMenu = (GtkMenu) menuEntry__;
                if (subMenu.getParent() != GtkMenu.this) {
                    // we don't want to "createMenu" on our sub-menu that is assigned to us directly, as they are already doing it
                    subMenu.createMenu();
                }
            }
        }

        Gtk2.gtk_widget_show_all(_nativeMenu);    // necessary to guarantee widget is visible (doesn't always show_all for all children)
        onMenuAdded(_nativeMenu);
    }

    /**
     * Completely obliterates the menu, no possible way to reconstruct it.
     *
     * ALWAYS CALLED ON THE EDT
     */
    @SuppressWarnings("ForLoopReplaceableByForEach")
    private
    void obliterateMenu() {
        if (_nativeMenu != null && !obliterateInProgress.get()) {
            obliterateInProgress.set(true);

            // have to remove all other menu entries

            // a copy is made because sub-menus remove themselves from parents when .remove() is called. If we don't
            // do this, errors will be had because indices don't line up anymore.
            ArrayList<GtkBaseMenuItem> menuEntriesCopy = new ArrayList<GtkBaseMenuItem>(menuEntries);
            menuEntries.clear();

            for (int i = 0, menuEntriesSize = menuEntriesCopy.size(); i < menuEntriesSize; i++) {
                final GtkBaseMenuItem menuEntry__ = menuEntriesCopy.get(i);
                menuEntry__.remove();
            }
            menuEntriesCopy.clear();

            Gtk2.gtk_widget_destroy(_nativeMenu);
            _nativeMenu = null;

            obliterateInProgress.set(false);
        }
    }

    @Override
    public
    void add(final Menu parentMenu, final Entry entry, final int index) {
        // must always be called on the GTK dispatch. This must be dispatchAndWait
        GtkEventDispatch.dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                deleteMenu();

                if (entry instanceof Menu) {
                    // some implementations of appindicator, do NOT like having a menu added, which has no menu items yet.
                    // see: https://bugs.launchpad.net/glipper/+bug/1203888
                    GtkMenu item = new GtkMenu(GtkMenu.this);
                    menuEntries.add(index, item);
                    ((Menu) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Separator) {
                    GtkMenuItemSeparator item = new GtkMenuItemSeparator(GtkMenu.this);
                    menuEntries.add(index, item);
                    entry.bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Checkbox) {
                    GtkMenuItemCheckbox item = new GtkMenuItemCheckbox(GtkMenu.this);
                    menuEntries.add(index, item);
                    ((Checkbox) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Status) {
                    GtkMenuItemStatus item = new GtkMenuItemStatus(GtkMenu.this);
                    menuEntries.add(index, item);
                    ((Status) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof MenuItem) {
                    GtkMenuItem item = new GtkMenuItem(GtkMenu.this);
                    menuEntries.add(index, item);
                    ((MenuItem) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }

                createMenu();
            }
        });
    }


    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25

    // is overridden in tray impl
    @SuppressWarnings("Duplicates")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        // is overridden by system tray
        setLegitImage(menuItem.getImage() != null);

        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (image != null) {
                    Gtk2.gtk_container_remove(_native, image); // will automatically get destroyed if no other references to it
                    image = null;
                    Gtk2.gtk_widget_show_all(_native);
                }

                if (menuItem.getImage() != null) {
                    image = Gtk2.gtk_image_new_from_file(menuItem.getImage()
                                                                 .getAbsolutePath());
                    Gtk2.gtk_image_menu_item_set_image(_native, image);

                    //  must always re-set always-show after setting the image
                    Gtk2.gtk_image_menu_item_set_always_show_image(_native, true);
                }

                Gtk2.gtk_widget_show_all(_native);
            }
        });
    }

    // is overridden in tray impl
    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        // is overridden by system tray
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk2.gtk_widget_set_sensitive(_native, menuItem.getEnabled());
            }
        });
    }

    // is overridden in tray impl
    @SuppressWarnings("Duplicates")
    @Override
    public
    void setText(final MenuItem menuItem) {
        // is overridden by system tray
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
                Gtk2.gtk_menu_item_set_label(_native, textWithMnemonic);
                Gtk2.gtk_widget_show_all(_native);
            }
        });
    }

    // is overridden in tray impl
    @Override
    public
    void setCallback(final MenuItem menuItem) {
        // can't have a callback for menus!
    }

    // is overridden in tray impl
    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        this.mnemonicKey = Character.toLowerCase(menuItem.getShortcut());

        setText(menuItem);
    }

    /**
     * called when a child removes itself from the parent menu. Does not work for sub-menus
     *
     * ALWAYS CALLED ON THE EDT
     */
    public
    void remove(final GtkBaseMenuItem item) {
        menuEntries.remove(item);

        // have to rebuild the menu now...
        deleteMenu();  // must be on EDT
        createMenu();  // must be on EDT
    }

    // a child will always remove itself from the parent.
    @Override
    public
    void remove() {
        GtkEventDispatch.dispatch(new Runnable() {
            @Override
            public
            void run() {
                GtkMenu parent = getParent();

                if (parent != null) {
                    // have to remove from the  parent.menuEntries first
                    parent.menuEntries.remove(GtkMenu.this);
                }

                // delete all of the children of this submenu (must happen before the menuEntry is removed)
                obliterateMenu(); // must be on EDT

                if (parent != null) {
                    // remove the gtk entry item from our menu NATIVE components
                    Gtk2.gtk_menu_item_set_submenu(_native, null);

                    // have to rebuild the menu now...
                    parent.deleteMenu();  // must be on EDT
                    parent.createMenu();  // must be on EDT
                }
            }
        });
    }
}
