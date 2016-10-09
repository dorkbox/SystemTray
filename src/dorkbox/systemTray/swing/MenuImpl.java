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
package dorkbox.systemTray.swing;


import static dorkbox.systemTray.swing.EntryImpl.getVkKey;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.util.SwingUtil;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
class MenuImpl implements Menu {
    public static final AtomicInteger MENU_ID_COUNTER = new AtomicInteger();
    private final int id = MenuImpl.MENU_ID_COUNTER.getAndIncrement();

    protected final java.util.List<Entry> menuEntries = new ArrayList<Entry>();

    private final SystemTray systemTray;
    private final Menu parent;

    // sub-menu = AdjustedJMenu
    // systemtray = TrayPopup
    volatile JComponent _native;

    // this have to be volatile, because they can be changed from any thread
    private volatile String text;
    private volatile boolean hasLegitIcon = false;

    /**
     * Called in the EDT
     *
     * @param systemTray the system tray (which is the object that sits in the system tray)
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param _native the native element that represents this menu
     */
    MenuImpl(final SystemTray systemTray, final Menu parent, final JComponent _native) {
        this.systemTray = systemTray;
        this.parent = parent;
        this._native = _native;
    }

    protected
    void dispatch(final Runnable runnable) {
        // this will properly check if we are running on the EDT
        SwingUtil.invokeLater(runnable);
    }

    protected
    void dispatchAndWait(final Runnable runnable) {
        // this will properly check if we are running on the EDT
        try {
            SwingUtil.invokeAndWait(runnable);
        } catch (Exception e) {
            SystemTray.logger.error("Error processing event on the dispatch thread.", e);
        }
    }

    // always called in the EDT
    private
    void renderText(final String text) {
        ((JMenuItem) _native).setText(text);
    }



    /**
     * Will add a new menu entry, or update one if it already exists
     * NOT ALWAYS CALLED ON EDT
     */
    private
    Entry addEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<Entry> value = new AtomicReference<Entry>();

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    Entry entry = get(menuText);

                    if (entry == null) {
                        // must always be called on the EDT
                        entry = new EntryItem(MenuImpl.this, callback);
                        entry.setText(menuText);
                        entry.setImage(imagePath);

                        menuEntries.add(entry);
                    } else if (entry instanceof EntryItem) {
                        entry.setText(menuText);
                        entry.setImage(imagePath);
                    }

                    value.set(entry);
                }
            }
        });

        return value.get();
    }

    /**
     * Will add a new sub-menu entry, or update one if it already exists
     * NOT ALWAYS CALLED ON EDT
     */
    private
    Menu addMenu_(final String menuText, final File imagePath) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<Menu> value = new AtomicReference<Menu>();

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    Entry entry = get(menuText);

                    if (entry == null) {
                        // must always be called on the EDT
                        entry = new MenuImpl(getSystemTray(), MenuImpl.this, new AdjustedJMenu());
                        _native.add(((MenuImpl) entry)._native);

                        entry.setText(menuText);
                        entry.setImage(imagePath);
                        value.set((Menu) entry);

                    } else if (entry instanceof MenuImpl) {
                        entry.setText(menuText);
                        entry.setImage(imagePath);
                    }

                    menuEntries.add(entry);
                }
            }
        });

        return value.get();
    }



    // public here so that Swing/Gtk/AppIndicator can override this
    public
    void setImage_(final File imageFile) {
        hasLegitIcon = imageFile != null;

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                if (imageFile != null) {
                    ImageIcon origIcon = new ImageIcon(imageFile.getAbsolutePath());
                    ((JMenuItem) _native).setIcon(origIcon);
                }
                else {
                    ((JMenuItem) _native).setIcon(null);
                }
            }
        });
    }









    public
    Menu getParent() {
        return parent;
    }

    public
    SystemTray getSystemTray() {
        return systemTray;
    }


    @Override
    public
    boolean hasImage() {
        return hasLegitIcon;
    }

    /**
     * Enables, or disables the sub-menu entry.
     */
    @Override
    public
    void setEnabled(final boolean enabled) {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                _native.setEnabled(enabled);
            }
        });
    }


    /**
     * NOT ALWAYS CALLED ON EDT
     */
    @Override
    public
    void addSeparator() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    synchronized (menuEntries) {
                        Entry entry = new EntrySeparator(MenuImpl.this);
                        menuEntries.add(entry);
                    }
                }
            }
        });
    }


    public
    Entry get(final String menuText) {
        if (menuText == null || menuText.isEmpty()) {
            return null;
        }

        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            for (Entry entry : menuEntries) {
                String text = entry.getText();

                // text can be null
                if (menuText.equals(text)) {
                    return entry;
                }
            }
        }

        return null;
    }

    public
    Entry getFirst() {
        return get(0);
    }

    public
    Entry getLast() {
        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            if (!menuEntries.isEmpty()) {
                Entry entry = null;
                for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                    entry = menuEntries.get(i);
                }

                if (!(entry instanceof dorkbox.systemTray.Separator || entry instanceof Status)) {
                    return entry;
                }
            }
        }

        return null;
    }

    public
    Entry get(final int menuIndex) {
        if (menuIndex < 0) {
            return null;
        }

        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            if (!menuEntries.isEmpty()) {
                int count = 0;
                for (Entry entry : menuEntries) {
                    if (entry instanceof dorkbox.systemTray.Separator || entry instanceof Status) {
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


    public
    Entry addEntry(String menuText, SystemTrayMenuAction callback) {
        return addEntry(menuText, (String) null, callback);
    }

    public
    Entry addEntry(String menuText, String imagePath, SystemTrayMenuAction callback) {
        if (imagePath == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath), callback);
        }
    }

    public
    Entry addEntry(String menuText, URL imageUrl, SystemTrayMenuAction callback) {
        if (imageUrl == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl), callback);
        }
    }

    public
    Entry addEntry(String menuText, String cacheName, InputStream imageStream, SystemTrayMenuAction callback) {
        if (imageStream == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream), callback);
        }
    }

    public
    Entry addEntry(String menuText, InputStream imageStream, SystemTrayMenuAction callback) {
        if (imageStream == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream), callback);
        }
    }






    public
    Menu addMenu(String menuText) {
        return addMenu(menuText, (String) null);
    }

    public
    Menu addMenu(String menuText, String imagePath) {
        if (imagePath == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath));
        }
    }

    public
    Menu addMenu(String menuText, URL imageUrl) {
        if (imageUrl == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl));
        }
    }

    public
    Menu addMenu(String menuText, String cacheName, InputStream imageStream) {
        if (imageStream == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream));
        }
    }

    public
    Menu addMenu(String menuText, InputStream imageStream) {
        if (imageStream == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream));
        }
    }




    @Override
    public final
    void setImage(final File imageFile) {
        if (imageFile == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageFile));
        }
    }

    @Override
    public final
    void setImage(final String imagePath) {
        if (imagePath == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath));
        }
    }

    @Override
    public final
    void setImage(final URL imageUrl) {
        if (imageUrl == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl));
        }
    }

    @Override
    public final
    void setImage(final String cacheName, final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream));
        }
    }

    @Override
    public final
    void setImage(final InputStream imageStream) {
        if (imageStream == null) {
            setImage_(null);
        }
        else {
            setImage_(ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream));
        }
    }




























    @Override
    public
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        text = newText;
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                renderText(newText);
            }
        });
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
    }

    @Override
    public
    void setShortcut(final char key) {
        if (_native instanceof JMenuItem) {
            // yikes...
            final int vKey = getVkKey(key);
            dispatch(new Runnable() {
                @Override
                public
                void run() {
                    ((JMenuItem) _native).setMnemonic(vKey);
                }
            });
        }
    }





















    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param entry This is the menu entry to remove
     */
    public
    void remove(final Entry entry) {
        if (entry == null) {
            throw new NullPointerException("No menu entry exists for entry");
        }

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                remove__(entry);
            }
        });
    }

    /**
     *  This removes a sub-menu entry from the dropdown menu.
     *
     * @param menu This is the menu entry to remove
     */
    @Override
    public
    void remove(final Menu menu) {
        final Menu parent = getParent();
        if (parent == null) {
            // we are the system tray menu, so we just remove from ourselves
            dispatchAndWait(new Runnable() {
                @Override
                public
                void run() {
                    remove__(menu);
                }
            });
        } else {
            final Menu _this = this;
            // we are a submenu
            dispatchAndWait(new Runnable() {
                @Override
                public
                void run() {
                    ((MenuImpl) parent).remove__(_this);
                }
            });
        }
    }

    // NOT ALWAYS CALLED ON EDT
    private
    void remove__(final Object menuEntry) {
        try {
            synchronized (menuEntries) {
                // null is passed in when a sub-menu is removing itself from us (because they have already called "remove" and have also
                // removed themselves from the menuEntries)
                if (menuEntry != null) {
                    for (Iterator<Entry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
                        final Entry entry = iterator.next();
                        if (entry == menuEntry) {
                            iterator.remove();
                            entry.remove();
                            break;
                        }
                    }
                }

                // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                if (!menuEntries.isEmpty()) {
                    if (menuEntries.get(0) instanceof dorkbox.systemTray.Separator) {
                        remove(menuEntries.get(0));
                    }
                }
                // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                if (!menuEntries.isEmpty()) {
                    if (menuEntries.get(menuEntries.size()-1) instanceof dorkbox.systemTray.Separator) {
                        remove(menuEntries.get(menuEntries.size() - 1));
                    }
                }
            }
        } catch (Exception e) {
            SystemTray.logger.error("Error removing entry from menu.", e);
        }
    }

    /**
     *  This removes a menu entry or sub-menu (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry or sub-menu to remove
     */
    public
    void remove(final String menuText) {
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    Entry entry = get(menuText);

                    if (entry != null) {
                        remove(entry);
                    }
                }
            }
        });
    }


    @Override
    public final
    void remove() {
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                _native.setVisible(false);
                if (_native instanceof TrayPopup) {
                    ((TrayPopup) _native).close();
                }

                MenuImpl parent = (MenuImpl) getParent();
                if (parent != null) {
                    parent._native.remove(_native);
                }
            }
        });
    }

    @Override
    public
    void clear() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    // have to make copy because we are deleting all of them, and sub-menus remove themselves from parents
                    ArrayList<Entry> menuEntriesCopy = new ArrayList<Entry>(MenuImpl.this.menuEntries);
                    for (Entry entry : menuEntriesCopy) {
                        entry.remove();
                    }
                }
            }
        });
    }
}
