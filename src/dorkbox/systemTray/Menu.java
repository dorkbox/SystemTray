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

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import dorkbox.systemTray.util.ImageUtils;

/**
 * Represents a cross-platform menu that is displayed by the tray-icon or as a sub-menu
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract
class Menu {
    public static final AtomicInteger MENU_ID_COUNTER = new AtomicInteger();
    private final int id = Menu.MENU_ID_COUNTER.getAndIncrement();

    protected final java.util.List<MenuEntry> menuEntries = new ArrayList<MenuEntry>();

    private final SystemTray systemTray;
    private final Menu parent;

    /**
     * @param systemTray the system tray (which is the object that sits in the system tray)
     * @param parent the parent of this menu, null if the parent is the system tray
     */
    public
    Menu(final SystemTray systemTray, final Menu parent) {
        this.systemTray = systemTray;
        this.parent = parent;
    }

    /**
     * @return the parent menu (of this menu) or null if we are the root menu
     */
    public
    Menu getParent() {
        return parent;
    }

    /**
     * @return the system tray that this menu is ultimately attached to
     */
    public
    SystemTray getSystemTray() {
        return systemTray;
    }

    /**
     * Adds a spacer to the dropdown menu. When menu entries are removed, any menu spacer that ends up at the top/bottom of the drop-down
     * menu, will also be removed. For example:
     *
     * Original     Entry3 deleted     Result
     *
     * <Status>         <Status>       <Status>
     * Entry1           Entry1         Entry1
     * Entry2      ->   Entry2    ->   Entry2
     * <Spacer>         (deleted)
     * Entry3           (deleted)
     */
    public abstract
    void addSeparator();

    /*
     * Will add a new menu entry, or update one if it already exists
     */
    protected abstract
    MenuEntry addEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback);

    /*
     * Will add a new sub-menu entry, or update one if it already exists
     */
    protected abstract
    Menu addMenu_(final String menuText, final File imagePath);

    /*
     * Called when this menu is removed from it's parent menu
     */
    protected abstract
    void removePrivate();

    /*
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    protected abstract
    void dispatch(Runnable runnable);

    /*
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    protected abstract
    void dispatchAndWait(Runnable runnable);

    /**
     * Enables, or disables the sub-menu entry.
     */
    public abstract
    void setEnabled(final boolean enabled);


    /**
     * Gets the menu entry for a specified text
     *
     * @param menuText the menu entry text to use to find the menu entry. The first result found is returned
     */
    public
    MenuEntry get(final String menuText) {
        if (menuText == null || menuText.isEmpty()) {
            return null;
        }

        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            for (MenuEntry entry : menuEntries) {
                String text = entry.getText();

                // text can be null
                if (menuText.equals(text)) {
                    return entry;
                }
            }
        }

        return null;
    }

    /**
     * Gets the first menu entry or sub-menu, ignoring status and spacers
     */
    public
    MenuEntry getFirst() {
        return get(0);
    }

    /**
     * Gets the last menu entry or sub-menu, ignoring status and spacers
     */
    public
    MenuEntry getLast() {
        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            if (!menuEntries.isEmpty()) {
                MenuEntry menuEntry = null;
                for (int i = 0, menuEntriesSize = menuEntries.size(); i < menuEntriesSize; i++) {
                    menuEntry = menuEntries.get(i);
                }

                if (!(menuEntry instanceof MenuSpacer || menuEntry instanceof MenuStatus)) {
                    return menuEntry;
                }
            }
        }

        return null;
    }

    /**
     * Gets the menu entry or sub-menu for a specified index (zero-index), ignoring status and spacers
     *
     * @param menuIndex the menu entry index to use to retrieve the menu entry.
     */
    public
    MenuEntry get(final int menuIndex) {
        if (menuIndex < 0) {
            return null;
        }

        // Must be wrapped in a synchronized block for object visibility
        synchronized (menuEntries) {
            if (!menuEntries.isEmpty()) {
                int count = 0;
                for (MenuEntry menuEntry : menuEntries) {
                    if (menuEntry instanceof MenuSpacer || menuEntry instanceof MenuStatus) {
                        continue;
                    }

                    if (count == menuIndex) {
                        return menuEntry;
                    }

                    count++;
                }
            }
        }

        return null;
    }



    /**
     * Adds a menu entry with text (no image)
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    MenuEntry addEntry(String menuText, SystemTrayMenuAction callback) {
        return addEntry(menuText, (String) null, callback);
    }

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    MenuEntry addEntry(String menuText, String imagePath, SystemTrayMenuAction callback) {
        if (imagePath == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath), callback);
        }
    }

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    MenuEntry addEntry(String menuText, URL imageUrl, SystemTrayMenuAction callback) {
        if (imageUrl == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl), callback);
        }
    }

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    MenuEntry addEntry(String menuText, String cacheName, InputStream imageStream, SystemTrayMenuAction callback) {
        if (imageStream == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream), callback);
        }
    }

    /**
     * Adds a menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    MenuEntry addEntry(String menuText, InputStream imageStream, SystemTrayMenuAction callback) {
        if (imageStream == null) {
            return addEntry_(menuText, null, callback);
        }
        else {
            return addEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream), callback);
        }
    }




    /**
     * Adds a sub-menu entry with text (no image)
     *
     * @param menuText string of the text you want to appear
     */
    public
    Menu addMenu(String menuText) {
        return addMenu(menuText, (String) null);
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, String imagePath) {
        if (imagePath == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath));
        }
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, URL imageUrl) {
        if (imageUrl == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl));
        }
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, String cacheName, InputStream imageStream) {
        if (imageStream == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream));
        }
    }

    /**
     * Adds a sub-menu entry with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     */
    public
    Menu addMenu(String menuText, InputStream imageStream) {
        if (imageStream == null) {
            return addMenu_(menuText, null);
        }
        else {
            return addMenu_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream));
        }
    }


    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param menuEntry This is the menu entry to remove
     */
    public
    void remove(final MenuEntry menuEntry) {
        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for menuEntry");
        }

        dispatchAndWait(new Runnable() {
            @Override
            public
            void run() {
                try {
                    synchronized (menuEntries) {
                        for (Iterator<MenuEntry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
                            final MenuEntry entry = iterator.next();
                            if (entry == menuEntry) {
                                iterator.remove();

                                // this will also reset the menu
                                menuEntry.remove();
                                break;
                            }
                        }

                        // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                        if (!menuEntries.isEmpty()) {
                            if (menuEntries.get(0) instanceof MenuSpacer) {
                                remove(menuEntries.get(0));
                            }
                        }
                        // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                        if (!menuEntries.isEmpty()) {
                            if (menuEntries.get(menuEntries.size()-1) instanceof MenuSpacer) {
                                remove(menuEntries.get(menuEntries.size() - 1));
                            }
                        }
                    }
                } catch (Exception e) {
                    SystemTray.logger.error("Error removing menu entry from list.", e);
                }
            }
        });
    }

    /**
     *  This removes a sub-menu entry from the dropdown menu.
     *
     * @param menu This is the menu entry to remove
     */
    @SuppressWarnings("Duplicates")
    public
    void remove(final Menu menu) {
        if (menu == null) {
            throw new NullPointerException("No menu entry exists for menuEntry");
        }

        dispatchAndWait(new Runnable() {
            @SuppressWarnings("Duplicates")
            @Override
            public
            void run() {
                try {
                    synchronized (menuEntries) {
                        for (Iterator<MenuEntry> iterator = menuEntries.iterator(); iterator.hasNext(); ) {
                            final MenuEntry entry = iterator.next();
                            if (entry == menu) {
                                iterator.remove();

                                // this will also reset the menu
                                menu.removePrivate();
                                break;
                            }
                        }

                        // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                        if (!menuEntries.isEmpty()) {
                            if (menuEntries.get(0) instanceof MenuSpacer) {
                                remove(menuEntries.get(0));
                            }
                        }
                        // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                        if (!menuEntries.isEmpty()) {
                            if (menuEntries.get(menuEntries.size()-1) instanceof MenuSpacer) {
                                remove(menuEntries.get(menuEntries.size() - 1));
                            }
                        }
                    }
                } catch (Exception e) {
                    SystemTray.logger.error("Error removing menu entry from list.", e);
                }
            }
        });
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
                    MenuEntry menuEntry = get(menuText);

                    if (menuEntry != null) {
                        remove(menuEntry);
                    }
                }
            }
        });
    }

    /**
     *  This removes the sub-menu entry from the dropdown menu.
     */
    public
    void remove() {
        getParent().remove(this);
    }
}
