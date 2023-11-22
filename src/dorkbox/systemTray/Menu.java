/*
 * Copyright 2023 dorkbox, llc
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
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;

import dorkbox.systemTray.peer.EntryPeer;
import dorkbox.systemTray.peer.MenuPeer;
import dorkbox.systemTray.util.EventDispatch;
import dorkbox.systemTray.util.ImageResizeUtil;
import dorkbox.util.SwingUtil;

/**
 * Represents a cross-platform menu that is displayed by the tray-icon or as a sub-menu
 */
@SuppressWarnings("unused")
public
class Menu extends MenuItem {
    // access on this object must be synchronized for object visibility
    final List<Entry> menuEntries = new ArrayList<>();

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

    public
    Menu(final JMenu jMenu) {
        super();

        setEnabled(jMenu.isEnabled());

        Icon icon = jMenu.getIcon();
        if (icon != null) {
            BufferedImage bimage = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
            setImage(bimage);
        }

        setText(jMenu.getText());
        setShortcut(jMenu.getMnemonic());

        Component[] menuComponents = jMenu.getMenuComponents();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, menuComponentsLength = menuComponents.length; i < menuComponentsLength; i++) {
            final Component c = menuComponents[i];

            if (c instanceof JMenu) {
                add((JMenu) c);
            }
            else if (c instanceof JCheckBoxMenuItem) {
                add((JCheckBoxMenuItem) c);
            }
            else if (c instanceof JMenuItem) {
                add((JMenuItem) c);
            }
            else if (c instanceof JSeparator) {
                add((JSeparator) c);
            }
        }
    }

    /**
     * @param peer the platform specific implementation for all actions for this type
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param imageResizeUtil the utility used to resize images. This can be Tray specific because of cache requirements
     */
    public
    void bind(final MenuPeer peer, final Menu parent, ImageResizeUtil imageResizeUtil) {
        super.bind(peer, parent, imageResizeUtil);

        List<Entry> copy;
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            // a copy is made to prevent deadlocks from occurring when operating in different threads
            copy = new ArrayList<>(menuEntries);
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
        add(new Menu(entry));
        return this;
    }

    /**
     * Adds a JCheckBoxMenuItem entry to this menu. Because this is a conversion, the JCheckBoxMenuItem is no longer valid after this action.
     */
    public final
    Menu add(final JCheckBoxMenuItem entry) {
        add(new Checkbox(entry));
        return this;
    }

    /**
     * Adds a JMenuItem entry to this menu. Because this is a conversion, the JMenuItem is no longer valid after this action.
     */
    public final
    Menu add(final JMenuItem entry) {
        add(new MenuItem(entry));
        return this;
    }

    /**
     * Adds a JSeparator entry to this menu. Because this is a conversion, the JSeparator is no longer valid after this action.
     */
    public final
    Menu add(final JSeparator entry) {
        add(new Separator());
        return this;
    }

    /**
     * Adds a menu entry, separator, or sub-menu to this menu.
     */
    public
    <T extends Entry> T add(final T entry, final int index) {
        final int insertIndex;
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            if (index == -1) {
                insertIndex = menuEntries.size();
                menuEntries.add(entry);
            } else {
                if (!menuEntries.isEmpty() && menuEntries.get(0) instanceof Status) {
                    // the "status" menu entry is ALWAYS first
                    insertIndex = index+1;
                } else {
                    insertIndex = index;
                }

                menuEntries.add(index, entry);
            }
        }

        // all ADD/REMOVE events have to be queued on our own dispatch thread, so the execution order of the events can be maintained.
        EventDispatch.runLater(()->{
            EntryPeer finalPeer = peer;
            if (finalPeer != null) {
                ((MenuPeer) finalPeer).add(Menu.this, entry, insertIndex);
            }
        });

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
     * @return a copy of all of the current menu entries. It is safe to modify any of the entries in this list without concerning yourself with synchronize.
     */
    public
    List<Entry> getEntries() {
        synchronized (menuEntries) {
            // access on this object must be synchronized for object visibility
            return Collections.unmodifiableList(new ArrayList<>(menuEntries));
        }
    }


    /**
     * @return a copy of this menu as a swing JMenu, with all elements converted to their respective swing elements. Modifications to the elements of the new JMenu will not affect anything, as they are all copies
     */
    @Override
    public
    JMenu asSwingComponent() {
        JMenu jMenu = new JMenu();

        if (getImage() != null) {
            jMenu.setIcon(new ImageIcon(getImage().getAbsolutePath()));
        }
        jMenu.setText(getText());
        jMenu.setToolTipText(getTooltip());
        jMenu.setEnabled(getEnabled());
        jMenu.setMnemonic(SwingUtil.INSTANCE.getVirtualKey(getShortcut()));


        synchronized (menuEntries) {
            for (final Entry menuEntry : menuEntries) {
                if (menuEntry instanceof Menu) {
                    Menu entry = (Menu) menuEntry;
                    jMenu.add(entry.asSwingComponent());
                }
                else if (menuEntry instanceof Checkbox) {
                    Checkbox entry = (Checkbox) menuEntry;
                    jMenu.add(entry.asSwingComponent());
                }
                else if (menuEntry instanceof MenuItem) {
                    MenuItem entry = (MenuItem) menuEntry;
                    jMenu.add(entry.asSwingComponent());
                }
                else if (menuEntry instanceof Separator) {
                    Separator entry = (Separator) menuEntry;
                    jMenu.add(entry.asSwingComponent());
                }
                else if (menuEntry instanceof Status) {
                    Status entry = (Status) menuEntry;
                    jMenu.add(entry.asSwingComponent());
                }
            }
        }

        return jMenu;
    }

    /**
     * This removes a menu entry from the menu.
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
                final Entry reference = toRemove;
                // all ADD/REMOVE events have to be queued on our own dispatch thread, so the execution order of the events can be maintained.
                EventDispatch.runLater(()->reference.remove());

                toRemove = null;
            }


            // now check to see if a spacer is at the TOP of the list (and remove it if so. This is a recursive function.
            synchronized (menuEntries) {
                // access on this object must be synchronized for object visibility. When it runs recursively, it will correctly remove the entry.
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


            // now check to see if a spacer is at the BOTTOM of the list (and remove it if so. This is a recursive function.
            synchronized (menuEntries) {
                // access on this object must be synchronized for object visibility. When it runs recursively, it will correctly remove the entry.
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
     *  This removes all menu entries from this menu AND this menu from its parent
     */
    @Override
    public
    void remove() {
        synchronized (menuEntries) {
            for (final Entry entry : menuEntries) {
                entry.remove();
            }

            menuEntries.clear();
        }

        // all ADD/REMOVE events have to be queued on our own dispatch thread, so the execution order of the events can be maintained.
        EventDispatch.runLater(()->Menu.this.remove_());
    }

    private
    void remove_() {
        if (peer instanceof MenuPeer) {
            MenuPeer castPeer = (MenuPeer) peer;
            if (!castPeer.hasParent()) {
                // if we are the ROOT, then we don't want to remove ourselves!
                return;
            }
        }

        super.remove();
    }
}
