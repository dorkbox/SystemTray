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
package dorkbox.systemTray;

import java.awt.Image;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dorkbox.systemTray.util.MenuHook;
import dorkbox.systemTray.util.Status;

/**
 * Represents a cross-platform menu that is displayed by the tray-icon or as a sub-menu
 */
@SuppressWarnings("unused")
public
class Menu extends MenuItem {
    protected final List<Entry> menuEntries = new ArrayList<Entry>();

    public
    Menu() {
    }

    public
    Menu(final String text) {
        super(text);
    }

    public
    Menu(final String text, final ActionListener callback) {
        super(text, callback);
    }

    public
    Menu(final String text, final String imagePath) {
        super(text, imagePath);
    }

    public
    Menu(final String text, final File imageFile) {
        super(text, imageFile);
    }

    public
    Menu(final String text, final URL imageUrl) {
        super(text, imageUrl);
    }

    public
    Menu(final String text, final InputStream imageStream) {
        super(text, imageStream);
    }

    public
    Menu(final String text, final Image image) {
        super(text, image);
    }

    public
    Menu(final String text, final String imagePath, final ActionListener callback) {
        super(text, imagePath, callback);
    }

    public
    Menu(final String text, final File imageFile, final ActionListener callback) {
        super(text, imageFile, callback);
    }

    public
    Menu(final String text, final URL imageUrl, final ActionListener callback) {
        super(text, imageUrl, callback);
    }

    public
    Menu(final String text, final InputStream imageStream, final ActionListener callback) {
        super(text, imageStream, callback);
    }

    public
    Menu(final String text, final Image image, final ActionListener callback) {
        super(text, image, callback);
    }

    /**
     * @param hook the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param systemTray the system tray (which is the object that sits in the system tray)
     */
    public synchronized
    void bind(final MenuHook hook, final Menu parent, final SystemTray systemTray) {
        super.bind(hook, parent, systemTray);

        synchronized (menuEntries) {
            for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                final Entry menuEntry = menuEntries.get(i);
                hook.add(this, menuEntry, i);
            }
        }
    }

    /**
     * Adds a swing widget as a menu entry.
     *
     * @param widget the JComponent that is to be added as an entry
     */
// TODO: buggy. The menu will **sometimes** stop responding to the "enter" key after this. Mnemonics still work however.
//    Entry add(JComponent widget);

    /**
     * Adds a menu entry, separator, or sub-menu to this menu
     */
    public final
    <T extends Entry> T add(final T entry) {
        return add(entry, -1);
    }

    /**
     * Adds a menu entry, separator, or sub-menu to this menu.
     */
    public final
    <T extends Entry> T  add(final T entry, int index) {
        synchronized (menuEntries) {
            if (index == -1) {
                menuEntries.add(entry);
            } else {
                if (!menuEntries.isEmpty() && menuEntries.get(0) instanceof Status) {
                    // the "status" menu entry is ALWAYS first
                    index++;
                }
                menuEntries.add(index, entry);
            }
        }

        if (hook != null) {
            ((MenuHook) hook).add(this, entry, index);
        }

        return entry;
    }

    /**
     * Gets the first menu entry or sub-menu, ignoring status and separators
     */
    public final
    Entry getFirst() {
        return get(0);
    }

    /**
     * Gets the last menu entry or sub-menu, ignoring status and separators
     */
    public final
    Entry getLast() {
        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            if (!menuEntries.isEmpty()) {
                Entry entry;
                for (int i = menuEntries.size()-1; i >= 0; i--) {
                    entry = menuEntries.get(i);

                    if (!(entry instanceof Separator || entry instanceof Status)) {
                        return entry;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gets the menu entry or sub-menu for a specified index (zero-index), ignoring status and separators
     *
     * @param menuIndex the menu entry index to use to retrieve the menu entry.
     */
    public final
    Entry get(final int menuIndex) {
        if (menuIndex < 0) {
            return null;
        }

        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            if (!menuEntries.isEmpty()) {
                int count = 0;
                for (Entry entry : menuEntries) {
                    if (entry instanceof Separator || entry instanceof Status) {
                        continue;
                    }

                    if (count == menuIndex) {
                        return entry;
                    }

                    count++;
                }
            }
        }

        return null;
    }

    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param entry This is the menu entry to remove
     */
    public final
    void remove(final Entry entry) {
        // null is passed in when a sub-menu is removing itself from us (because they have already called "remove" and have also
        // removed themselves from the menuEntries)
        if (entry != null) {
            synchronized (menuEntries) {
                for (Iterator<Entry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
                    final Entry entry__ = iterator.next();
                    if (entry__ == entry) {
                        iterator.remove();
                        entry.remove();
                        break;
                    }
                }
            }
        }
    }

    /**
     *  This removes all menu entries from this menu
     */
    public final
    void removeAll() {
        synchronized (menuEntries) {
            // have to make copy because we are deleting all of them, and sub-menus remove themselves from parents
            ArrayList<Entry> menuEntriesCopy = new ArrayList<Entry>(this.menuEntries);
            for (Entry entry : menuEntriesCopy) {
                entry.remove();
            }
            menuEntries.clear();
        }
    }


    /**
     *  This removes all menu entries from this menu AND this menu from it's parent
     */
    @Override
    public synchronized
    void remove() {
        removeAll();

        super.remove();
    }
}
