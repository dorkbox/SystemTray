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


import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.MenuBase;
import dorkbox.systemTray.util.SystemTrayFixes;
import dorkbox.util.SwingUtil;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
@SuppressWarnings("ForLoopReplaceableByForEach")
class AwtMenu extends MenuBase implements NativeUI {

    // sub-menu = java.awt.Menu
    // systemtray = java.awt.PopupMenu
    volatile java.awt.Menu _native;

    // this have to be volatile, because they can be changed from any thread
    private volatile String text;

    /**
     * Called in the EDT
     *
     * @param systemTray the system tray (which is the object that sits in the system tray)
     * @param parent the parent of this menu, null if the parent is the system tray
     * @param _native the native element that represents this menu
     */
    AwtMenu(final SystemTray systemTray, final Menu parent, final java.awt.Menu _native) {
        super(systemTray, parent);
        this._native = _native;
    }

    @Override
    protected final
    void dispatch(final Runnable runnable) {
        // this will properly check if we are running on the EDT
        SwingUtil.invokeLater(runnable);
    }

    @Override
    protected final
    void dispatchAndWait(final Runnable runnable) {
        // this will properly check if we are running on the EDT
        try {
            SwingUtil.invokeAndWait(runnable);
        } catch (Exception e) {
            SystemTray.logger.error("Error processing event on the dispatch thread.", e);
        }
    }

    // always called in the EDT
    protected final
    void renderText(final String text) {
        _native.setLabel(text);
    }

    @Override
    public final
    String getText() {
        return text;
    }

    @Override
    public final
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

    /**
     * Will add a new menu entry
     * NOT ALWAYS CALLED ON EDT
     */
    protected final
    Entry addEntry_(final String menuText, final File imagePath, final ActionListener callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<Entry> value = new AtomicReference<Entry>();

        // must always be called on the EDT
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    Entry entry = entry = new AwtEntryItem(AwtMenu.this, callback);
                    entry.setText(menuText);
                    entry.setImage(imagePath);

                    menuEntries.add(entry);
                    value.set(entry);
                }
            }
        });

        return value.get();
    }

    /**
     * Will add a new checkbox menu entry
     * NOT ALWAYS CALLED ON DISPATCH
     */
    @Override
    protected
    Checkbox addCheckbox_(final String menuText, final ActionListener callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<Checkbox> value = new AtomicReference<Checkbox>();

        // must always be called on the EDT
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    Entry entry = new AwtEntryCheckbox(AwtMenu.this, callback);
                    entry.setText(menuText);

                    menuEntries.add(entry);
                    value.set((Checkbox) entry);
                }
            }
        });

        return value.get();
    }


    /**
     * Will add a new sub-menu entry
     * NOT ALWAYS CALLED ON EDT
     */
    protected final
    Menu addMenu_(final String menuText, final File imagePath) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<Menu> value = new AtomicReference<Menu>();

        // must always be called on the EDT
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    Entry entry = new AwtMenu(getSystemTray(), AwtMenu.this, new java.awt.Menu());
                    _native.add(((AwtMenu) entry)._native); // have to add it to our native item separately

                    entry.setText(menuText);
                    entry.setImage(imagePath);

                    menuEntries.add(entry);
                    value.set((Menu) entry);
                }
            }
        });

        return value.get();
    }



    // public here so that Swing/Gtk/AppIndicator can override this
    public
    void setImage_(final File imageFile) {
        // not supported!
    }

    // not supported!
    @Override
    public
    boolean hasImage() {
        return false;
    }

    // public here so that Swing/Gtk/AppIndicator can override this
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
    public final
    void addSeparator() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    synchronized (menuEntries) {
                        Entry entry = new AwtEntrySeparator(AwtMenu.this);
                        menuEntries.add(entry);
                    }
                }
            }
        });
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
//                    Entry entry = new SwingEntryWidget(SwingMenu.this, widget);
//                    value.set(entry);
//                    menuEntries.add(entry);
//                }
//            }
//        });
//
//        return value.get();
//    }


    // public here so that Swing/Gtk/AppIndicator can access this
    public final
    void setStatus(final String statusText) {
        final AwtMenu _this = this;
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    // status is ALWAYS at 0 index...
                    AwtEntry menuEntry = null;
                    if (!menuEntries.isEmpty()) {
                        menuEntry = (AwtEntry) menuEntries.get(0);
                    }

                    if (menuEntry instanceof Status) {
                        // set the text or delete...

                        if (statusText == null) {
                            // delete
                            remove(menuEntry);
                        }
                        else {
                            // set text
                            menuEntry.setText(statusText);
                        }

                    } else {
                        // create a new one
                        menuEntry = new AwtEntryStatus(_this, statusText);
                        // status is ALWAYS at 0 index...
                        menuEntries.add(0, menuEntry);
                    }
                }
            }
        });
    }

    @Override
    public final
    void setShortcut(final char key) {
        if (!(_native instanceof PopupMenu)) {
            // yikes...
            final int vKey = SystemTrayFixes.getVirtualKey(key);

            dispatch(new Runnable() {
                @Override
                public
                void run() {
                    _native.setShortcut(new MenuShortcut(vKey));
                }
            });
        }
    }

    @Override
    public final
    void remove() {
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                AwtMenu parent = (AwtMenu) getParent();
                if (parent != null) {
                    parent._native.remove(_native);
                }
            }
        });
    }
}
