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
package dorkbox.systemTray.swingUI;


import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;

import dorkbox.systemTray.Action;
import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.Status;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.util.MenuBase;
import dorkbox.systemTray.util.SystemTrayFixes;
import dorkbox.util.SwingUtil;

// this is a weird composite class, because it must be a Menu, but ALSO a Entry -- so it has both
@SuppressWarnings("ForLoopReplaceableByForEach")
class SwingMenu extends MenuBase implements SwingUI {

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
    SwingMenu(final SystemTray systemTray, final Menu parent, final JComponent _native) {
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
        ((JMenuItem) _native).setText(text);
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
     * Will add a new menu entry, or update one if it already exists
     * NOT ALWAYS CALLED ON EDT
     */
    protected final
    Entry addEntry_(final String menuText, final File imagePath, final Action callback) {
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
                        entry = new SwingEntryItem(SwingMenu.this, callback);
                        entry.setText(menuText);
                        entry.setImage(imagePath);

                        menuEntries.add(entry);
                    } else if (entry instanceof SwingEntryItem) {
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
    protected final
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
                        entry = new SwingMenu(getSystemTray(), SwingMenu.this, new AdjustedJMenu());
                        _native.add(((SwingMenu) entry)._native); // have to add it separately

                        entry.setText(menuText);
                        entry.setImage(imagePath);
                        value.set((Menu) entry);

                    } else if (entry instanceof SwingMenu) {
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







    @Override
    public
    boolean hasImage() {
        return hasLegitIcon;
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
                        Entry entry = new SwingEntrySeparator(SwingMenu.this);
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
        final SwingMenu _this = this;
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    // status is ALWAYS at 0 index...
                    SwingEntry menuEntry = null;
                    if (!menuEntries.isEmpty()) {
                        menuEntry = (SwingEntry) menuEntries.get(0);
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
                        menuEntry = new SwingEntryStatus(_this, statusText);
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
        if (_native instanceof JMenuItem) {
            // yikes...
            final int vKey = SystemTrayFixes.getVirtualKey(key);
            dispatch(new Runnable() {
                @Override
                public
                void run() {
                    ((JMenuItem) _native).setMnemonic(vKey);
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
                _native.setVisible(false);
                if (_native instanceof TrayPopup) {
                    ((TrayPopup) _native).close();
                }

                SwingMenu parent = (SwingMenu) getParent();
                if (parent != null) {
                    parent._native.remove(_native);
                }
            }
        });
    }
}
