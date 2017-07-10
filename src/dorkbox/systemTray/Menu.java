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

import java.awt.Component;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import dorkbox.systemTray.peer.MenuPeer;

/**
 * Represents a cross-platform menu that is displayed by the tray-icon or as a sub-menu
 */
@SuppressWarnings("unused")
public
class Menu extends MenuItem {
    // access on this object must be synchronized for object visibility
    final List<Entry> menuEntries = new ArrayList<Entry>();

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
     * @param peer the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param systemTray the system tray (which is the object that sits in the system tray)
     */
    public
    void bind(final MenuPeer peer, final Menu parent, final SystemTray systemTray) {
        super.bind(peer, parent, systemTray);

        List<Entry> copy;
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            // a copy is made to prevent deadlocks from occurring when operating in different threads
            copy = new ArrayList<Entry>(menuEntries);
        }

        for (int i = 0, menuEntriesSize = copy.size(); i < menuEntriesSize; i++) {
            final Entry menuEntry = copy.get(i);
            peer.add(this, menuEntry, i);
        }
    }

    /**
     * Adds a menu entry, separator, or sub-menu to this menu
     */
    public final
    <T extends Entry> T add(final T entry) {
        return add(entry, -1);
    }

    /**
     * Adds a JMenu sub-menu to this menu. Because this is a conversion, the JMenu is no longer valid after this action.
     */
    @SuppressWarnings("Duplicates")
    public final
    Menu add(final JMenu entry) {
        Menu menu = new Menu();
        menu.setEnabled(entry.isEnabled());

        Icon icon = entry.getIcon();
        BufferedImage bimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        menu.setImage(bimage);

        menu.setText(entry.getText());
        menu.setShortcut(entry.getMnemonic());

        Component[] menuComponents = entry.getMenuComponents();
        for (int i = 0, menuComponentsLength = menuComponents.length; i < menuComponentsLength; i++) {
            final Component c = menuComponents[i];

            if (c instanceof JMenu) {
                menu.add((JMenu) c);
            }
            else if (c instanceof JCheckBoxMenuItem) {
                menu.add((JCheckBoxMenuItem) c);
            }
            else if (c instanceof JMenuItem) {
                menu.add((JMenuItem) c);
            }
            else if (c instanceof JSeparator) {
                menu.add((JSeparator) c);
            }
        }

        add(menu);
        return this;
    }

    /**
     * Adds a JCheckBoxMenuItem entry to this menu. Because this is a conversion, the JCheckBoxMenuItem is no longer valid after this action.
     */
    public final
    Menu add(final JCheckBoxMenuItem entry) {
        Checkbox checkbox = new Checkbox();

        final ActionListener[] actionListeners = entry.getActionListeners();
        //noinspection Duplicates
        if (actionListeners != null) {
            if (actionListeners.length == 1) {
                checkbox.setCallback(actionListeners[0]);
            } else {
                ActionListener actionListener = new ActionListener() {
                    @Override
                    public
                    void actionPerformed(final ActionEvent e) {
                        for (ActionListener actionListener : actionListeners) {
                            actionListener.actionPerformed(e);
                        }
                    }
                };
                checkbox.setCallback(actionListener);
            }
        }

        checkbox.setEnabled(entry.isEnabled());
        checkbox.setChecked(entry.getState());
        checkbox.setShortcut(entry.getMnemonic());
        checkbox.setText(entry.getText());

        add(checkbox);
        return this;
    }

    /**
     * Adds a JMenuItem entry to this menu. Because this is a conversion, the JMenuItem is no longer valid after this action.
     */
    public final
    Menu add(final JMenuItem entry) {
        MenuItem item = new MenuItem();

        final ActionListener[] actionListeners = entry.getActionListeners();
        //noinspection Duplicates
        if (actionListeners != null) {
            if (actionListeners.length == 1) {
                item.setCallback(actionListeners[0]);
            } else {
                ActionListener actionListener = new ActionListener() {
                    @Override
                    public
                    void actionPerformed(final ActionEvent e) {
                        for (ActionListener actionListener : actionListeners) {
                            actionListener.actionPerformed(e);
                        }
                    }
                };
                item.setCallback(actionListener);
            }
        }

        item.setEnabled(entry.isEnabled());

        Icon icon = entry.getIcon();
        BufferedImage bimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        item.setImage(bimage);

        item.setShortcut(entry.getMnemonic());
        item.setText(entry.getText());

        add(item);
        return this;
    }

    /**
     * Adds a JSeparator entry to this menu. Because this is a conversion, the JSeparator is no longer valid after this action.
     */
    public final
    Menu add(final JSeparator entry) {
        Separator separator = new Separator();

        add(separator);
        return this;
    }

    /**
     * Adds a menu entry, separator, or sub-menu to this menu.
     */
    public
    <T extends Entry> T add(final T entry, int index) {
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            if (index == -1) {
                index = menuEntries.size();
                menuEntries.add(entry);
            } else {
                if (!menuEntries.isEmpty() && menuEntries.get(0) instanceof Status) {
                    // the "status" menu entry is ALWAYS first
                    index++;
                }
                menuEntries.add(index, entry);
            }

            if (peer != null) {
                ((MenuPeer) peer).add(this, entry, index);
            }
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
    public
    Entry getLast() {
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            if (!menuEntries.isEmpty()) {
                Entry entry;
                for (int i = menuEntries.size() - 1; i >= 0; i--) {
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
    public
    Entry get(final int menuIndex) {
        if (menuIndex < 0) {
            return null;
        }

        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
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
     *  This removes a menu entry from the menu.
     *
     * @param entry This is the menu entry to remove
     */
    public
    void remove(final Entry entry) {
        // null is passed in when a sub-menu is removing itself from us (because they have already called "remove" and have also
        // removed themselves from the menuEntries)
        if (entry != null) {
            Entry toRemove = null;

            synchronized (menuEntries) {
                // access on this object must be synchronized for object visibility
                for (Iterator<Entry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
                    final Entry entry__ = iterator.next();
                    if (entry__ == entry) {
                        iterator.remove();
                        toRemove = entry__;
                        break;
                    }
                }
            }
            if (toRemove != null) {
                toRemove.remove();
                toRemove = null;
            }


            // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
            synchronized (menuEntries) {
                // access on this object must be synchronized for object visibility
                if (!menuEntries.isEmpty()) {
                    if (menuEntries.get(0) instanceof dorkbox.systemTray.Separator) {
                        toRemove = menuEntries.get(0);
                    }
                }
            }
            if (toRemove != null) {
                remove(toRemove);
                toRemove = null;
            }


            // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
            synchronized (menuEntries) {
                // access on this object must be synchronized for object visibility
                if (!menuEntries.isEmpty()) {
                    if (menuEntries.get(menuEntries.size()-1) instanceof dorkbox.systemTray.Separator) {
                        toRemove = menuEntries.get(menuEntries.size() - 1);
                    }
                }
            }
            if (toRemove != null) {
                remove(toRemove);
            }
        }
    }

    /**
     *  This removes all menu entries from this menu
     */
    public
    void clear() {
        List<Entry> copy;
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            // a copy is made to prevent deadlocks from occurring when operating in different threads
            // have to make copy because we are deleting all of them, and sub-menus remove themselves from parents
            copy = new ArrayList<Entry>(menuEntries);
            menuEntries.clear();
        }

        for (Entry entry : copy) {
            entry.remove();
        }
    }


    /**
     *  This removes all menu entries from this menu AND this menu from it's parent
     */
    @Override
    public
    void remove() {
        clear();

        super.remove();
    }
}
