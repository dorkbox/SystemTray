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


import java.util.Iterator;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.SystemTray;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
@SuppressWarnings("ForLoopReplaceableByForEach")
public abstract
class MenuBase extends Menu {
//    /**
//     * Called in the EDT/GTK dispatch threads
//     *
//     * @param systemTray the system tray (which is the object that sits in the system tray)
//     * @param parent the parent of this menu, null if the parent is the system tray
//     */
//    public
//    MenuBase(final SystemTray systemTray, final Menu parent) {
//        setSystemTray(systemTray);
//        setParent(parent);
//    }

    protected abstract
    void dispatch(final Runnable runnable);

    protected abstract
    void dispatchAndWait(final Runnable runnable);














    /**
     * Will add a new menu entry
     * NOT ALWAYS CALLED ON DISPATCH
     */
//    protected abstract
//    Entry addEntry_(final String menuText, final File imagePath, final ActionListener callback);

    /**
     * Will add a new checkbox menu entry
     * NOT ALWAYS CALLED ON DISPATCH
     */
//    protected abstract
//    Checkbox addCheckbox_(final String menuText, final ActionListener callback);

    /**
     * Will add a new sub-menu entry
     * NOT ALWAYS CALLED ON DISPATCH
     */
//    protected abstract
//    Menu addMenu_(final String menuText, final File imagePath);


//    // public here so that Swing/Gtk/AppIndicator can override this
//    protected abstract
//    void setImage_(final File imageFile);











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


//    public final
//    Entry get(final String menuText) {
//        if (menuText == null || menuText.isEmpty()) {
//            return null;
//        }
//
//        // Must be wrapped in a synchronized block for object visibility
//        synchronized (menuEntries) {
//            for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
//                final Entry entry = menuEntries.get(i);
//
//                if (entry instanceof Separator || entry instanceof Status) {
//                    continue;
//                }
//
////                String text = entry.getText();
//
//                // text can be null
////                if (menuText.equals(text)) {
////                    return entry;
////                }
//            }
//        }
//
//        return null;
//    }





//    /**
//     *  This removes a menu entry from the dropdown menu.
//     *
//     * @param entry This is the menu entry to remove
//     */
//    @Override
//    public final
//    void remove(final Entry entry) {
//        if (entry == null) {
//            throw new NullPointerException("No menu entry exists for entry");
//        }
//
//        dispatchAndWait(new Runnable() {
//            @Override
//            public
//            void run() {
//                remove__(entry);
//            }
//        });
//    }
//
//    /**
//     *  This removes a sub-menu entry from the dropdown menu.
//     *
//     * @param menu This is the menu entry to remove
//     */
//    @Override
//    public final
//    void remove(final Menu menu) {
//        final Menu parent = getParent();
//        if (parent == null) {
//            // we are the system tray menu, so we just remove from ourselves
//            dispatchAndWait(new Runnable() {
//                @Override
//                public
//                void run() {
//                    remove__(menu);
//                }
//            });
//        } else {
//            final Menu _this = this;
//            // we are a submenu
//            dispatchAndWait(new Runnable() {
//                @Override
//                public
//                void run() {
//                    ((MenuBase) parent).remove__(_this);
//                }
//            });
//        }
//    }

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

//    /**
//     *  This removes a menu entry or sub-menu (via the text label) from the dropdown menu.
//     *
//     * @param menuText This is the label for the menu entry or sub-menu to remove
//     */
//    public final
//    void remove(final String menuText) {
//        dispatchAndWait(new Runnable() {
//            @Override
//            public
//            void run() {
//                synchronized (menuEntries) {
//                    Entry entry = get(menuText);
//
//                    if (entry != null) {
//                        remove(entry);
//                    }
//                }
//            }
//        });
//    }
//
//    @Override
//    public final
//    void removeAll() {
//        dispatch(new Runnable() {
//            @Override
//            public
//            void run() {
//                synchronized (menuEntries) {
//                    // have to make copy because we are deleting all of them, and sub-menus remove themselves from parents
//                    ArrayList<Entry> menuEntriesCopy = new ArrayList<Entry>(MenuBase.this.menuEntries);
//                    for (Entry entry : menuEntriesCopy) {
//                        entry.remove();
//                    }
//                    menuEntries.clear();
//                }
//            }
//        });
//    }
}
