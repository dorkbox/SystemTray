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


import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;

import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuEntry;
import dorkbox.systemTray.SystemTray;
import dorkbox.systemTray.SystemTrayMenuAction;
import dorkbox.systemTray.util.ImageUtils;
import dorkbox.util.OS;
import dorkbox.util.SwingUtil;

// this is a weird composite class, because it must be a Menu, but ALSO a MenuEntry -- so it has both
public
class SwingMenu extends Menu implements MenuEntry {

    volatile JComponent _native;

    // this have to be volatile, because they can be changed from any thread
    private volatile String text;
    private volatile boolean hasLegitIcon = false;

    /**
     * @param systemTray
     *                 the system tray (which is the object that sits in the system tray)
     * @param parent
     *                 the parent of this menu, null if the parent is the system tray
     */
    public
    SwingMenu(final SystemTray systemTray, final Menu parent) {
        super(systemTray, parent);

        try {
            SwingUtil.invokeAndWait(new Runnable() {
                @Override
                public
                void run() {
                    if (parent != null) {
                        if (OS.isWindows()) {
                            _native = new AdjustedJMenu(null);
                        }
                        else {
                            _native = new AdjustedJMenu((SwingSystemTrayMenuPopup)((SwingMenu) systemTray.getMenu())._native);
                        }

                        ((SwingMenu) parent)._native.add(_native);
                    } else {
                        // when we are the system tray
                        if (OS.isWindows()) {
                            _native = new SwingSystemTrayMenuWindowsPopup();
                        } else {
                            _native = new SwingSystemTrayMenuPopup();
                        }
                    }
                }
            });
        } catch (Exception e) {
            SystemTray.logger.error("Error processing event on the dispatch thread.", e);
        }
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


    @Override
    public
    void addSeparator() {
        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    synchronized (menuEntries) {
                        MenuEntry menuEntry = new SwingEntrySeparator(SwingMenu.this);
                        menuEntries.add(menuEntry);
                    }
                }
            }
        });
    }

    /**
     * Enables, or disables the sub-menu entry.
     */
    @Override
    public
    void setEnabled(final boolean enabled) {
        _native.setEnabled(enabled);
    }

    /**
     * Will add a new menu entry, or update one if it already exists
     */
    protected
    MenuEntry addEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback) {
        if (menuText == null) {
            throw new NullPointerException("Menu text cannot be null");
        }

        final AtomicReference<MenuEntry> value = new AtomicReference<MenuEntry>();

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = get(menuText);

                    if (menuEntry == null) {
                        // must always be called on the EDT
                        menuEntry = new SwingEntryItem(SwingMenu.this, callback);
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);

                        menuEntries.add(menuEntry);
                    } else if (menuEntry instanceof SwingEntryItem) {
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
     * Will add a new sub-menu entry, or update one if it already exists
     */
    protected
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
                    MenuEntry menuEntry = get(menuText);

                    if (menuEntry == null) {
                        // must always be called on the EDT
                        menuEntry = new SwingMenu(getSystemTray(), SwingMenu.this);
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);
                        value.set((Menu)menuEntry);

                    } else if (menuEntry instanceof SwingMenu) {
                        menuEntry.setText(menuText);
                        menuEntry.setImage(imagePath);
                    }

                    menuEntries.add(menuEntry);
                }
            }
        });

        return value.get();
    }



    // always called in the EDT
    void renderText(final String text) {
        ((JMenuItem) _native).setText(text);
    }

    @SuppressWarnings("Duplicates")
    void setImage_(final File imageFile) {
        hasLegitIcon = imageFile != null;

        SwingUtil.invokeLater(new Runnable() {
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
    String getText() {
        return text;
    }

    @Override
    public
    void setText(final String newText) {
        text = newText;
        SwingUtil.invokeLater(new Runnable() {
            @Override
            public
            void run() {
                renderText(newText);
            }
        });
    }

    @Override
    public
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
    boolean hasImage() {
        return hasLegitIcon;
    }

    @Override
    public
    void setCallback(final SystemTrayMenuAction callback) {
    }

    @Override
    public final
    void remove() {
        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                _native.setVisible(false);
                if (_native instanceof SwingSystemTrayMenuWindowsPopup) {
                    ((SwingSystemTrayMenuWindowsPopup) _native).close();
                }
                else if (_native instanceof SwingSystemTrayMenuPopup) {
                    ((SwingSystemTrayMenuPopup) _native).close();
                }

                SwingMenu parent = (SwingMenu) getParent();
                if (parent != null) {
                    parent._native.remove(_native);
                }
            }
        });
    }
}
