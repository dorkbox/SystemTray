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


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.sun.jna.Pointer;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.jna.linux.Gtk;
import dorkbox.systemTray.util.MenuHook;
import dorkbox.systemTray.util.Status;

class GtkMenu extends GtkMenuBaseItem implements MenuHook {
    // this is a list (that mirrors the actual list) BECAUSE we have to create/delete the entire menu in GTK every time something is changed
    private final List<GtkMenuBaseItem> menuEntries = new LinkedList<GtkMenuBaseItem>();

    private final GtkMenu parent;
    volatile Pointer _nativeMenu;  // must ONLY be created at the end of delete!

    private final Pointer _nativeEntry; // is what is added to the parent menu, if we are NOT on the system tray
    private volatile Pointer image;

    // The mnemonic will ONLY show-up once a menu entry is selected. IT WILL NOT show up before then!
    // AppIndicators will only show if you use the keyboard to navigate
    // GtkStatusIconTray will show on mouse+keyboard movement
    private volatile char mnemonicKey = 0;

    // have to make sure no other methods can call obliterate, delete, or create menu once it's already started
    private volatile boolean obliterateInProgress = false;

    // This is NOT a copy constructor!
    @SuppressWarnings("IncompleteCopyConstructor")
    GtkMenu(final GtkMenu parent) {
        this.parent = parent;

        if (parent != null) {
            _nativeEntry = Gtk.gtk_image_menu_item_new_with_mnemonic(""); // is what is added to the parent menu
        } else {
            _nativeEntry = null;
        }
    }

    GtkMenu getParent() {
        return parent;
    }

    private
    void add(final GtkMenuBaseItem item, final int index) {
        if (index > 0) {
            menuEntries.add(index, item);
        } else {
            menuEntries.add(item);
        }
    }

    /**
     * Called inside the gdk_threads block
     */
    protected
    void onMenuAdded(final Pointer menu) {
        // only needed for AppIndicator
    }


    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    /**
     * Deletes the menu, and unreferences everything in it. ALSO recreates ONLY the menu object.
     */
    private
    void deleteMenu() {
        if (obliterateInProgress) {
            return;
        }

        if (_nativeMenu != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                    final GtkMenuBaseItem menuEntry__ = menuEntries.get(i);
                    menuEntry__.onDeleteMenu(_nativeMenu);
                }

                Gtk.gtk_widget_destroy(_nativeMenu);
            }
        }

        if (parent != null) {
            parent.deleteMenu();
        }

        // makes a new one
        _nativeMenu = Gtk.gtk_menu_new();

        // binds sub-menu to entry (if it exists! it does not for the root menu)
        if (parent != null) {
            Gtk.gtk_menu_item_set_submenu(_nativeEntry, _nativeMenu);
        }
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    private
    void createMenu() {
        if (obliterateInProgress) {
            return;
        }

        if (parent != null) {
            parent.createMenu();
        }

        boolean hasImages = false;

        // now add back other menu entries
        synchronized (menuEntries) {
            for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                final GtkMenuBaseItem menuEntry__ = menuEntries.get(i);
                hasImages |= menuEntry__.hasImage();
            }

            for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                // the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images
                final GtkMenuBaseItem menuEntry__ = menuEntries.get(i);
                menuEntry__.onCreateMenu(_nativeMenu, hasImages);

                if (menuEntry__ instanceof GtkMenu) {
                    GtkMenu subMenu = (GtkMenu) menuEntry__;
                    if (subMenu.getParent() != GtkMenu.this) {
                        // we don't want to "createMenu" on our sub-menu that is assigned to us directly, as they are already doing it
                        subMenu.createMenu();
                    }
                }
            }

            onMenuAdded(_nativeMenu);
            Gtk.gtk_widget_show_all(_nativeMenu);    // necessary to guarantee widget is visible (doesn't always show_all for all children)
        }
    }

    /**
     * must be called on the dispatch thread
     *
     * Completely obliterates the menu, no possible way to reconstruct it.
     */
    private
    void obliterateMenu() {
        if (_nativeMenu != null && !obliterateInProgress) {
            obliterateInProgress = true;

            // have to remove all other menu entries
            synchronized (menuEntries) {
                // a copy is made because sub-menus remove themselves from parents when .remove() is called. If we don't
                // do this, errors will be had because indices don't line up anymore.
                ArrayList<GtkMenuBaseItem> menuEntriesCopy = new ArrayList<GtkMenuBaseItem>(this.menuEntries);

                for (int i = 0, menuEntriesSize = menuEntriesCopy.size(); i < menuEntriesSize; i++) {
                    final GtkMenuBaseItem menuEntry__ = menuEntriesCopy.get(i);
                    menuEntry__.remove();
                }
                this.menuEntries.clear();
                menuEntriesCopy.clear();

                Gtk.gtk_widget_destroy(_nativeMenu);
                _nativeMenu = null;
            }

            obliterateInProgress = false;
        }
    }

    @Override
    public
    void add(final Menu parentMenu, final Entry entry, final int index) {
        // must always be called on the GTK dispatch. This must be dispatchAndWait
        Gtk.dispatchAndWait(new Runnable() {
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
                    add(item, index);
                    ((Menu) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Separator) {
                    GtkMenuItemSeparator item = new GtkMenuItemSeparator(GtkMenu.this);
                    add(item, index);
                    entry.bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Checkbox) {
                    GtkMenuItemCheckbox item = new GtkMenuItemCheckbox(GtkMenu.this);
                    add(item, index);
                    ((Checkbox) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof Status) {
                    GtkMenuItemStatus item = new GtkMenuItemStatus(GtkMenu.this);
                    add(item, index);
                    ((Status) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }
                else if (entry instanceof MenuItem) {
                    GtkMenuItem item = new GtkMenuItem(GtkMenu.this);
                    add(item, index);
                    ((MenuItem) entry).bind(item, parentMenu, parentMenu.getSystemTray());
                }

                createMenu();
            }
        });
    }


    // NOTE: XFCE used to use appindicator3, which DOES NOT support images in the menu. This change was reverted.
    // see: https://ask.fedoraproject.org/en/question/23116/how-to-fix-missing-icons-in-program-menus-and-context-menus/
    // see: https://git.gnome.org/browse/gtk+/commit/?id=627a03683f5f41efbfc86cc0f10e1b7c11e9bb25
    @SuppressWarnings("Duplicates")
    @Override
    public
    void setImage(final MenuItem menuItem) {
        // is overridden by system tray
        setLegitImage(menuItem.getImage() != null);

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (image != null) {
                    Gtk.gtk_widget_destroy(image);
                    image = null;
                    Gtk.gtk_widget_show_all(_nativeEntry);
                }

                if (menuItem.getImage() != null) {
                    image = Gtk.gtk_image_new_from_file(menuItem.getImage()
                                                                .getAbsolutePath());
                    Gtk.gtk_image_menu_item_set_image(_nativeEntry, image);

                    //  must always re-set always-show after setting the image
                    Gtk.gtk_image_menu_item_set_always_show_image(_nativeEntry, true);
                }

                Gtk.gtk_widget_show_all(_nativeEntry);
            }
        });
    }

    @Override
    public
    void setEnabled(final MenuItem menuItem) {
        // is overridden by system tray
        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_widget_set_sensitive(_nativeEntry, menuItem.getEnabled());
            }
        });
    }

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

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                Gtk.gtk_menu_item_set_label(_nativeEntry, textWithMnemonic);
                Gtk.gtk_widget_show_all(_nativeEntry);
            }
        });
    }

    @Override
    public
    void setCallback(final MenuItem menuItem) {
        // can't have a callback for menus!
    }

    @Override
    public
    void setShortcut(final MenuItem menuItem) {
        this.mnemonicKey = Character.toLowerCase(menuItem.getShortcut());

        setText(menuItem);
    }


    @Override
    void onDeleteMenu(final Pointer parentNative) {
        if (parent != null) {
            onDeleteMenu(parentNative, _nativeEntry);
        }
    }

    @Override
    void onCreateMenu(final Pointer parentNative, final boolean hasImagesInMenu) {
        if (parent != null) {
            onCreateMenu(parentNative, _nativeEntry, hasImagesInMenu);
        }
    }

    // called when a child removes itself from the parent menu. Does not work for sub-menus
    public
    void remove(final GtkMenuBaseItem item) {
        synchronized (menuEntries) {
            menuEntries.remove(item);
        }

        // have to rebuild the menu now...
        deleteMenu();
        createMenu();
    }

    // a child will always remove itself from the parent.
    @Override
    public
    void remove() {
        Gtk.dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                GtkMenu parent = getParent();

                if (parent != null) {
                    // have to remove from the  parent.menuEntries first
                    synchronized (parent.menuEntries) {
                        parent.menuEntries.remove(GtkMenu.this);
                    }
                }

                // delete all of the children of this submenu (must happen before the menuEntry is removed)
                obliterateMenu();

                if (parent != null) {
                    // remove the gtk entry item from our menu NATIVE components
                    Gtk.gtk_menu_item_set_submenu(_nativeEntry, null);

                    // have to rebuild the menu now...
                    parent.deleteMenu();
                    parent.createMenu();
                }
            }
        });
    }
}
