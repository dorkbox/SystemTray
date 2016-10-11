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
package dorkbox.systemTray.util;


import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import dorkbox.systemTray.Action;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract
class MenuBase implements Menu {
    public static final AtomicInteger MENU_ID_COUNTER = new AtomicInteger();
    private final int id = MenuBase.MENU_ID_COUNTER.getAndIncrement();

    protected final java.util.List<Entry> menuEntries = new ArrayList<Entry>();

    private final SystemTray systemTray;
    private final Menu parent;

    /**
     * Called in the EDT/GTK dispatch threads
     *
     * @param systemTray the system tray (which is the object that sits in the system tray)
     * @param parent the parent of this menu, null if the parent is the system tray
     */
    public
    MenuBase(final SystemTray systemTray, final Menu parent) {
        this.systemTray = systemTray;
        this.parent = parent;
    }

    protected abstract
    void dispatch(final Runnable runnable);

    protected abstract
    void dispatchAndWait(final Runnable runnable);


    /**
     * Will add a new menu entry, or update one if it already exists
     * NOT ALWAYS CALLED ON DISPATCH
     */
    protected abstract
    Entry addEntry_(final String menuText, final File imagePath, final Action callback);

    /**
     * Will add a new sub-menu entry, or update one if it already exists
     * NOT ALWAYS CALLED ON DISPATCH
     */
    protected abstract
    Menu addMenu_(final String menuText, final File imagePath);


    // public here so that Swing/Gtk/AppIndicator can override this
    protected abstract
    void setImage_(final File imageFile);








    @Override
    public final
    Menu getParent() {
        return parent;
    }

    @Override
    public final
    SystemTray getSystemTray() {
        return systemTray;
    }

    // public here so that Swing/Gtk/AppIndicator can access this
    public final
    String getStatus() {
        synchronized (menuEntries) {
            Entry entry = menuEntries.get(0);
            if (entry instanceof Status) {
                return entry.getText();
            }
        }

        return null;
    }

// TODO: buggy. The menu will **sometimes** stop responding to the "enter" key after this. Mnemonics still work however.
//    public
//    Entry addWidget(final JComponent widget) {
//        if (widget == null) {
//            throw new NullPointerException("Widget cannot be null");
//        }
//
//        final AtomicReference<Entry> value = new AtomicReference<Entry>();
//
//        dispatchAndWait(new Runnable() {
//            @Override
//            public
//            void run() {
//                synchronized (menuEntries) {
//                    // must always be called on the EDT
//                    Entry entry = new EntryWidget(MenuImpl.this, widget);
//                    value.set(entry);
//                    menuEntries.add(entry);
//                }
//            }
//        });
//
//        return value.get();
//    }


    @Override
    public final
    Entry get(final String menuText) {
        if (menuText == null || menuText.isEmpty()) {
            return null;
        }

        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                final Entry entry = menuEntries.get(i);

                if (entry instanceof Separator || entry instanceof Status) {
                    continue;
                }

                String text = entry.getText();

                // text can be null
                if (menuText.equals(text)) {
                    return entry;
                }
            }
        }

        return null;
    }

    // ignores status + separators
    @Override
    public final
    Entry getFirst() {
        return get(0);
    }

    // ignores status + separators
    @Override
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

    // ignores status + separators
    @Override
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

    @Override
    public final
    Entry addEntry(String menuText, Action callback) {
        return addEntry(menuText, (String) null, callback);
    }

    @Override
    public final
    Entry addEntry(String menuText, String imagePath, Action callback) {
        if (imagePath == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath), callback);
        }
    }

    @Override
    public final
    Entry addEntry(String menuText, URL imageUrl, Action callback) {
        if (imageUrl == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl), callback);
        }
    }

    @Override
    public final
    Entry addEntry(String menuText, String cacheName, InputStream imageStream, Action callback) {
        if (imageStream == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream), callback);
        }
    }

    @Override
    public final
    Entry addEntry(String menuText, InputStream imageStream, Action callback) {
        if (imageStream == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream), callback);
        }
    }





    @Override
    public final
    Menu addMenu(String menuText) {
        return addMenu(menuText, (String) null);
    }

    @Override
    public final
    Menu addMenu(String menuText, String imagePath) {
        if (imagePath == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath));
        }
    }

    @Override
    public final
    Menu addMenu(String menuText, URL imageUrl) {
        if (imageUrl == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl));
        }
    }

    @Override
    public final
    Menu addMenu(String menuText, String cacheName, InputStream imageStream) {
        if (imageStream == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream));
        }
    }

    @Override
    public final
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
    public final
    void setCallback(final Action callback) {
    }























    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param entry This is the menu entry to remove
     */
    @Override
    public final
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
    public final
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
                    ((MenuBase) parent).remove__(_this);
                }
            });
        }
    }

    // NOT ALWAYS CALLED ON EDT
    protected
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
    public final
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


//    @Override
//    public final
//    void remove() {
//        dispatchAndWait(new Runnable() {
//            @Override
//            public
//            void run() {
//                _native.setVisible(false);
//                if (_native instanceof TrayPopup) {
//                    ((TrayPopup) _native).close();
//                }
//
//                MenuBase parent = (MenuBase) getParent();
//                if (parent != null) {
//                    parent._native.remove(_native);
//                }
//            }
//        });
//    }

    @Override
    public final
    void removeAll() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    // have to make copy because we are deleting all of them, and sub-menus remove themselves from parents
                    ArrayList<Entry> menuEntriesCopy = new ArrayList<Entry>(MenuBase.this.menuEntries);
                    for (Entry entry : menuEntriesCopy) {
                        entry.remove();
                    }
                    menuEntries.clear();
                }
            }
        });
    }

    @Override
    public final
    int hashCode() {
        return id;
    }


    @Override
    public final
    boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        MenuBase other = (MenuBase) obj;
        return this.id == other.id;
    }
}
