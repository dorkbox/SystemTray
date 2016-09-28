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

import static dorkbox.systemTray.SystemTray.TIMEOUT;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dorkbox.systemTray.util.ImageUtils;

/**
 * Represents a cross-platform menu that is displayed by the tray-icon or as a sub-menu
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public
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
    public
    void addMenuSpacer() {
    }

    /*
     * Will add a new menu entry, or update one if it already exists
     */
    protected
    void addMenuEntry_(final String menuText, final File imagePath, final SystemTrayMenuAction callback) {
    }

    /*
     * Necessary to guarantee all updates occur on the dispatch thread
     */
    protected
    void dispatch(Runnable runnable) {
    }

    /**
     * Gets the menu entry for a specified text
     *
     * @param menuText the menu entry text to use to find the menu entry. The first result found is returned
     */
    public
    MenuEntry getMenuEntry(final String menuText) {
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
     * Gets the first menu entry, ignoring status and spacers
     */
    public
    MenuEntry getFirstMenuEntry() {
        return getMenuEntry(0);
    }

    /**
     * Gets the last menu entry, ignoring status and spacers
     */
    public
    MenuEntry getLastMenuEntry() {
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
     * Gets the menu entry for a specified index (zero-index), ignoring status and spacers
     *
     * @param menuIndex the menu entry index to use to retrieve the menu entry.
     */
    public
    MenuEntry getMenuEntry(final int menuIndex) {
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
     * Adds a menu entry to the tray icon with text (no image)
     *
     * @param menuText string of the text you want to appear
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    void addMenuEntry(String menuText, SystemTrayMenuAction callback) {
        addMenuEntry(menuText, (String) null, callback);
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imagePath the image (full path required) to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    void addMenuEntry(String menuText, String imagePath, SystemTrayMenuAction callback) {
        if (imagePath == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imagePath), callback);
        }
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageUrl the URL of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    void addMenuEntry(String menuText, URL imageUrl, SystemTrayMenuAction callback) {
        if (imageUrl == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageUrl), callback);
        }
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param cacheName @param cacheName the name to use for lookup in the cache for the imageStream
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    void addMenuEntry(String menuText, String cacheName, InputStream imageStream, SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, cacheName, imageStream), callback);
        }
    }

    /**
     * Adds a menu entry to the tray icon with text + image
     *
     * @param menuText string of the text you want to appear
     * @param imageStream the InputStream of the image to use. If null, no image will be used
     * @param callback callback that will be executed when this menu entry is clicked
     */
    public
    void addMenuEntry(String menuText, InputStream imageStream, SystemTrayMenuAction callback) {
        if (imageStream == null) {
            addMenuEntry_(menuText, null, callback);
        }
        else {
            addMenuEntry_(menuText, ImageUtils.resizeAndCache(ImageUtils.ENTRY_SIZE, imageStream), callback);
        }
    }

    /**
     *  This removes a menu entry from the dropdown menu.
     *
     * @param menuEntry This is the menu entry to remove
     */
    public
    void removeMenuEntry(final MenuEntry menuEntry) {
        if (menuEntry == null) {
            throw new NullPointerException("No menu entry exists for menuEntry");
        }

        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue = new AtomicBoolean(false);

        dispatch(new Runnable() {
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
                                hasValue.set(true);
                                break;
                            }
                        }

                        // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                        if (!menuEntries.isEmpty()) {
                            if (menuEntries.get(0) instanceof MenuSpacer) {
                                removeMenuEntry(menuEntries.get(0));
                            }
                        }
                        // now check to see if a spacer is at the top/bottom of the list (and remove it if so. This is a recursive function.
                        if (!menuEntries.isEmpty()) {
                            if (menuEntries.get(menuEntries.size()-1) instanceof MenuSpacer) {
                                removeMenuEntry(menuEntries.get(menuEntries.size()-1));
                            }
                        }
                    }
                } catch (Exception e) {
                    SystemTray.logger.error("Error removing menu entry from list.", e);
                } finally {
                    countDownLatch.countDown();
                }
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            SystemTray.logger.error("Error removing menu entry: {}", menuEntry.getText());
        }

        if (!hasValue.get()) {
            throw new NullPointerException("Menu entry '" + menuEntry.getText() + "'not found in list while trying to remove it.");
        }
    }


    /**
     *  This removes a menu entry (via the text label) from the dropdown menu.
     *
     * @param menuText This is the label for the menu entry to remove
     */
    public
    void removeMenuEntry(final String menuText) {
        // have to wait for the value
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean hasValue = new AtomicBoolean(true);

        dispatch(new Runnable() {
            @Override
            public
            void run() {
                synchronized (menuEntries) {
                    MenuEntry menuEntry = getMenuEntry(menuText);

                    if (menuEntry == null) {
                        hasValue.set(false);
                    }
                    else {
                        removeMenuEntry(menuEntry);
                    }
                }
                countDownLatch.countDown();
            }
        });

        try {
            if (!countDownLatch.await(TIMEOUT, TimeUnit.SECONDS)) {
                throw new RuntimeException("Event dispatch queue took longer than " + TIMEOUT + " seconds to complete. Please adjust " +
                                           "`SystemTray.TIMEOUT` to a value which better suites your environment.");

            }
        } catch (InterruptedException e) {
            SystemTray.logger.error("Error removing menu entry: {}", menuText);
        }

        if (!hasValue.get()) {
            throw new NullPointerException("No menu entry exists for string '" + menuText + "'");
        }
    }
}
