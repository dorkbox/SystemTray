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

import static dorkbox.systemTray.SystemTray.TIMEOUT;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.jna.Pointer;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.linux.jna.Gobject;
import dorkbox.systemTray.linux.jna.Gtk;

class GtkMenu extends Menu implements MenuEntry {
    // menu entry that this menu is attached to. Will be NULL when it's the system tray
    private final GtkEntryItem menuEntry;

    // must ONLY be created at the end of delete!
    volatile Pointer _native;


    // called on dispatch
    GtkMenu(final SystemTray systemTray, final GtkMenu parent, final GtkEntryItem menuEntry) {
        super(systemTray, parent);

        if (menuEntry != null) {
            // by default, no callback on a menu entry means it's DISABLED. we have to undo that, because we don't have a callback for menus
            Gtk.gtk_widget_set_sensitive(menuEntry._native, Gtk.TRUE);
        }
        this.menuEntry = menuEntry;
    }

    /**
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    @Override
    protected
    void dispatch(final Runnable runnable) {
        Gtk.dispatch(runnable);
    }

    /**
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    protected
    void dispatchAndWait(final Runnable runnable) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        Gtk.dispatch(new Runnable() {
            @Override
            public
            void run() {
                try {
                    runnable.run();
                } finally {
                    countDownLatch.countDown();
                }
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            SystemTray.logger.error("Error waiting for dispatch to complete.", new Exception());
        }
    }


    public
    void shutdown() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                obliterateMenu();

                Gtk.shutdownGui();
            }
        });
    }

    @Override
    public
    void addSeparator() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                // To work around this issue, we destroy then recreate the menu every time something is changed.
                synchronized (menuEntries) {
                    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                    // To work around this issue, we destroy then recreate the menu every time something is changed.
                    deleteMenu();

                    GtkEntry menuEntry = new GtkEntrySeparator(GtkMenu.this);
                    menuEntries.add(menuEntry);

                    createMenu();
                }
            }
        });
    }

    @Override
    public
    String getText() {
        return menuEntry.getText();
    }

    @Override
    public
    void setText(final String newText) {
        menuEntry.setText(newText);
    }

    @Override
    public
    void setImage(final File imageFile) {
        menuEntry.setImage(imageFile);
    }

    @Override
    public
    void setImage(final String imagePath) {
        menuEntry.setImage(imagePath);
    }

    @Override
    public
    void setImage(final URL imageUrl) {
        menuEntry.setImage(imageUrl);
    }

    @Override
    public
    void setImage(final String cacheName, final InputStream imageStream) {
        menuEntry.setImage(cacheName, imageStream);
    }

    @Override
    public
    void setImage(final InputStream imageStream) {
        menuEntry.setImage(imageStream);
    }

    @Override
    public
    boolean hasImage() {
        return menuEntry.hasImage();
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
        // NO OP.
    }


   /**
    * Called when this sub-menu is removed from it's parent menu
    */
    protected
    void removePrivate() {
        // delete all of the children
        obliterateMenu();

        // remove the gtk entry item from our parent menu
        if (menuEntry != null) {
            menuEntry.remove();
        }
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    /**
     * Deletes the menu, and unreferences everything in it. ALSO recreates ONLY the menu object.
     */
    void deleteMenu() {
        if (_native != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    MenuEntry menuEntry__ = menuEntries.get(i);

                    if (menuEntry__ instanceof GtkEntry) {
                        GtkEntry entry = (GtkEntry) menuEntry__;

                        Gobject.g_object_force_floating(entry._native);
                        Gtk.gtk_container_remove(_native, entry._native);
                    }
                    else if (menuEntry__ instanceof GtkMenu) {
                        GtkMenu subMenu = (GtkMenu) menuEntry__;

                        Gobject.g_object_force_floating(subMenu.menuEntry._native);
                        Gtk.gtk_container_remove(_native, subMenu.menuEntry._native);
                    }
                }

                Gtk.gtk_widget_destroy(_native);
            }
        }

        if (getParent() != null) {
            ((GtkMenu) getParent()).deleteMenu();
        }

        // makes a new one
        _native = Gtk.gtk_menu_new();

        // binds sub-menu to entry (if it exists! it does not for the root menu)
        if (menuEntry != null) {
            Gtk.gtk_menu_item_set_submenu(menuEntry._native, _native);
        }
    }

    // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
    // To work around this issue, we destroy then recreate the menu every time something is changed.
    void createMenu() {
        if (getParent() != null) {
            ((GtkMenu) getParent()).createMenu();
        }

        boolean hasImages = false;

        // now add back other menu entries
        synchronized (menuEntries) {
            for (int i = 0; i < menuEntries.size(); i++) {
                MenuEntry menuEntry__ = menuEntries.get(i);
                hasImages |= menuEntry__.hasImage();
            }

            for (int i = 0; i < menuEntries.size(); i++) {
                MenuEntry menuEntry__ = menuEntries.get(i);

                // the menu entry looks FUNKY when there are a mis-match of entries WITH and WITHOUT images
                if (menuEntry__ instanceof GtkEntry) {
                    GtkEntry entry = (GtkEntry) menuEntry__;
                    entry.setSpacerImage(hasImages);

                    // will also get:  gsignal.c:2516: signal 'child-added' is invalid for instance '0x7f1df8244080' of type 'GtkMenu'
                    Gtk.gtk_menu_shell_append(this._native, entry._native);
                    Gobject.g_object_ref_sink(entry._native);  // undoes "floating"
                }
                else if (menuEntry__ instanceof GtkMenu) {
                    GtkMenu subMenu = (GtkMenu) menuEntry__;

                    // will also get:  gsignal.c:2516: signal 'child-added' is invalid for instance '0x7f1df8244080' of type 'GtkMenu'
                    Gtk.gtk_menu_shell_append(this._native, subMenu.menuEntry._native);
                    Gobject.g_object_ref_sink(subMenu.menuEntry._native);  // undoes "floating"

                    if (subMenu.getParent() != GtkMenu.this) {
                        // we don't want to "createMenu" on our sub-menu that is assigned to us directly, as they are already doing it
                        subMenu.createMenu();
                    }
                }
            }

            onMenuAdded(_native);
            Gtk.gtk_widget_show_all(_native);
        }
    }

    /**
     * Called inside the gdk_threads block
     */
    void onMenuAdded(final Pointer menu) {
        // only needed for AppIndicator
    }

    /**
     * Completely obliterates the menu, no possible way to reconstruct it.
     */
    void obliterateMenu() {
        if (_native != null) {
            // have to remove all other menu entries
            synchronized (menuEntries) {
                for (int i = 0; i < menuEntries.size(); i++) {
                    MenuEntry menuEntry__ = menuEntries.get(i);

                    if (menuEntry__ instanceof GtkEntry) {
                        ((GtkEntry) menuEntry__).removePrivate();
                    }
                    else if (menuEntry__ instanceof GtkMenu) {
                        ((GtkMenu) menuEntry__).removePrivate();
                    }
                }
                menuEntries.clear();

                Gtk.gtk_widget_destroy(_native);
            }
        }
    }


    /**
     * Will add a new menu entry, or update one if it already exists
     */
    protected
    MenuEntry addEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback) {
        // some implementations of appindicator, do NOT like having a menu added, which has no menu items yet.
        // see: https://bugs.launchpad.net/glipper/+bug/1203888

        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        // have to wait for the value
        final AtomicReference<MenuEntry> value = new AtomicReference<MenuEntry>();

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = get(menuText);
                    if (menuEntry == null) {
                        // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                        // To work around this issue, we destroy then recreate the menu every time something is changed.
                        deleteMenu();

                        menuEntry = new GtkEntryItem(GtkMenu.this, callback);
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);
                        menuEntries.add(menuEntry);

                        createMenu();
                    } else if (menuEntry instanceof GtkEntryItem) {
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);
                    }

                    value.set(menuEntry);
                }
            }
        });

        return value.get();
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    protected
    Menu addMenu_(final String menuText, final File imagePath) {
        // some implementations of appindicator, do NOT like having a menu added, which has no menu items yet.
        // see: https://bugs.launchpad.net/glipper/+bug/1203888

        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<Menu> value = new AtomicReference<Menu>();

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = get(menuText);
                    if (menuEntry == null) {
                        // some GTK libraries DO NOT let us add items AFTER the menu has been attached to the indicator.
                        // To work around this issue, we destroy then recreate the menu every time something is changed.
                        deleteMenu();

                        GtkMenu subMenu = new GtkMenu(getSystemTray(), GtkMenu.this, new GtkEntryItem(GtkMenu.this, null));
                        subMenu.setText(menuText);
                        subMenu.setImage(imagePath);

                        menuEntries.add(subMenu);

                        value.set(subMenu);

                        createMenu();
                    } else if (menuEntry instanceof GtkMenu) {
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);

                        value.set(((GtkMenu) menuEntry));
                    }
                }
            }
        });

        return value.get();
    }

    @Override
    public
    void remove() {
        GtkMenu parent = (GtkMenu) getParent();

        Gtk.gtk_container_remove(parent._native, _native);
        Gtk.gtk_menu_shell_deactivate(parent._native, _native);

        removePrivate();

        Gtk.gtk_widget_destroy(_native);

        // have to rebuild the menu now...
        parent.deleteMenu();
        parent.createMenu();
    }
}
